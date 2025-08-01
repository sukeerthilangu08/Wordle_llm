import requests
import random

class WordleAPI:
    BASE_URL = "https://wordle.we4shakthi.in/game"
    SESSION = requests.Session()

    @staticmethod
    def register(name="Sukeerthi"):
        payload = {"mode": "wordle", "name": name}
        response = WordleAPI.SESSION.post(f"{WordleAPI.BASE_URL}/register", json=payload)
        print("Register:", response.json())
        return response.json()["id"]

    @staticmethod
    def create_game(id):
        payload = {"id": id, "overwrite": True}
        response = WordleAPI.SESSION.post(f"{WordleAPI.BASE_URL}/create", json=payload)
        print("Create Game:", response.json())
        return response.json()

    @staticmethod
    def guess(id, guess_word):
        payload = {"guess": guess_word, "id": id}
        response = WordleAPI.SESSION.post(f"{WordleAPI.BASE_URL}/guess", json=payload)
        print(f"Guess: {guess_word}")
        print("API Response:", response.json())
        return response.json()["feedback"].replace("B", "R")  # Use R instead of B

class Wordle:
    FIRST_TIME = True
    words = []
    instructions = """The computer will guess the word. Feedback is fetched from the API."""

    def __init__(self, id):
        if Wordle.FIRST_TIME:
            Wordle.FIRST_TIME = False
            with open("medium.txt") as f:
                Wordle.words = [w.strip().lower() for w in f if len(w.strip()) == 5]
        self.id = id
        self.possible_words = Wordle.words[:]
        random.shuffle(self.possible_words)
        self.guess = self.possible_words[0]
        self.attempts = 0
        self.status = "PLAY"
        self.word_count = len(self.possible_words)
        self.response = ""
        self.word_size = len(self.guess)
        self.max_attempts = 6

    @staticmethod
    def get_feedback(guess: str, answer: str) -> str:
        feedback = ['R'] * 5
        answer_chars = list(answer)
        guess_chars = list(guess)
        for i in range(5):
            if guess_chars[i] == answer_chars[i]:
                feedback[i] = 'G'
                answer_chars[i] = None
                guess_chars[i] = None
        for i in range(5):
            if guess_chars[i] is not None and guess_chars[i] in answer_chars:
                feedback[i] = 'Y'
                answer_chars[answer_chars.index(guess_chars[i])] = None
        return ''.join(feedback)

    def remove_impossible_words(self):
        self.possible_words = [
            w for w in self.possible_words[1:]
            if Wordle.get_feedback(self.guess, w) == self.response
        ]

    def play(self):
        print(self.guess)
        # Instead of user input, get feedback from API
        response = WordleAPI.guess(self.id, self.guess)
        if len(response) != self.word_size or any(c not in "GYR" for c in response):
            print("Invalid feedback from API.")
        elif response == "GGGGG":
            print(f"Wowee! The computer guessed your word in {self.attempts + 1} attempts!")
            self.status = "WON"
        else:
            self.response = response

    def game(self):
        print(Wordle.instructions)
        while self.status == "PLAY" and self.possible_words and self.attempts < self.max_attempts:
            self.play()
            if self.status == "WON":
                break
            self.remove_impossible_words()
            if not self.possible_words:
                print("No more possible words. Game over.")
                break
            self.guess = self.possible_words[0]
            self.attempts += 1
        if self.status != "WON":
            print("The computer couldn't guess your word in 6 attempts.")

if __name__ == "__main__":
    id = WordleAPI.register("Sukeerthi")
    WordleAPI.create_game(id)
    game = Wordle(id)
    game.game()