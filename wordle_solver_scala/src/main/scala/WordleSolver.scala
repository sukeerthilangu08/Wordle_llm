import sttp.client3._
import sttp.client3.circe._
import io.circe.generic.auto._
import io.circe.parser._
import scala.io.Source
import scala.util.Random
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

// --- Configuration ---
object Config {
  val WordListPath = "medium.txt"
  val WordLength = 5
  val MaxAttempts = 6
  val UserName = "Sukeerthi"
  val ApiBaseUrl = "https://wordle.we4shakthi.in/game"
}

// --- API Communication ---
case class RegisterPayload(mode: String, name: String)
case class RegisterResponse(id: String)
case class CreatePayload(id: String, overwrite: Boolean)
case class GuessPayload(id: String, guess: String)
case class GuessResponse(feedback: String)

class WordleClient(baseUrl: String, userName: String) {
  private val backend = HttpClientFutureBackend()
  private var sessionId: Option[String] = None

  private def post[T: io.circe.Decoder](endpoint: String, payload: ujson.Value): Future[T] = {
    val request = basicRequest
      .post(uri"$baseUrl/$endpoint")
      .body(payload)
      .response(asJson[T])

    backend.send(request).flatMap {
      case Response(Right(data), _, _, _, _, _) => Future.successful(data)
      case Response(Left(error), _, _, _, _, _) => Future.failed(new Exception(s"HTTP error: $error"))
    }
  }

  def startSession(): Future[Unit] = {
    println(s"Registering user: $userName...")
    val payload = ujson.Obj("mode" -> "wordle", "name" -> userName)
    post[RegisterResponse]("register", payload).flatMap { res =>
      sessionId = Some(res.id)
      println(s"Registration successful. Session ID: ${res.id}")
      println("Creating a new game...")
      val createPayload = ujson.Obj("id" -> res.id, "overwrite" -> true)
      post[Unit]("create", createPayload).map(_ => println("Game created successfully."))
    }
  }

  def submitGuess(guess: String): Future[String] = {
    sessionId match {
      case Some(id) =>
        println(s"Guessing: '$guess'")
        val payload = ujson.Obj("guess" -> guess, "id" -> id)
        post[GuessResponse]("guess", payload).map { res =>
          val feedback = res.feedback.replace("B", "R")
          println(s"Feedback: $feedback")
          feedback
        }
      case None => Future.failed(new Exception("Session not started. Cannot submit a guess."))
    }
  }
}

// --- Game Logic ---
class WordleGame {
  val allWords = loadWords(Config.WordListPath, Config.WordLength)
  var possibleWords = allWords.toBuffer
  val apiClient = new WordleClient(Config.ApiBaseUrl, Config.UserName)

  private def loadWords(path: String, length: Int): List[String] = {
    try {
      val source = Source.fromResource(path)
      val words = source.getLines().map(_.strip().toLowerCase).filter(_.length == length).toList
      source.close()
      if (words.isEmpty) {
        throw new Exception("Word list is empty or contains no words of the correct length.")
      }
      println(s"Loaded ${words.length} words from $path.")
      words
    } catch {
      case e: java.io.FileNotFoundException =>
        throw new Exception(s"Error: Word list file not found at '$path'.")
    }
  }

  private def getFeedbackForFilter(guess: String, answer: String): String = {
    val feedback = Array.fill(Config.WordLength)('R')
    val answerChars = answer.toBuffer
    val guessChars = guess.toBuffer

    for (i <- 0 until Config.WordLength) {
      if (guessChars(i) == answerChars(i)) {
        feedback(i) = 'G'
        answerChars(i) = ' '
        guessChars(i) = ' '
      }
    }

    for (i <- 0 until Config.WordLength) {
      if (guessChars(i) != ' ' && answerChars.contains(guessChars(i))) {
        feedback(i) = 'Y'
        answerChars(answerChars.indexOf(guessChars(i))) = ' '
      }
    }
    feedback.mkString
  }

  private def filterWords(guess: String, feedback: String): Unit = {
    val initialCount = possibleWords.length
    possibleWords = possibleWords.filter(word => getFeedbackForFilter(guess, word) == feedback)
    println(s"Filtered word list: $initialCount -> ${possibleWords.length} possible words.")
  }

  private def getBestGuess(): Option[String] = {
    if (possibleWords.isEmpty) {
      return None
    }
    if (possibleWords.length == allWords.length && possibleWords.contains("soare")) {
      return Some("soare")
    }
    if (possibleWords.length <= 2) {
      return Some(possibleWords(Random.nextInt(possibleWords.length)))
    }

    val flatListOfChars = possibleWords.flatMap(_.toSet).toList
    val charCounts = flatListOfChars.groupMapReduce(identity)(_ => 1)(_ + _)

    var bestWord = ""
    var maxScore = -1

    for (word <- possibleWords) {
      val score = word.toSet.toList.map(charCounts.getOrElse(_, 0)).sum
      if (score > maxScore) {
        maxScore = score
        bestWord = word
      }
    }
    Some(bestWord)
  }

  def solve(): Future[Unit] = {
    apiClient.startSession().flatMap {
      _ =>
        def loop(attempt: Int): Future[Unit] = {
          if (attempt > Config.MaxAttempts) {
            println(s"\nFailed to solve the Wordle in ${Config.MaxAttempts} attempts.")
            return Future.unit
          }

          println(s"\n--- Attempt $attempt/${Config.MaxAttempts} ---")

          getBestGuess() match {
            case Some(guess) =>
              apiClient.submitGuess(guess).flatMap {
                feedback =>
                  if (feedback == "G" * Config.WordLength) {
                    println(s"\nSuccess! The word was '$guess'. Solved in $attempt attempts.")
                    Future.unit
                  } else {
                    filterWords(guess, feedback)
                    loop(attempt + 1)
                  }
              }
            case None =>
              println("Couldn't find a possible word. Something went wrong.")
              Future.unit
          }
        }
        loop(1)
    }
  }
}

object WordleSolver extends App {
  val game = new WordleGame()
  game.solve()
}