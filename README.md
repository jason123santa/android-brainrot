# Brainrot 10M for legacy Android

Offline Android inference code for the `chat-model-10m-brainrot-v2` checkpoint.

## AI-generated code

**This project's implementation, scripts, tests and documentation were generated with an AI coding assistant.** Human review and real-device testing are still required; AI-generated code may contain errors.

This repository includes **the Android source, build scripts, and the exported model/tokenizer** in `assets/model.bin`. You can build directly from a checkout; the original Hugging Face checkpoint is **not required**. APKs, downloaded build tools, signing keys and machine-local settings remain excluded. The resulting APK is `dist/brainrot-legacy-debug.apk` (about 9.7 MiB).

## License and model-data caveats

The original app code, scripts and documentation are licensed under **[Apache-2.0](LICENSE)**. Copyright (c) 2026 **jason123santa**, dated **11/18/2026**. Preserve [NOTICE](NOTICE) and applicable third-party notices when redistributing. The AI-generated-code disclosure above remains applicable.

The base model `nkthebass/tinybrainbot-10m-v2-base` also declares Apache-2.0; **it is not license-free**. The bundled modified/quantized model has separate scope and provenance described in [MODEL_LICENSE.md](MODEL_LICENSE.md).

**Unrestricted/commercial clearance of the bundled model is unresolved.** In particular, current smol-smoltalk includes Explore-Instruct data whose upstream source declares CC-BY-NC-4.0 and research-only model use. The historical training runs did not record exact rows/revisions. WikiText, other mixtures and the base model's datasets also have their own terms; not every upstream permission could be verified. A permissive code license does not erase those obligations or automatically resolve how dataset terms apply to weights.

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the audit, source links, confirmed declarations and open issues, plus [docs/upstream-licenses.json](docs/upstream-licenses.json) for the pinned documentation snapshots. Missing licenses are not public-domain grants. Built APKs include the legal documents under `assets/legal/`. This is a technical provenance audit, not a legal clearance.

## UI update (v1.1)

There is still **one APK**. The inference engine, model, API 3 minimum, old target SDK and CPU support are unchanged. The layout now declares density awareness and all screen sizes instead of asking newer Android versions to use fixed legacy-screen scaling. Newer systems also receive resizable-activity/unrestricted-aspect-ratio declarations; older systems ignore declarations they do not implement.

- Compact message row with a **Send / Stop** button and 48 dp minimum touch target.
- Readable light colors, scalable text and flexible chat space on phones/tablets. The header/composer compact automatically when the available height is small, including landscape keyboards, multi-window and larger font settings.
- Explicit keyboard resizing; the keyboard hides when a message is sent. Keyboards that honor the IME flag avoid the full-screen landscape editor.
- Output follows the bottom only when you are already near it, rather than pulling you away from earlier messages.
- Draft/transcript restoration after Activity recreation; interrupted replies are labeled, not silently restarted.

Code compiles against the Android 1.5 API stubs and APK screen/keyboard declarations pass automated checks. **No Android device or emulator was available for visual validation**, so this is not a claim that every OEM layout, keyboard, font size or old-device runtime has been verified. The target SDK has not been raised: modern installation warnings/blocks described below still apply.

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

After building, enable installation from unknown sources on the device, then sideload the APK or:

```sh
adb install dist/brainrot-legacy-debug.apk
adb shell am start -n org.brainrot.legacy/.ChatActivity
```

On **Android 14+**, normal installation may be blocked because this deliberately targets the legacy SDK. For testing on versions that permit this ADB override:

```sh
adb install --bypass-low-target-sdk-block dist/brainrot-legacy-debug.apk
```

OEM policy, future Android versions, memory and the device's Dalvik/ART implementation may impose additional restrictions. This is a sideloaded legacy debug app, not a Play Store release. Its SHA-1/v1 signature is intentional for pre-2.3 compatibility and is not a recommended modern distribution scheme.

## Build from a clean checkout

Prerequisites on a Linux/Bash host:

- **JDK 8 `javac`** (supports Java 1.5 source/target), plus `java`, `jarsigner` and `keytool`.
- **`aapt`**, available as a Debian/Ubuntu system package or through compatible Android SDK Build Tools.
- **Python 3**, `curl`, `sha256sum` and ordinary shell utilities. No third-party Python packages are needed for an APK build.
- Network access on the first build for SHA-256-pinned Android 1.5 API stubs, dx 1.7, and the Google SDK Platform 26 archive (about 61 MiB). Only `android.jar` is extracted from that archive for manifest resource IDs. Later builds reuse the verified local cache.

From the repository root, run:

```sh
./build.sh
```

The script automatically fetches/verifies the legacy tool JARs and manifest framework, checks the bundled model checksum, compiles the app, generates a local debug signing key, and writes **`dist/brainrot-legacy-debug.apk`**. It does not require the original checkpoint, PyTorch, NumPy, Transformers, Gradle, Android Studio or an NDK. The model is stored as a normal Git file, not a Git LFS pointer.

To prefetch tools separately (for example before an offline build):

```sh
./tools/fetch_toolchain.sh
```

The build script does not install anything system-wide. If tools are not on PATH, override `JAVAC`, `JAVA`, `JARSIGNER`, `KEYTOOL`, `AAPT`, `ANDROID_JAR`, `MANIFEST_ANDROID_JAR` and/or `DX_JAR` with your local tool locations. In particular, use a JDK **8** `javac`, not JDK 17/21 (which removed target 1.5). Custom Android/dx JAR overrides are your responsibility to provision and verify. Keep `ANDROID_JAR` pointed at API 3 to preserve the Java API boundary; `MANIFEST_ANDROID_JAR` needs API 26 or later for the screen-related declarations.

