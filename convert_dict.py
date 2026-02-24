# convert_dict.py — run once on PC, output goes to app/src/main/assets/
# Reads main_en_US.combined
# Outputs en_us_words.bin — compact format: word\tfrequency\n
# Outputs en_us_bigrams.bin — compact format: word\tnext1,next2,next3\n
# Filters out: offensive words (if desired, though currently keeping possibly_offensive), f=0 words, nonword flagged entries
# Keeps: all words with f >= 50

import os

input_file = 'app/src/main/assets/main_en_US.combined'
words_output = 'app/src/main/assets/en_us_words.bin'
bigrams_output = 'app/src/main/assets/en_us_bigrams.bin'

words = []
bigrams = {}

current_word = None

print(f"Starting to process {input_file}...")

if not os.path.exists(input_file):
    print(f"Error: {input_file} not found. Please place main_en_US.combined in app/src/main/assets/ before running.")
    exit(1)

with open(input_file, 'r', encoding='utf-8') as f:
    line_count = 0
    for line in f:
        line_count += 1
        line_stripped = line.strip()
        if line_stripped.startswith('word='):
            parts = line_stripped.split(',')
            data = {}
            for p in parts:
                if '=' in p:
                    k, v = p.split('=', 1)
                    data[k] = v

            word = data.get('word')
            try:
                freq = int(data.get('f', 0))
            except ValueError:
                freq = 0
            flags = data.get('flags', '')

            if freq < 50:
                current_word = None
                continue
            if 'nonword' in flags:
                current_word = None
                continue

            words.append((word, freq))
            current_word = word
            bigrams[current_word] = []

        elif line_stripped.startswith('bigram=') and current_word:
            parts = line_stripped.split(',')
            data = {}
            for p in parts:
                if '=' in p:
                    k, v = p.split('=', 1)
                    data[k] = v

            bigram_word = data.get('bigram')
            if bigram_word:
                bigrams[current_word].append(bigram_word)

        if line_count % 100000 == 0:
            print(f"Processed {line_count} lines...")

# Write words
print(f"Writing words to {words_output}...")
with open(words_output, 'w', encoding='utf-8') as f:
    for word, freq in words:
        f.write(f"{word}\t{freq}\n")

# Write bigrams
print(f"Writing bigrams to {bigrams_output}...")
with open(bigrams_output, 'w', encoding='utf-8') as f:
    for word, next_words in bigrams.items():
        if next_words:
            f.write(f"{word}\t{','.join(next_words[:3])}\n")

print(f"Successfully processed {len(words)} words.")
