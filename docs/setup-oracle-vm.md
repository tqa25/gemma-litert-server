# Oracle VM Setup

This server now uses LiteRT-LM JVM, so JDK 21 is required.

Install JDK 21 on Ubuntu arm64:

```bash
sudo apt-get update
sudo apt-get install -y openjdk-21-jdk
```

Verify:

```bash
java -version
javac -version
```

Build:

```bash
./gradlew clean build
```

Run mock backend:

```bash
GEMMA_ENGINE=mock ./gradlew run
```

Run LiteRT-LM backend:

```bash
GEMMA_ENGINE=litert \
GEMMA_MODEL_PATH=models/gemma-4-E4B-it.litertlm \
./gradlew run
```

Test:

```bash
scripts/test-health.sh
scripts/test-generate-text.sh
scripts/test-generate-image.sh
```

Model file used in testing:

```text
models/gemma-4-E4B-it.litertlm
```

Source:

```text
https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm
```
