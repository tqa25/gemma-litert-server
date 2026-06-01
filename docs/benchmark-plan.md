# Benchmark Plan

Run each test image three times after one warmup.

Record:

- model load ms
- inference ms
- total request ms
- prompt chars
- image bytes
- response chars
- OCR correctness notes

Compare:

- Oracle VM LiteRT-LM JVM
- ROG Phone 6 Edge Gallery
- ROG Phone 6 backend service, if needed later
