import scala.io.Source
import scala.util.{Random, Try}
import upickle.default._

// --- Domain Model & Configuration ---

case class Config(
    apiBaseUrl: String = "https://wordle.we4shakthi.in/game",
    wordListPath: String = "medium.txt",
    wordLength: Int = 5,
    maxAttempts: Int = 6,
    userName: String = "Sukeerthi"
)

// Using a Scala 3 enum for type-safe feedback
enum Feedback:
  case Green, Yellow, Red

object Feedback:
  def fromString(feedbackStr: String): List[Feedback] =
    feedbackStr.map {
      case 'G' => Green
      case 'Y' => Yellow
      case 'B' | 'R' => Red // API uses 'B' for black/miss, we'll call it Red
      case _   => Red // Default case
    }.toList

// --- API Communication ---

case class WordleAPIError(message: String) extends Exception(message)

// upickle readers/writers for Scala 3 using 'given'
case class RegisterResponse(id: String)
given ReadWriter[RegisterResponse] = macroRW

case class GuessResponse(feedback: String)
given ReadWriter[GuessResponse] = macroRW

class WordleClient(baseUrl: String, userName: String):
  private var sessionId: Option[String] = None
  private val session = requests.Session()

  private def post[T: ReadWriter](endpoint: String, payload: ujson.Value): Either[WordleAPIError, T] =
    Try {
      val response = session.post(s"$baseUrl/$endpoint", data = payload)
      if response.statusCode == 200 || response.statusCode == 201 then
        read[T](response.text())
      else
        throw WordleAPIError(s"HTTP error: ${response.statusCode} - ${response.text()}")
    }.toEither.left.map {
      case e: WordleAPIError => e
      case e => WordleAPIError(s"Request failed: ${e.getMessage}")
    }

  def startSession(): Either[WordleAPIError, String] =
    println(s"Registering user: $userName...")
    val registerPayload = ujson.Obj("mode" -> "wordle", "name" -> userName)
    for {
      regResponse <- post[RegisterResponse]("register", registerPayload)
      _ = sessionId = Some(regResponse.id)
      _ = println(s"Registration successful. Session ID: ${regResponse.id}")
      _ = println("Creating a new game...")
      createPayload = ujson.Obj("id" -> regResponse.id, "overwrite" -> true)
      _ <- post[ujson.Value]("create", createPayload)
      _ = println("Game created successfully.")
    } yield regResponse.id

  def submitGuess(guess: String): Either[WordleAPIError, List[Feedback]] =
    sessionId match
      case None => Left(WordleAPIError("Session not started."))
      case Some(id) =>
        println(s"Guessing: '$guess'")
        val guessPayload = ujson.Obj("guess" -> guess, "id" -> id)
        post[GuessResponse]("guess", guessPayload).map { resp =>
          val feedbackList = Feedback.fromString(resp.feedback)
          println(s"Feedback: ${feedbackList.map(_.toString.head).mkString}")
          feedbackList
        }

// --- Game Logic ---

class WordleGame(config: Config):
  private val apiClient = new WordleClient(config.apiBaseUrl, config.userName)

  private def loadWords(path: String, length: Int): Either[String, List[String]] =
    Try {
      val source = Source.fromFile(path)
      try {
        val words = source.getLines().map(_.strip().toLowerCase).filter(_.length == length).toList
        if words.isEmpty then throw Exception("Word list is empty or has no words of the correct length.")
        println(s"Loaded ${words.length} words from $path.")
        words
      } finally {
        source.close()
      }
    }.toEither.left.map(_.getMessage)

  private def getFeedbackForFilter(guess: String, answer: String): List[Feedback] =
    // 1. Find green matches
    val greens = guess.zip(answer).map { case (g, a) => g == a }

    // 2. Count remaining letters in the answer after accounting for greens
    val remainingInAnswer = answer.zip(greens)
      .filterNot(_._2) // filter out greens
      .map(_._1)       // get the letters
      .groupBy(identity)
      .view.mapValues(_.length)
      .to(collection.mutable.Map) // mutable map for easy decrementing

    // 3. Build feedback, checking for yellows
    val feedback = guess.zip(greens).map {
      case (_, true) => Feedback.Green
      case (g, false) =>
        if remainingInAnswer.getOrElse(g, 0) > 0 then
          remainingInAnswer(g) -= 1
          Feedback.Yellow
        else
          Feedback.Red
    }
    feedback.toList

  private def filterWords(words: List[String], guess: String, feedback: List[Feedback]): List[String] =
    val newPossibleWords = words.filter(word => getFeedbackForFilter(guess, word) == feedback)
    println(s"Filtered word list: ${words.length} -> ${newPossibleWords.length} possible words.")
    newPossibleWords

  private def getBestGuess(possibleWords: List[String], allWords: List[String]): Option[String] =
    possibleWords.length match
      case 0 => None
      case 1 | 2 => Some(possibleWords.head)
      case _ if possibleWords.length == allWords.length && possibleWords.contains("soare") => Some("soare")
      case _ =>
        val charCounts = possibleWords.flatten.groupBy(identity).view.mapValues(_.size).toMap
        possibleWords.maxByOption(_.distinct.map(charCounts.getOrElse(_, 0)).sum)

  def solve(): Unit =
    val gameFlow = for {
      allWords <- loadWords(config.wordListPath, config.wordLength).left.map(WordleAPIError(_))
      _ <- apiClient.startSession()
    } yield
      (1 to config.maxAttempts).foldLeft((false, allWords)) {
        case ((true, _), _) => (true, Nil) // Already solved
        case ((false, possibleWords), attempt) =>
          println(s"\n--- Attempt $attempt/${config.maxAttempts} ---")
          getBestGuess(possibleWords, allWords) match
            case None =>
              println("Couldn't find a possible word. Something went wrong.")
              (true, Nil) // Stop loop
            case Some(guess) =>
              apiClient.submitGuess(guess) match
                case Left(err) =>
                  println(s"API Error: ${err.message}")
                  (true, Nil) // Stop loop
                case Right(feedback) =>
                  if feedback.forall(_ == Feedback.Green) then
                    println(s"\nSuccess! The word was '$guess'. Solved in $attempt attempts.")
                    (true, Nil)
                  else
                    val nextWords = filterWords(possibleWords, guess, feedback)
                    (false, nextWords)
      }

    gameFlow match
      case Left(err) => println(s"\nAn error occurred: ${err.message}")
      case Right((solved, _)) if !solved => println(s"\nFailed to solve the Wordle in ${config.maxAttempts} attempts.")
      case _ => () // Success message is handled inside the fold

// --- Main Application ---
@main def runWordleSolver(): Unit =
  val config = Config()
  val game = new WordleGame(config)
  game.solve()
