#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Check native-theme/sizing contracts; this is NOT a visual Android UI test."""
import os
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    parents = {
        'values': '@android:style/Theme',
        'values-v11': '@android:style/Theme.Holo.Light',
        'values-night-v11': '@android:style/Theme.Holo',
        'values-v14': '@android:style/Theme.DeviceDefault.Light',
        'values-night-v14': '@android:style/Theme.DeviceDefault',
    }
    for directory, parent in parents.items():
        style = ET.parse(ROOT / 'res' / directory / 'styles.xml').getroot().find('style')
        assert style.get('name') == 'AppTheme' and style.get('parent') == parent
        items = {item.get('name'): item.text for item in style.findall('item')}
        expected = {'android:windowNoTitle': 'false', 'android:windowFullscreen': 'false'}
        if directory != 'values':
            expected['android:windowActionBar'] = 'true'
        assert items == expected, 'Only native window decorations should be overridden'
    activity = (ROOT / 'src/org/brainrot/legacy/ChatActivity.java').read_text(encoding='utf-8')
    for custom_style in ['GradientDrawable', 'StateListDrawable', 'setBackground', 'setTextColor',
                         'setHintTextColor', 'setTextSize', 'setTypeface', 'setMinWidth', 'setMinHeight']:
        assert custom_style not in activity, 'Custom widget styling was reintroduced'
    assert 'send.setPadding(' not in activity and 'input.setPadding(' not in activity
    assert 'TextAppearance_Small' in activity and 'TextAppearance_Medium' in activity
    for full_text in ['Loading offline model...', 'Ready - 128-token context, 32-token replies. CPU only.',
                      'Send a short message to start. Replies are generated offline.',
                      'Each message starts a fresh context.', 'Message (short prompts work best)',
                      'Thinking... Old CPUs may take a long time. Stop cancels.']:
        assert full_text in activity, 'Full app wording was shortened: ' + full_text
    assert 'status.setMaxLines' not in activity and 'status.setEllipsize' not in activity
    assert 'Toast' not in activity and 'AlertDialog' not in activity, 'App-owned popups were reintroduced'
    build = ROOT / 'build/host-ui'
    build.mkdir(parents=True, exist_ok=True)
    subprocess.run([os.environ.get('JAVAC', 'javac'), '-encoding', 'UTF-8', '-source', '1.5',
                    '-target', '1.5', '-d', str(build), str(ROOT / 'src/org/brainrot/legacy/UiLayout.java'),
                    str(ROOT / 'tests/UiLayoutCheck.java')], check=True)
    subprocess.run([os.environ.get('JAVA', 'java'), '-cp', str(build),
                    'org.brainrot.legacy.UiLayoutCheck'], check=True)
    print('PASS: platform-native themes, no custom widget styling, sizing policy')
    print('Real layout, native widget measurements and keyboard behavior still need device testing.')


if __name__ == '__main__':
    main()
