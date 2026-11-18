# Bundled model: license scope and provenance

## Not license-free; not a complete rights clearance

The original Android code is covered by the repository's [Apache License 2.0](LICENSE). **Do not interpret that code license as a waiver of model, tokenizer, dataset, or other third-party rights.**

The base model, [`nkthebass/tinybrainbot-10m-v2-base`](https://huggingface.co/nkthebass/tinybrainbot-10m-v2-base/blob/617e0f06dfe57f3c519be75177bfd8ca8a715530/README.md), declares **Apache-2.0**. The bundled model is a fine-tuned and quantized descendant, not an unrelated model or a copy of Meta's Llama weights. A Llama architecture name by itself does not establish a Meta model-weight dependency.

Upstream model permissions remain governed by Apache-2.0 to the extent of the upstream publisher's rights. Project-owned modifications to the weights/tokenizer are also offered under Apache-2.0, **only to the extent the contributors can grant those rights**. This does not relicense third-party training material, resolve incompatible terms, or certify that upstream publishers held every necessary right.

**Unrestricted public/commercial redistribution of this particular model has not been cleared.** Research/non-commercial terms were found in a component of the current instruction-data mixture, and historical row-level provenance is missing. Keep that issue separate from the permissively licensed app code. This document does not declare the entire model CC-BY-NC, nor does it claim that dataset terms automatically propagate to all weights: their applicability requires clarification/review. It does mean an unconditional commercial-use assurance would be unsupported.

## Artifact covered

- File: `assets/model.bin` (weights and tokenizer in the BRJ1 format).
- SHA-256: `d81dfe999cd400e31cfc9fb290860335b7a53b051f16f692b5f2e5b82949c3f8`.
- Export: signed int8 matrix rows with per-row float32 scales; float32 normalization weights.
- Base: TinyBrainBot-10M-v2-Base, published by `nkthebass`.
- Changes: continued WikiText training; chat and multi-task fine-tuning; Python and brainrot style passes; final brainrot-v2 fine-tuning; quantization and tokenizer-format conversion for the Java runtime. These changes are also identified in [NOTICE](NOTICE).

The locally available training scripts describe this sequence:

```text
TinyBrainBot-10M-v2-Base
  -> WikiText continuation
  -> chat SFT
  -> combined multi-task SFT
  -> Python SFT
  -> brainrot
  -> brainrot-full
  -> brainrot-v2
  -> Android int8 export
```

This is a source/log-based reconstruction, not a cryptographic proof of every historical input. The scripts did not pin upstream dataset/model revisions or preserve all selected row identifiers. The original checkpoints and training logs are not published in this Android repository.

## What Apache-2.0 requires

For distributions covered by Apache-2.0, preserve the license, relevant upstream attribution/notices, and prominent modification notices; pass along a readable NOTICE where applicable. Apache-2.0 permits commercial use within the rights granted, without a license fee, but **"royalty-free" does not mean "no obligations"**. Patent, trademark, warranty and other provisions also matter; the full license controls.

The build includes `LICENSE`, `NOTICE`, this model document, third-party notices and the retained OpenAI notice in the APK's `assets/legal/` directory. Distribute these documents with the model if distributing it separately. No separate upstream LICENSE/NOTICE file was found in the audited base-model repository; its model-card license declaration is recorded rather than inventing an upstream copyright notice.

## Outstanding checks

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and the pinned documentation manifest at [docs/upstream-licenses.json](docs/upstream-licenses.json). Important unresolved items include:

1. **Explore-Instruct:** current smol-smoltalk includes an Explore-Instruct rewriting component. Its upstream card declares CC-BY-NC-4.0 and states that models trained on it should remain research-only. Establish whether these rows occurred in the historical training selections and obtain appropriate permission/clarification before claiming broader rights.
2. **Other mixture sources:** OpenHermes lacks an overall license declaration in the audited card and has many transitive sources. LongAlign's linked code repository has Apache-2.0, but explicit coverage of the separately hosted dataset and its source documents was not established.
3. **WikiText/Wikipedia:** attribution/share-alike terms exist. WikiText's metadata and body disagree about the CC-BY-SA version. Clarify the exact historical data/version and any obligations applicable to the intended use.
4. **Base-model internals:** some distilled/synthetic pretraining sets are described but not individually released or licensed in the base-model card. Web corpus/content rights and historical generation-service terms were not exhaustively audited.

Neither quantization, fine-tuning, AI generation, nor keeping a repository private removes underlying license obligations. This is an evidence-based technical audit, not a legal opinion or a guarantee of non-infringement.
