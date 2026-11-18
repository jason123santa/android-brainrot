# Brainrot 10M for legacy Android

Offline Android inference code for the `chat-model-10m-brainrot-v2` checkpoint.

## AI-generated code

**This project's implementation, scripts, tests and documentation were generated with an AI coding assistant.** Human review and real-device testing are still required; AI-generated code may contain errors.

This repository contains **source code only**. Model weights, APKs, downloaded build tools, signing keys and machine-local settings are intentionally excluded. Supply a compatible checkpoint locally and build the APK as described below. The resulting APK is `dist/brainrot-legacy-debug.apk` (about 9.7 MiB with the tested model).

## Compatibility and limits

- **Android 1.5 / API 3 minimum.** Android UI calls compile against 1.5 API stubs; Java 5 bytecode is converted to DEX 035.
- **ARMv5 and MIPS by design**, as well as other architectures: inference is scalar Java running on Dalvik/ART. No JNI, `.so`, NEON, ARMv7, hardware floating-point, GPU or native ABI requirements. MIPS needs a working Android/Dalvik port; Android 1.5 was not a standard official MIPS release.
- **Not yet tested on an Android device/emulator**, including ARMv5 or MIPS. Host verification and APK build checks pass; they do not certify every Android implementation.
- Ancient ARMv5/MIPS CPUs may be **extremely slow**, especially without hardware floating point or a JIT. This is a compatibility-oriented implementation, not optimized native inference. No device throughput has been measured.
- Weights: **9.67 MiB**, signed int8 with per-row float32 scales; float32 activations and KV cache. This is a new quantization of the original safetensors, not a loader for the GGUF/llamafile.
- Default context **128 tokens**, including prompt and up to **32 reply tokens**. KV cache is 1.5 MiB. Short inputs only; excessive token counts produce an error rather than silently changing the prompt.
- The engine passes a **16 MiB host JVM heap** smoke test. Android framework/UI overhead is additional; a 16 MiB Dalvik app heap may be insufficient. Prefer **24–32 MiB or larger app heaps**. This cannot guarantee operation on every 1.5-era handset.
- Single-turn chat: each message starts fresh, although previous exchanges remain visible. Uses the checkpoint template `User: ...\nAssistant:`; replies are sampled at temperature 0.8, top-k 40, repetition penalty 1.15. No hardcoded duck/meow replies. Quantization and sampling can change output.
- No network calls, requested permissions, external files, accounts, downloads or services at runtime. On later Android releases, the **old target SDK can cause Android to implicitly grant storage/phone compatibility permissions**. The app never uses those APIs.

### Install

Enable installation from unknown sources on the device, then sideload the APK or:

```sh
adb install dist/brainrot-legacy-debug.apk
adb shell am start -n org.brainrot.legacy/.ChatActivity
```

On **Android 14+**, normal installation may be blocked because this deliberately targets the legacy SDK. For testing on versions that permit this ADB override:

```sh
adb install --bypass-low-target-sdk-block dist/brainrot-legacy-debug.apk
```

OEM policy, future Android versions, memory and the device's Dalvik/ART implementation may impose additional restrictions. This is a sideloaded legacy debug app, not a Play Store release. Its SHA-1/v1 signature is intentional for pre-2.3 compatibility and is not a recommended modern distribution scheme.

## Rebuild

From the repository root:

```sh
python3 -m venv .venv
. .venv/bin/activate
python -m pip install numpy safetensors

# Put a compatible checkpoint in this ignored local directory first.
python tools/export_model.py --source ./checkpoint

# Downloads two small, SHA-256-pinned Maven artifacts (API 3 stubs and dx 1.7).
./tools/fetch_toolchain.sh

# Requires JDK 8 javac (supports -source/-target 1.5), java, aapt,
# jarsigner, keytool and Python 3. No Gradle, Android Studio or NDK.
./build.sh
```

