# tools/normalize_dict.py
# Usage: python normalize_dict.py <assets_dir>
#
# Normalizes the dictionary assets to lowercase so that capitalization is decided
# exclusively by the keyboard's shift state at typing time, never by the data:
#
#   en_us_words.txt   (word\tfreq)
#     - words lowercased (apostrophes kept, e.g. I'll -> i'll)
#     - case duplicates merged, keeping the highest frequency
#       (frequencies are log-scaled 0-255, so max - not sum - is correct)
#     - sorted by frequency desc, then alphabetically
#
#   en_us_bigrams.txt (word1\tword2\tfreq)
#     - both words lowercased
#     - duplicate pairs merged by summing counts
#     - grouped by first word (alphabetical), counts desc within each group
#       (the loader keeps insertion order per key, so this IS the suggestion order)

import sys
import os
from collections import defaultdict


def normalize_words(path):
    merged = {}
    total = 0
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 2:
                continue
            total += 1
            word = parts[0].strip().lower()
            if not word:
                continue
            try:
                freq = int(parts[1].split(" ")[0])
            except ValueError:
                continue
            if word not in merged or freq > merged[word]:
                merged[word] = freq

    ordered = sorted(merged.items(), key=lambda kv: (-kv[1], kv[0]))
    with open(path, "w", encoding="utf-8") as f:
        for word, freq in ordered:
            f.write(f"{word}\t{freq}\n")
    print(f"words:   {total} lines -> {len(merged)} unique lowercase words")


def normalize_bigrams(path):
    merged = defaultdict(int)
    total = 0
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 3:
                continue
            total += 1
            w1 = parts[0].strip().lower()
            w2 = parts[1].strip().lower()
            if not w1 or not w2:
                continue
            try:
                freq = int(parts[2].split(" ")[0])
            except ValueError:
                continue
            merged[(w1, w2)] += freq

    ordered = sorted(merged.items(), key=lambda kv: (kv[0][0], -kv[1], kv[0][1]))
    with open(path, "w", encoding="utf-8") as f:
        for (w1, w2), freq in ordered:
            f.write(f"{w1}\t{w2}\t{freq}\n")
    print(f"bigrams: {total} lines -> {len(merged)} unique lowercase pairs")


def main():
    if len(sys.argv) < 2:
        print("Usage: python normalize_dict.py <assets_dir>")
        sys.exit(1)
    assets = sys.argv[1]
    normalize_words(os.path.join(assets, "en_us_words.txt"))
    normalize_bigrams(os.path.join(assets, "en_us_bigrams.txt"))


if __name__ == "__main__":
    main()
