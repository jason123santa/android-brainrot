#!/usr/bin/env python3
"""Cross-check the actual Java engine against HF tokenizer and quantized PyTorch."""
import argparse
import subprocess
from pathlib import Path

import numpy as np
import torch
from transformers import AutoModelForCausalLM, AutoTokenizer

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True,
                        help='Directory containing the compatible HF reference checkpoint')
    parser.add_argument('--java-source', default='5', choices=['5', '7', '8'])
    args = parser.parse_args()
    build = ROOT / 'build/host'
    build.mkdir(parents=True, exist_ok=True)
    subprocess.run(['javac', '-encoding', 'UTF-8', '-source', args.java_source, '-target', args.java_source,
                    '-d', str(build), str(ROOT / 'src/org/brainrot/legacy/Model.java'),
                    str(ROOT / 'src/org/brainrot/legacy/Tokenizer.java'),
                    str(ROOT / 'tests/HostCheck.java')], check=True)
    fixtures = ['', 'User: hello\nAssistant:', 'skibidi rizz ohio 💀🔥', "I'm he's won't WE'RE",
                '123 456.789 1st', ' a  b\t\n\n  ', '\r\n\tfoo\u00a0bar\u0085',
                'café naïve 中文 日本語 العربية', '０１２ ²½Ⅻ', '💀👩\u200d💻',
                '<|user|>hi<|end|><|assistant|>', '<think>hmm</think>',
                'User: what is 2+2?\nAssistant:', '\x00\x01\x7f', ' !!!???  .',
                'a\u2028b\u2029c', ' space at end   ', 'tabs\t\tand spaces  \nnext']
    # Exercise BPE boundaries with reproducible ASCII/Unicode fuzzing.
    import random
    rng = random.Random(42)
    alphabet = "abcXYZ123 '!?\t\n_é中💀\u00a0²"
    fixtures += [''.join(rng.choice(alphabet) for _ in range(rng.randrange(1, 70))) for _ in range(120)]
    (build / 'fixtures.hex').write_text('\n'.join(s.encode('utf-8').hex() for s in fixtures) + '\n')
    subprocess.run(['java', '-Xmx16m', '-cp', str(build), 'HostCheck', str(ROOT / 'assets/model.bin'),
                    str(build / 'fixtures.hex'), str(build / 'ids.txt'), str(build / 'logits.bin')], check=True)
    tokenizer = AutoTokenizer.from_pretrained(args.source, local_files_only=True)
    actual = (build / 'ids.txt').read_text().splitlines()
    for text, line in zip(fixtures, actual):
        expected = tokenizer.encode(text, add_special_tokens=False)
        found = [int(x) for x in line.split(',')] if line else []
        assert expected == found, (repr(text), expected, found)
    assert len(actual) == len(fixtures)
    print('Tokenizer: %d fixtures match Hugging Face exactly' % len(fixtures))

    torch.set_num_threads(1)
    model = AutoModelForCausalLM.from_pretrained(args.source, local_files_only=True,
                                                attn_implementation='eager').eval()
    prompt = tokenizer('User: hello\nAssistant:', add_special_tokens=False, return_tensors='pt')
    with torch.no_grad():
        reference_fp32 = model(**prompt).logits[0].numpy().copy()
        # Same per-row quantization as the exporter; keep float32 for HF reference.
        for name, parameter in model.named_parameters():
            if parameter.ndim == 2:
                scale = parameter.abs().amax(dim=1).clamp(min=1.27e-28) / 127
                parameter.copy_((parameter / scale[:, None]).round().clamp(-127, 127) * scale[:, None])
        reference = model(**prompt).logits[0].numpy()
    java = np.fromfile(build / 'logits.bin', dtype='>f4').reshape(reference.shape)
    error = np.abs(reference - java)
    print('Java vs quantized HF: max abs error %.7f, RMS %.7f' %
          (error.max(), np.sqrt(np.mean(error**2))))
    np.testing.assert_allclose(java, reference, atol=0.002, rtol=0.002)
    assert np.array_equal(java.argmax(axis=-1), reference.argmax(axis=-1))
    print('All greedy token choices match; float32-to-int8 logit RMS: %.5f' %
          np.sqrt(np.mean((reference_fp32 - reference)**2)))
    print('PASS (host JVM only; this does not certify Android hardware)')


if __name__ == '__main__':
    main()
