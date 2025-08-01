import requests
import random
from collections import Counter

# --- Configuration ---
CONFIG = {
    "API_BASE_URL": "https://wordle.we4shakthi.in/game",
    "WORD_LIST_PATH": "medium.txt",
    "WORD_LENGTH": 5,
    "MAX_ATTEMPTS": 6,
    "USER_NAME": "Sukeerthi"
}

# --- API Communication ---
class WordleAPIError(Exception):
    """Custom exception for API related errors."""
    pass

class WordleClient:
    """Handles all communication with the Wordle API, including session management."""
    def __init__(self, base_url, user_name):
        self.base_url = base_url
        self.user_name = user_name
        self.session = requests.Session()
        self.session_id = None

    def _post(self, endpoint, payload):
        """Helper method for making POST requests."""
        try:
            response = self.session.post(f"{self.base_url}/{endpoint}", json=payload)
            response.raise_for_status()
            return response.json()
        except requests.exceptions.HTTPError as http_err:
            raise WordleAPIError(f"HTTP error occurred: {http_err} - {response.text}")
        except requests.exceptions.RequestException as req_err:
            raise WordleAPIError(f"A request error occurred: {req_err}")

    def start_session(self):
        """Registers the user and creates a new game, managing the session."""
        print(f"Registering user: {self.user_name}...")
        payload = {"mode": "wordle", "name": self.user_name}
        response = self._post("register", payload)
        self.session_id = response.get("id")
        if not self.session_id:
            raise WordleAPIError("Failed to get a session ID from the API.")
        print(f"Registration successful. Session ID: {self.session_id}")

        print("Creating a new game...")
        create_payload = {"id": self.session_id, "overwrite": True}
        self._post("create", create_payload)
        print("Game created successfully.")

    def submit_guess(self, guess):
        """Submits a guess and returns the feedback."""
        if not self.session_id:
            raise WordleAPIError("Session not started. Cannot submit a guess.")
        print(f"Guessing: '{guess}'")
        payload = {"guess": guess, "id": self.session_id}
        response = self._post("guess", payload)
        feedback = response.get("feedback", "").replace("B", "R")
        print(f"Feedback: {feedback}")
        return feedback

# --- Game Logic ---
class WordleGame:
    """Manages the Wordle game logic and solving strategy."""
    def __init__(self, config):
        self.config = config
        self.all_words = self._load_words(config["WORD_LIST_PATH"], config["WORD_LENGTH"])
        self.possible_words = self.all_words[:]
        self.api_client = WordleClient(config["API_BASE_URL"], config["USER_NAME"])

    def _load_words(self, path, length):
        """Loads words from a file."""
        try:
            with open(path) as f:
                words = [w.strip().lower() for w in f if len(w.strip()) == length]
                if not words:
                    raise ValueError("Word list is empty or contains no words of the correct length.")
                print(f"Loaded {len(words)} words from {path}.")
                return words
        except FileNotFoundError:
            raise ValueError(f"Error: Word list file not found at '{path}'.")

    def _get_feedback_for_filter(self, guess, answer):
        """Generates feedback for a guess against a potential answer (for filtering)."""
        feedback = ['R'] * self.config["WORD_LENGTH"]
        answer_chars = list(answer)
        guess_chars = list(guess)

        for i in range(self.config["WORD_LENGTH"]):
            if guess_chars[i] == answer_chars[i]:
                feedback[i] = 'G'
                answer_chars[i] = None
                guess_chars[i] = None

        for i in range(self.config["WORD_LENGTH"]):
            if guess_chars[i] is not None and guess_chars[i] in answer_chars:
                feedback[i] = 'Y'
                answer_chars[answer_chars.index(guess_chars[i])] = None

        return "".join(feedback)

    def _filter_words(self, guess, feedback):
        """Filters the list of possible words based on the feedback received."""
        initial_count = len(self.possible_words)
        self.possible_words = [
            word for word in self.possible_words
            if self._get_feedback_for_filter(guess, word) == feedback
        ]
        print(f"Filtered word list: {initial_count} -> {len(self.possible_words)} possible words.")

    def _get_best_guess(self):
        """Chooses the best word to guess next."""
        if not self.possible_words:
            return None
        
        if len(self.possible_words) == len(self.all_words) and "soare" in self.possible_words:
            return "soare"

        if len(self.possible_words) <= 2:
            return random.choice(self.possible_words)

        flat_list_of_chars = [char for word in self.possible_words for char in set(word)]
        char_counts = Counter(flat_list_of_chars)

        best_word = ""
        max_score = -1

        for word in self.possible_words:
            score = sum(char_counts[char] for char in set(word))
            if score > max_score:
                max_score = score
                best_word = word
        
        return best_word

    def solve(self):
        """Runs the main Wordle solving loop."""
        try:
            self.api_client.start_session()

            for attempt in range(1, self.config["MAX_ATTEMPTS"] + 1):
                print(f"\n--- Attempt {attempt}/{self.config['MAX_ATTEMPTS']} ---")
                
                guess = self._get_best_guess()
                if not guess:
                    print("Couldn't find a possible word. Something went wrong.")
                    break

                feedback = self.api_client.submit_guess(guess)
                if not feedback:
                    break

                if feedback == 'G' * self.config["WORD_LENGTH"]:
                    print(f"\nSuccess! The word was '{guess}'. Solved in {attempt} attempts.")
                    break
                
                self._filter_words(guess, feedback)

            else:
                print(f"\nFailed to solve the Wordle in {self.config['MAX_ATTEMPTS']} attempts.")

        except (WordleAPIError, ValueError) as e:
            print(f"\nAn error occurred: {e}")
        except Exception as e:
            print(f"\nAn unexpected error occurred: {e}")


if __name__ == "__main__":
    game = WordleGame(CONFIG)
    game.solve()