Java still compiles **only against the pinned Android 1.5 Maven jar**. AAPT separately uses SDK Platform 26 to encode newer manifest attributes; this does not raise `minSdkVersion`, `targetSdkVersion`, Java bytecode or the DEX version. The framework JAR is neither compiled into the app nor bundled in it. The downloaded SDK platform remains subject to the [Android SDK terms](https://developer.android.com/studio/terms).

The pinned Android Maven jar checks Android API references but excludes core Java stubs. Core Java calls have therefore also been kept to Java 5/Android 1.5-era APIs; no claim is made that host compilation alone verifies the entire old runtime.

Weights are streamed into their final arrays, not duplicated into a byte buffer. `aapt -0 bin` keeps the >1 MiB asset uncompressed, avoiding old AssetManager compressed-asset limits. `zipalign` is used if available, but is not required for streamed asset loading. An RSA debug key is created in `build/apk/debug.keystore` and reused for rebuilds. Preserve it if you want to install updates over an existing installation; otherwise uninstall the older APK first. Do not use this public-password debug key for production.

The signing script narrowly overrides the Java process's JAR SHA-1 restriction for this legacy APK; it does **not** alter global JVM security settings.

## Verify

These checks run from a checkout with the prerequisites above, without the original checkpoint or third-party Python packages:

```sh
python3 tests/smoke_test.py
python3 tests/check_licenses.py
./build.sh
python3 tests/check_apk.py
```

### Optional: re-export or compare with the original checkpoint

The original HF checkpoint is needed **only** for exporting replacement weights or running the Hugging Face parity test, not for building the app. Put it in the ignored `checkpoint` directory and use a separate Python environment:

```sh
python3 -m venv .venv
. .venv/bin/activate
python -m pip install numpy safetensors torch transformers

# Optional: overwrites assets/model.bin and refreshes assets/model.bin.sha256.
python tools/export_model.py --source ./checkpoint
python tests/test_port.py --source ./checkpoint --java-source 5
```

The checkpoint directory must contain `config.json`, `tokenizer.json`, `tokenizer_config.json` and `model.safetensors`. The runtime deliberately supports the tested configuration (256 hidden size, 704 intermediate size, 12 layers, 4 attention heads, 2 KV heads, 64 head dimension, tied embeddings and a 4096-token vocabulary), not arbitrary Llama checkpoints. Check the applicable model license before redistributing replacement weights.

Observed host results:

- A clean source snapshot builds successfully using the bundled model, automatically downloaded tools and a newly generated debug key, without the original HF checkpoint or any existing local build files. The standalone runtime smoke test and APK checks pass from that snapshot.
- 138 tokenizer fixtures (including Unicode, whitespace, digits, special tokens and seeded fuzz cases) match Hugging Face exactly.
- Java incremental KV-cached inference versus the same int8-dequantized Hugging Face model: maximum absolute logit difference **0.000138**; all tested greedy token choices match.
- Original float32 versus int8 reference logit RMS: **0.11385** on the test prompt; not a general quality benchmark.
- Cache reset, generation smoke test, prompt bounds and thread cancellation pass under `java -Xmx16m`.
- APK checks validate DEX 035 integrity, API 3 minimum, density/all-screen support, keyboard-resize/resizable-activity declarations, absence of native libraries, uncompressed bundled weights and legacy JAR signature metadata. `build.sh` also verifies the cryptographic JAR signature.

Old Android Unicode tables can differ from the host JVM for newer Unicode characters. Device-side tokenizer/inference and lifecycle testing is still required.

### Device acceptance checklist (not yet performed)

1. Install and launch on actual API 3 ARMv5 and on a working MIPS Android system.
2. Confirm model loading completes without an out-of-memory or verification error.
3. Send `hello`; wait for model-generated output, then try another short prompt.
4. Test the Send/Stop action during prefill and generation, rotation, Back/relaunch, state restoration and an overlong message.
5. On modern phones/tablets, check tall screens, high density, portrait/landscape, keyboard open/closed, large font settings and manual scrolling while tokens arrive. OEM compatibility overrides may still affect display behavior.
6. Measure peak app heap, token latency and battery use before calling a particular device supported.

## Files

- `src/org/brainrot/legacy/Model.java`: int8 matvecs, RMSNorm, split-half RoPE, grouped-query attention, causal KV cache, SwiGLU and sampling.
- `src/org/brainrot/legacy/Tokenizer.java`: Digits + GPT-2 byte-level BPE.
- `src/org/brainrot/legacy/ChatActivity.java`: API 3 UI, worker-thread loading/inference and cancellation.
- `assets/model.bin`: committed int8 weights and tokenizer, bundled in the APK.
- `assets/model.bin.sha256`: model checksum, verified before every build.
- `tools/export_model.py`: deterministic HF safetensors exporter.
- `LICENSE`, `NOTICE`, `MODEL_LICENSE.md`, `THIRD_PARTY_NOTICES.md`: code license, attribution, model scope and dataset audit; packaged in the APK.
- `docs/upstream-licenses.json`: upstream audit revisions, declarations and current mixture statistics.
- `licenses/`: retained third-party license text.
- `tests/`: host reference and APK packaging checks.

`Model` is stateful and must only be used by one worker at a time. To raise context/reply limits, change `CONTEXT`/`NEW_TOKENS` in `ChatActivity.java` and rebuild; context cannot exceed the checkpoint's 1024 positions, and KV memory grows linearly.
