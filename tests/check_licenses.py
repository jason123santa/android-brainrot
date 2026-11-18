#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Check local license/notice consistency, not legal clearance or upstream rights."""
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    # Only the project header and appendix attribution personalize the Apache text.
    copyright_header = b'Copyright (c) 2026 jason123santa\nCopyright notice date: 11/18/2026\n'
    license_text = (ROOT / 'LICENSE').read_bytes()
    assert license_text.startswith(copyright_header)
    template = b'   Copyright [yyyy] [name of copyright owner]\n'
    attribution = b'   Copyright (c) 2026 jason123santa\n'
    assert template not in license_text
    body = license_text[len(copyright_header):]
    assert body.count(attribution) == 1
    canonical = body.replace(attribution, template, 1)
    assert hashlib.sha256(canonical).hexdigest() == (
        'cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30')
    project_notice = (ROOT / 'NOTICE').read_text(encoding='utf-8')
    assert 'Copyright (c) 2026 jason123santa' in project_notice
    assert 'Copyright notice date: 11/18/2026' in project_notice
    audit = json.loads((ROOT / 'docs/upstream-licenses.json').read_text(encoding='utf-8'))
    model_hash = hashlib.sha256((ROOT / 'assets/model.bin').read_bytes()).hexdigest()
    assert audit['bundled_model_sha256'] == model_hash
    assert model_hash in (ROOT / 'MODEL_LICENSE.md').read_text(encoding='utf-8')
    sources = {source['id']: source for source in audit['sources']}
    assert len(sources) == len(audit['sources']) == 20
    for source in sources.values():
        assert re.fullmatch(r'[0-9a-f]{40}', source['audited_card_revision'])
        assert re.fullmatch(r'[0-9a-f]{64}', source['card_sha256'])
        assert source['audited_card_revision'] in source['card_url']
        assert source['card_url'].startswith('https://huggingface.co/')
    assert sources['nkthebass/tinybrainbot-10m-v2-base']['declared_card_license'] == 'apache-2.0'
    assert sources['Wanfq/Explore_Instruct_Rewriting_32k']['declared_card_license'] == 'cc-by-nc-4.0'
    assert sources['teknium/OpenHermes-2.5']['declared_card_license'] is None
    stats = audit['current_smol_smoltalk_statistics']
    assert stats['partial'] is False
    assert sum(stats['source_frequencies'].values()) == stats['num_examples']
    assert stats['source_frequencies']['explore-instruct-rewrite'] == 3017
    assert 'not a legal clearance' in audit['scope']
    for directory in ['src', 'tests', 'tools']:
        for path in (ROOT / directory).rglob('*'):
            if path.suffix in ('.java', '.py', '.sh'):
                assert 'SPDX-License-Identifier: Apache-2.0' in path.read_text(encoding='utf-8')
    for document in ['NOTICE', 'MODEL_LICENSE.md', 'THIRD_PARTY_NOTICES.md']:
        assert (ROOT / document).is_file()
    upstream_notice = (ROOT / 'licenses/OpenAI-GPT2-LICENSE.txt').read_text(encoding='utf-8')
    assert 'Modified MIT License' in upstream_notice and 'Software Copyright (c) 2019 OpenAI' in upstream_notice
    readme = (ROOT / 'README.md').read_text(encoding='utf-8')
    assert 'AI coding assistant' in readme and 'clearance of the bundled model is unresolved' in readme
    print('PASS: license text, artifact/provenance hashes, SPDX headers, retained notices and documented caveats')
    print('This is a consistency test only; unresolved model/data permissions remain unresolved.')


if __name__ == '__main__':
    main()
