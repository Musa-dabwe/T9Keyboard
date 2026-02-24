# T9 Keyboard

An Android T9 input method with XT9 predictive text support.

## Dictionary Preprocessing

The keyboard uses a processed version of the AOSP English (US) dictionary. The raw source files (`.combined` format) are not included in the repository and should be treated as local-only preprocessing inputs.

To update the dictionary:
1. Place `main_en_US.combined` in `app/src/main/assets/`.
2. Run the preprocessing script:
   ```bash
   python3 tools/convert_dict.py app/src/main/assets/main_en_US.combined app/src/main/assets/en_us_words.bin app/src/main/assets/en_us_bigrams.bin
   ```
3. Remove the `.combined` file before committing.

The raw `.combined` files are excluded via `.gitignore`.
