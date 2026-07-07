# tools/convert_dict.py
# Usage: python convert_dict.py main_en_US.combined en_us_words.txt en_us_bigrams.txt
#
# Regenerates the app's dictionary assets from the raw AOSP combined dictionary.
# The output feeds AospDictionary's hashmap combination dictionary, which stores
# every word lowercase and maps it to its key sequence - capitalization is applied
# at typing time from the shift state, so no case information is emitted here.
#
# Words output format (TSV):  word\tfrequency
#   - word: lowercased, apostrophes kept (I'll -> i'll)
#   - case duplicates merged keeping the highest frequency (frequencies are
#     log-scaled 0-255, so max - not sum - is correct)
#   - sorted by frequency desc, then alphabetically
#   - filtered out: f < 50, possibly_offensive=true, flags=nonword, flags=babytalk
#
# Bigrams output format (TSV): previousWord\tnextWord\tcount
#   - both words lowercased, duplicate pairs merged by summing counts
#   - grouped by first word (alphabetical), counts desc within each group
#     (the loader keeps insertion order per key, so this IS the suggestion order)

import sys
import os
from collections import defaultdict


def main():
    if len(sys.argv) < 4:
        print("Usage: python convert_dict.py <input.combined> <words_output.txt> <bigrams_output.txt>")
        sys.exit(1)

    input_file = sys.argv[1]
    words_output = sys.argv[2]
    bigrams_output = sys.argv[3]

    if not os.path.exists(input_file):
        print(f"Error: {input_file} not found.")
        sys.exit(1)

    words = {}
    bigrams = defaultdict(int)
    current_word = None

    print(f"Processing {input_file}...")

    with open(input_file, "r", encoding="utf-8") as f:
        line_count = 0
        for line in f:
            line_count += 1
            line_stripped = line.strip()
            if line_stripped.startswith("word="):
                data = {}
                for p in line_stripped.split(","):
                    if "=" in p:
                        k, v = p.split("=", 1)
                        data[k] = v

                word = data.get("word")
                if not word:
                    current_word = None
                    continue

                try:
                    freq = int(data.get("f", 0))
                except ValueError:
                    freq = 0
                flags = data.get("flags", "")

                if (freq < 50
                        or data.get("possibly_offensive") == "true"
                        or "nonword" in flags
                        or "babytalk" in flags):
                    current_word = None
                    continue

                if not any("a" <= c <= "z" for c in word.lower()):
                    current_word = None
                    continue

                lower = word.lower()
                if lower not in words or freq > words[lower]:
                    words[lower] = freq
                current_word = lower

            elif line_stripped.startswith("bigram=") and current_word:
                data = {}
                for p in line_stripped.split(","):
                    if "=" in p:
                        k, v = p.split("=", 1)
                        data[k] = v

                bigram_word = data.get("bigram")
                if bigram_word:
                    try:
                        count = int(data.get("f", 1))
                    except ValueError:
                        count = 1
                    bigrams[(current_word, bigram_word.lower())] += count

            if line_count % 100000 == 0:
                print(f"Processed {line_count} lines...")

    print(f"Writing {len(words)} words to {words_output}...")
    with open(words_output, "w", encoding="utf-8") as f:
        for word, freq in sorted(words.items(), key=lambda kv: (-kv[1], kv[0])):
            f.write(f"{word}\t{freq}\n")

    print(f"Writing {len(bigrams)} bigrams to {bigrams_output}...")
    with open(bigrams_output, "w", encoding="utf-8") as f:
        for (w1, w2), count in sorted(bigrams.items(), key=lambda kv: (kv[0][0], -kv[1], kv[0][1])):
            f.write(f"{w1}\t{w2}\t{count}\n")

    print("Done.")


if __name__ == "__main__":
    main()
