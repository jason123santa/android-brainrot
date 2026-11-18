#!/usr/bin/env python3
"""Exercise the bundled model on a host JVM using only Python's standard library."""
import hashlib
import os
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    model = ROOT / 'assets/model.bin'
    expected = (ROOT / 'assets/model.bin.sha256').read_text(encoding='ascii').split()[0]
    assert hashlib.sha256(model.read_bytes()).hexdigest() == expected, 'Model checksum mismatch'
    build = ROOT / 'build/host-smoke'
    build.mkdir(parents=True, exist_ok=True)
    subprocess.run([os.environ.get('JAVAC', 'javac'), '-encoding', 'UTF-8', '-source', '1.5',
                    '-target', '1.5', '-d', str(build),
                    str(ROOT / 'src/org/brainrot/legacy/Model.java'),
                    str(ROOT / 'src/org/brainrot/legacy/Tokenizer.java'),
                    str(ROOT / 'tests/HostCheck.java')], check=True)
    fixtures = ['', 'User: hello\nAssistant:', 'skibidi rizz ohio 💀🔥', "I'm he's won't WE'RE",
                '123 456.789 1st', ' a  b\t\n\n  ', 'café 中文 العربية',
                '<|user|>hi<|end|><|assistant|>', '<think>hmm</think>', '\x00\x01\x7f']
    (build / 'fixtures.hex').write_text('\n'.join(s.encode('utf-8').hex() for s in fixtures) + '\n',
                                      encoding='ascii')
    subprocess.run([os.environ.get('JAVA', 'java'), '-Xmx16m', '-cp', str(build), 'HostCheck', str(model),
                    str(build / 'fixtures.hex'), str(build / 'ids.txt'), str(build / 'logits.bin')], check=True)
    assert len((build / 'ids.txt').read_text(encoding='utf-8').splitlines()) == len(fixtures)
    print('PASS: bundled model checksum, tokenizer round trips, generation, cache reset, bounds and cancellation')
    print('Host JVM smoke test only; no original checkpoint or Python packages required.')


if __name__ == '__main__':
    main()
