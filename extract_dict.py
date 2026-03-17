import os
import re

def extract():
    input_file = "app/src/main/assets/main_en_US.combined.txt"
    words_output = "app/src/main/assets/en_us_words.txt"
    bigrams_output = "app/src/main/assets/en_us_bigrams.txt"

    words = []
    bigrams = []

    current_word = None

    # Matches word=word_value,f=frequency
    # Note: word might contain characters other than alphanumeric, so we capture up to the next comma.
    word_pattern = re.compile(r"word=([^,]+),f=(\d+)")
    # Matches bigram=bigram_value,f=frequency
    bigram_pattern = re.compile(r"bigram=([^,]+),f=(\d+)")

    with open(input_file, 'r', encoding='utf-8') as f:
        for line in f:
            stripped_line = line.strip()
            if not stripped_line:
                continue

            if stripped_line.startswith("word="):
                match = word_pattern.search(stripped_line)
                if match:
                    word = match.group(1)
                    freq = int(match.group(2))
                    words.append((word, freq))
                    current_word = word
                else:
                    # In case the format is slightly different, e.g. more attributes
                    # like word=...,something=...,f=...
                    # But based on the head output, it seems simple.
                    pass
            elif stripped_line.startswith("bigram="):
                if current_word:
                    match = bigram_pattern.search(stripped_line)
                    if match:
                        bigram_word = match.group(1)
                        freq = int(match.group(2))
                        bigrams.append((current_word, bigram_word, freq))
            elif stripped_line.startswith("dictionary="):
                continue
            elif stripped_line.startswith("shortcut="):
                continue

    # Sort words by frequency descending
    words.sort(key=lambda x: x[1], reverse=True)

    # Sort bigrams by word then frequency descending
    bigrams.sort(key=lambda x: (x[0], -x[2]))

    with open(words_output, 'w', encoding='utf-8') as f:
        for word, freq in words:
            f.write(f"{word}\t{freq}\n")

    with open(bigrams_output, 'w', encoding='utf-8') as f:
        for word, bigram, freq in bigrams:
            f.write(f"{word}\t{bigram}\t{freq}\n")

    print(f"Extracted {len(words)} words and {len(bigrams)} bigrams.")

if __name__ == "__main__":
    extract()
