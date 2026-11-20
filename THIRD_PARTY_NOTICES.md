# Third-party licenses and training-data audit

Audited **2026-11-18**. This records published declarations, not complete legal clearance. **The model and its training sources are not license-free.** Free downloads and royalty-free licenses can still require attribution, notices, share-alike, non-commercial use, or other conditions.

The [root LICENSE](LICENSE) applies to original project code/documentation. Model scope is explained separately in [MODEL_LICENSE.md](MODEL_LICENSE.md). Existing third-party terms are not replaced by the project license. Public upstream links below contain upstream project names, not private account configuration or local machine paths.

## Evidence and limits

[docs/upstream-licenses.json](docs/upstream-licenses.json) records 20 audited Hugging Face documentation revisions, card hashes, declared licenses and public source URLs. **These revisions are audit snapshots, not the revisions originally used for training.** Local training scripts did not pin them or record all selected rows.

The local training chain was traced through WikiText continuation, chat SFT, combined multi-task SFT, Python SFT, brainrot, brainrot-full and brainrot-v2. Available source/log evidence identifies the direct datasets below. The base-model card identifies its pretraining sources. This does not prove all transitive sources, rights ownership, or historical generation/API terms.

## Base model and direct local fine-tuning sources

| Source and attribution | Published terms / findings |
| --- | --- |
| [TinyBrainBot-10M-v2-Base](https://huggingface.co/nkthebass/tinybrainbot-10m-v2-base), `nkthebass` | **Apache-2.0** in model-card metadata. Retain license/attribution and mark modifications. No separate LICENSE or NOTICE file was present at the audited revision. The card says the model was trained from scratch; a Llama architecture is not evidence of Meta weight reuse. |
| [WikiText](https://huggingface.co/datasets/Salesforce/wikitext), Stephen Merity, Caiming Xiong, James Bradbury, Richard Socher and Wikipedia contributors | The local script used `wikitext-2-raw-v1`. Metadata declares **CC-BY-SA-3.0 and GFDL**, while the card's licensing paragraph links **CC-BY-SA-4.0**. This conflict is unresolved, not a reason to assume no license. Attribution/share-alike requirements govern covered data uses; the effect on particular weights/outputs is not established here. |
| [smol-smoltalk](https://huggingface.co/datasets/HuggingFaceTB/smol-smoltalk), HuggingFaceTB / SmolLM2 team and upstream contributors | Card metadata declares **Apache-2.0**, but this is a mixture. Its parent [SmolTalk card](https://huggingface.co/datasets/HuggingFaceTB/smoltalk) expressly says existing public components retain their original licenses. The aggregate label does not clear every constituent. See the next section. |
| [basic-arithmetic](https://huggingface.co/datasets/ChrisMcCormick/basic-arithmetic), Chris McCormick | Card declares **MIT**. Preserve applicable copyright/permission notices when redistributing covered material; do not treat it as public domain. No standalone dataset LICENSE file was present at the audited revision. This project trained on question/answer fields, not the optional Qwen-tokenized configuration. |
| Locally constructed arithmetic, grammar, Python and style examples | Source scripts generate examples from templates and simple snippets. No separate external corpus was identified for these generators, but that does not establish authorship/rights in every phrase or any undisclosed source. |

### Important: smol-smoltalk has a non-commercial/research-only upstream component

The public [dataset statistics endpoint](https://datasets-server.huggingface.co/statistics?dataset=HuggingFaceTB%2Fsmol-smoltalk&config=default&split=train) reported a complete (not partial) histogram of 460,341 current training examples during this audit. It includes **3,017 `explore-instruct-rewrite` examples**, along with OpenHermes, self-oss-instruct, LongAlign and everyday-conversation components. Counts and the response hash are preserved in the manifest.

These statistics concern the **current mixture**, not the exact historically selected fine-tuning rows. They establish a real provenance question; they do not prove that a particular row is encoded in these weights.

| Current mixture component | Upstream findings |
| --- | --- |
| `explore-instruct-rewrite`: [Wanfq/Explore_Instruct_Rewriting_32k](https://huggingface.co/datasets/Wanfq/Explore_Instruct_Rewriting_32k) | **CC-BY-NC-4.0**. The audited card states: "Explore-Instruct is intended and licensed for research use only" and "models trained using the dataset should not be used outside of research purposes." This prevents a blanket claim that every possible input permits unrestricted commercial training/model use. Exact historical inclusion and applicable permission require clarification. |
| `self-oss-instruct`: [bigcode/self-oss-instruct-sc2-exec-filter-50k](https://huggingface.co/datasets/bigcode/self-oss-instruct-sc2-exec-filter-50k) | **ODC-By** declaration. Seed Python functions originate with the MultiPL-T pipeline. Database attribution does not by itself license every underlying code sample or resolve seed-code obligations. |
| `openhermes-50k`: [teknium/OpenHermes-2.5](https://huggingface.co/datasets/teknium/OpenHermes-2.5) | No overall license declaration or standalone LICENSE was found in the audited HF repository. It lists many source datasets; one source page is explicitly described as lost. This is **unresolved permission/provenance**, not public domain or an inferred Apache grant. Its transitive sources have not all been audited. |
| `longalign`: [THUDM/LongAlign-10k](https://huggingface.co/datasets/THUDM/LongAlign-10k), now resolving to `zai-org/LongAlign-10k` | No license declaration was found in the audited dataset card. The linked [LongAlign code repository](https://github.com/THUDM/LongAlign) has Apache-2.0, but explicit coverage of the separately hosted dataset/source documents was not established. Do not silently assign that code license to all data. |
| `everyday-conversations`: [HuggingFaceTB/everyday-conversations-llama3.1-2k](https://huggingface.co/datasets/HuggingFaceTB/everyday-conversations-llama3.1-2k) | **Apache-2.0** declaration; the card says conversations were generated using Llama-3.1-70B-Instruct. Historical generation/model terms were not exhaustively audited. |
| Smol-Magpie, constraints, rewrite and summarize components | Parent SmolTalk attributes its newly generated datasets to **Apache-2.0**. Generator and seed-content rights remain distinct questions; the parent card says Smol-Magpie used Llama-3.1-405B-Instruct. |

The local scripts streamed/shuffled/filtered portions of smol-smoltalk and did **not** preserve row-level source manifests. Filtering out some conversation content is not a license filter. The broad permissive mixture label is insufficient to certify this model for commercial release. This document does not automatically relicense the entire trained model under the most restrictive dataset license either; that legal question remains open.

### CodeParrot branch was attempted but did not contribute rows in the observed run

The Python fine-tuning source includes an optional `codeparrot/github-code` loader. Its available training log reports a dataset-script loading failure and **`cp=0 canon=4000`**. The observed run therefore fell back to local canonical examples rather than importing GitHub code rows through that branch. A fresh training run in another environment could behave differently and would need per-source/per-repository license checks. This finding does not clear code-derived data already present in upstream instruction mixtures.

## Base model's declared pretraining sources

The base-model card lists these datasets. Their labels apply to the published data/licenses within the rights the publishers hold; they do not certify every underlying web page, source text, generation service, or model output.

| Declared source | Published terms and caveats |
| --- | --- |
| [ArrowLabs/DCLM-Ultra](https://huggingface.co/datasets/ArrowLabs/DCLM-Ultra) | **Apache-2.0** declaration. Derived from DCLM/DCLM-pro; upstream declarations differ (see below). Retain source attribution; do not assume filtering erases earlier rights. |
| [ArrowLabs/DCLM-Ultra-1.1](https://huggingface.co/datasets/ArrowLabs/DCLM-Ultra-1.1) | **Apache-2.0** declaration; the same DCLM ancestry caveat applies. |
| [HuggingFaceFW/fineweb-edu](https://huggingface.co/datasets/HuggingFaceFW/fineweb-edu) | **ODC-By 1.0** plus [Common Crawl Terms of Use](https://commoncrawl.org/terms-of-use). The license covers database rights, not all individual-content rights. The card describes Llama-generated classifier annotations. |
| [HuggingFaceTB/cosmopedia](https://huggingface.co/datasets/HuggingFaceTB/cosmopedia) | **Apache-2.0** declaration. Synthetic material uses seeds from web samples, Stanford, WikiHow, OpenStax and Khan Academy; complete seed/source/generator clearance was not verified. |
| [roneneldan/TinyStories](https://huggingface.co/datasets/roneneldan/TinyStories) | **CDLA-Sharing-1.0**, not public domain. [Section 3.5](https://cdla.dev/sharing-1-0/) imposes no obligations/restrictions on qualifying computational **Results**, but section 1.11 excludes outputs containing more than a de minimis portion of the input data. Sharing the actual data/enhanced data has separate attribution/sharing requirements. Do not assume weights must be share-alike solely because this dataset was used. |
| [wikimedia/wikipedia](https://huggingface.co/datasets/wikimedia/wikipedia), Wikipedia contributors | Audited card declares **CC-BY-SA-3.0 / GFDL** and warns some text is CC-only. Exact dump, per-content terms and applicable attribution/share-alike requirements matter. |
| [microsoft/orca-math-word-problems-200k](https://huggingface.co/datasets/microsoft/orca-math-word-problems-200k), Microsoft | **MIT** declaration. Card describes Lila/DMath seeds and Azure GPT-4-Turbo generations; it also labels educational-system use out of scope. Do not confuse that stated intended use with an invented change to the MIT text, or assume all upstream seed terms were audited. |
| [openbmb/UltraData-Math](https://huggingface.co/datasets/openbmb/UltraData-Math) | **Apache-2.0** declaration. The card describes multiple web/math seed corpora and synthesis models. Those transitive rights were not exhaustively audited. |
| Author-described distilled Q&A/facts, commonsense/how-to/writing sets, own arithmetic drills and LLM-written frames | The base-model card describes these but does not individually identify/license all artifacts or generation arrangements. Their full provenance cannot be verified from the published card. |

### DCLM ancestry has different declarations

- [mlfoundations/dclm-baseline-1.0](https://huggingface.co/datasets/mlfoundations/dclm-baseline-1.0) declares **CC-BY-4.0**.
- [gair-prox/DCLM-pro](https://huggingface.co/datasets/gair-prox/DCLM-pro) has **ODC-By** metadata while its licensing paragraph describes DCLM as CC-BY-4.0.
- The ArrowLabs derived corpora declare Apache-2.0. This is not evidence that earlier attribution or individual web-content rights disappeared. The apparent scope/label differences remain documented, not silently resolved.

## Practical license distinctions

- **Apache-2.0 / MIT:** permissive and generally royalty-free, but preserve required notices/permissions, identify modifications where applicable, and respect their scope. Permissive is not the same as no license.
- **CC-BY / CC-BY-SA:** attribution and, for covered adaptations under BY-SA, share-alike obligations. Whether particular model weights/outputs constitute covered adaptations is not decided by this audit.
- **CC-BY-NC:** has a non-commercial condition; do not ignore an upstream research-only statement just because a mixture advertises Apache-2.0.
- **ODC-By:** attribution for covered databases/produced works; it does not license all individual database contents. As a precaution, [NOTICE](NOTICE) acknowledges FineWeb-Edu, DCLM-pro and self-oss-instruct and links [ODC-By 1.0](https://opendatacommons.org/licenses/by/1-0/). A [plain-text mirror](https://github.com/spdx/license-list-data/blob/main/text/ODC-By-1.0.txt) was also consulted.
- **CDLA-Sharing:** distinguish data/enhanced data from qualifying computational Results; the express Results exception matters.
- **Common Crawl:** its terms explicitly warn about separate crawled-content rights and include restrictions, disclaimers and indemnity provisions. A web corpus label does not grant unrestricted rights in every page.
- **No license found:** no permission has been verified. Missing metadata is not a license waiver.

## Code algorithms, dependencies and build tools

- The Java runtime implements Llama equations independently and has no bundled Transformers, PyTorch, llama.cpp, llamafile or Meta Llama weights/runtime. Architecture names and math alone are not model-license evidence.
- GPT-2 byte-alphabet/BPE conventions are credited to OpenAI. A precautionary copy of the upstream **Modified MIT License**, including `Software Copyright (c) 2019 OpenAI`, is retained in [licenses/OpenAI-GPT2-LICENSE.txt](licenses/OpenAI-GPT2-LICENSE.txt). No GPT-2 weights are included; this is algorithm/code-convention attribution, not a claim that app replies use GPT-2.
- The downloaded Android API stubs and dx tool declare Apache-2.0 in their Maven POMs: [Android 1.5_r4 POM](https://repo.maven.apache.org/maven2/com/google/android/android/1.5_r4/android-1.5_r4.pom), [dx 1.7 POM](https://repo.maven.apache.org/maven2/com/google/android/tools/dx/1.7/dx-1.7.pom). They are build inputs, not copied into app DEX or committed JARs. Other locally installed SDK/JDK/tools remain subject to their own distribution terms.
- Manifest packaging additionally uses the Android SDK Platform 26 framework resource schema from [Google's SDK archive](https://dl.google.com/android/repository/platform-26_r02.zip), under the applicable [Android SDK terms](https://developer.android.com/studio/terms). The archive/framework is cached only as a build tool, not committed or bundled in the APK. It supplies manifest attribute IDs, not runtime classes; app Java still compiles against API 3. This is unrelated to model training-data provenance.
- Optional export/reference tests use NumPy, safetensors, PyTorch and Transformers installed separately. Those packages are not bundled in the Android app and retain their own licenses/notices. Installing a dependency is not permission to strip its license if later redistributing it.

## Before claiming unrestricted model distribution

1. Recover exact historical model/dataset revisions and selected row/source identifiers.
2. Obtain upstream permission/clarification for the Explore-Instruct restriction and missing/ambiguous mixture licenses. If needed, replace affected training sources and retrain the relevant lineage; removing a notice or a dataset file does not untrain weights.
3. Clarify WikiText versions, DCLM ancestry and unpublished distilled-set provenance with the publishers.
4. Review transitive content/code/generator rights and applicable law for the intended release, especially public/commercial deployment.
5. Preserve licenses, attributions and change notices with model/APK distributions. The build embeds project legal documents under `assets/legal/`.

Repository visibility is not a substitute for permission. A public repository does not establish third-party rights or clear the model for commercial use. The app code can have a clear license while model clearance remains unresolved. This audit cannot honestly certify that no licenses or obligations apply to all training sources.
