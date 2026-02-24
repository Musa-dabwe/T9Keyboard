# tools/convert_dict.py
# Usage: python convert_dict.py main_en_US.combined en_us_words.bin en_us_bigrams.bin
#
# Words output format (TSV):
#   strippedWord\tfrequency\tdisplayWord
#   - strippedWord: lowercase, apostrophes and non-alpha characters removed
#   - displayWord: original form (only written if different from strippedWord)
#   - frequency: integer 0-255
#   - filtered out: f < 50, possibly_offensive=true, flags=nonword, flags=babytalk
#   - kept: abbreviation, medical, technical
#   - contractions: stored under stripped form, display word preserves apostrophe
#
# Bigrams output format (TSV):
#   previousWord\tnext1,next2,next3
#   - previousWord: lowercased
#   - up to 3 next words, comma separated, only as many as exist

import sys
import os

def get_t9_key(word):
    return "".join([c for c in word.lower() if 'a' <= c <= 'z'])

def main():
    if len(sys.argv) < 4:
        print("Usage: python convert_dict.py <input.combined> <words_output.bin> <bigrams_output.bin>")
        sys.exit(1)

    input_file = sys.argv[1]
    words_output = sys.argv[2]
    bigrams_output = sys.argv[3]

    if not os.path.exists(input_file):
        print(f"Error: {input_file} not found.")
        sys.exit(1)

    words = []
    bigrams = {}
    current_word = None

    print(f"Processing {input_file}...")

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
                if not word:
                    current_word = None
                    continue

                try:
                    freq = int(data.get('f', 0))
                except ValueError:
                    freq = 0
                flags = data.get('flags', '')
                is_offensive = data.get('possibly_offensive') == 'true'

                # Filtering
                if freq < 50:
                    current_word = None
                    continue
                if is_offensive:
                    current_word = None
                    continue
                if 'nonword' in flags:
                    current_word = None
                    continue
                if 'babytalk' in flags:
                    current_word = None
                    continue

                stripped = get_t9_key(word)
                if not stripped:
                    current_word = None
                    continue

                # display_word: original form (only written if different from strippedWord)
                display_word = word if word != stripped else ""

                words.append((stripped, freq, display_word))
                current_word = word.lower() # Bigrams use lowercased previous word
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

    print(f"Writing words to {words_output}...")
    with open(words_output, 'w', encoding='utf-8') as f:
        for stripped, freq, display in words:
            f.write(f"{stripped}\t{freq}\t{display}\n")

    print(f"Writing bigrams to {bigrams_output}...")
    with open(bigrams_output, 'w', encoding='utf-8') as f:
        for prev, nexts in bigrams.items():
            if nexts:
                f.write(f"{prev}\t{','.join(nexts[:3])}\n")

    print(f"Done. Processed {len(words)} words.")

if __name__ == "__main__":
    main()