The checkpoint directory must contain `config.json`, `tokenizer.json`, `tokenizer_config.json` and `model.safetensors`. The runtime deliberately supports the tested model configuration (256 hidden size, 704 intermediate size, 12 layers, 4 attention heads, 2 KV heads, 64 head dimension, tied embeddings and a 4096-token vocabulary), not arbitrary Llama checkpoints. Obtain the checkpoint separately and check its license before redistribution.

On Debian/Ubuntu `aapt` is available as a system package; SDK Build Tools can also provide it. The build script does not install anything system-wide. If tools are not on PATH, override `JAVAC`, `JAVA`, `JARSIGNER`, `KEYTOOL`, `AAPT`, `ANDROID_JAR` and/or `DX_JAR` with full paths. In particular, use a JDK **8** `javac`, not JDK 17/21 (which removed target 1.5).

The pinned Android Maven jar checks Android API references but excludes core Java stubs. Core Java calls have therefore also been kept to Java 5/Android 1.5-era APIs; no claim is made that host compilation alone verifies the entire old runtime.

Weights are streamed into their final arrays, not duplicated into a byte buffer. `aapt -0 bin` keeps the >1 MiB asset uncompressed, avoiding old AssetManager compressed-asset limits. `zipalign` is used if available, but is not required for streamed asset loading. An RSA debug key is created in `build/apk/debug.keystore` and reused for rebuilds. Preserve it if you want to install updates over an existing installation; otherwise uninstall the older APK first. Do not use this public-password debug key for production.

The signing script narrowly overrides the Java process's JAR SHA-1 restriction for this legacy APK; it does **not** alter global JVM security settings.

## Verify

```sh
# Activate the virtual environment used above; provide the local reference model.
python -m pip install torch transformers
python tests/test_port.py --source ./checkpoint --java-source 5
python tests/check_apk.py
```

Observed host results:

- 138 tokenizer fixtures (including Unicode, whitespace, digits, special tokens and seeded fuzz cases) match Hugging Face exactly.
- Java incremental KV-cached inference versus the same int8-dequantized Hugging Face model: maximum absolute logit difference **0.000138**; all tested greedy token choices match.
- Original float32 versus int8 reference logit RMS: **0.11385** on the test prompt; not a general quality benchmark.
- Cache reset, generation smoke test, prompt bounds and thread cancellation pass under `java -Xmx16m`.
- APK checks validate DEX 035 integrity, API 3 manifest, absence of native libraries, uncompressed bundled weights and legacy JAR signature metadata. `build.sh` also verifies the cryptographic JAR signature.

Old Android Unicode tables can differ from the host JVM for newer Unicode characters. Device-side tokenizer/inference and lifecycle testing is still required.

### Device acceptance checklist (not yet performed)

1. Install and launch on actual API 3 ARMv5 and on a working MIPS Android system.
2. Confirm model loading completes without an out-of-memory or verification error.
3. Send `hello`; wait for model-generated output, then try another short prompt.
4. Test Stop during prefill and reply generation, rotation, Back/relaunch and an overlong message.
5. Measure peak app heap, token latency and battery use before calling a particular device supported.

## Files

- `src/org/brainrot/legacy/Model.java`: int8 matvecs, RMSNorm, split-half RoPE, grouped-query attention, causal KV cache, SwiGLU and sampling.
- `src/org/brainrot/legacy/Tokenizer.java`: Digits + GPT-2 byte-level BPE.
- `src/org/brainrot/legacy/ChatActivity.java`: API 3 UI, worker-thread loading/inference and cancellation.
- `assets/model.bin`: locally generated weights and tokenizer bundled in the APK; not committed.
- `tools/export_model.py`: deterministic HF safetensors exporter.
- `tests/`: host reference and APK packaging checks.

`Model` is stateful and must only be used by one worker at a time. To raise context/reply limits, change `CONTEXT`/`NEW_TOKENS` in `ChatActivity.java` and rebuild; context cannot exceed the checkpoint's 1024 positions, and KV memory grows linearly.
