#!/usr/bin/env python3
"""Do not capture unchanged screens or the country guide (owner's standing exclusion)."""
from pathlib import Path
import os
import subprocess
import sys

GUIDE = 'app/src/main/java/app/dirhamrub/ui/guide/'
PALETTE = 'app/src/main/java/app/dirhamrub/ui/theme/EasterEggPalette.kt'

def needs_screens(paths, version_only=False):
    def guide(path):
        return path.startswith(GUIDE) or path == PALETTE or path.startswith('app/src/main/res/drawable/ic_egg_')
    def supporting(path):
        return (path.startswith(('docs/', 'app/src/test/')) or
                path in {'CLAUDE.md', 'tools/ui-scope.py', '.github/workflows/android.yml'} or
                path == 'app/build.gradle.kts' and version_only)
    # Unknown files conservatively keep the existing screen and visual approval gates.
    return not (any(guide(p) for p in paths) and all(guide(p) or supporting(p) for p in paths))

def main():
    if '--self-test' in sys.argv:
        assert not needs_screens([GUIDE+'CountryEasterEgg.kt', PALETTE])
        assert not needs_screens([GUIDE+'EasterEggParticleOverlay.kt', 'app/build.gradle.kts'], True)
        assert needs_screens([GUIDE+'CountryEasterEgg.kt', 'app/build.gradle.kts'], False)
        assert needs_screens([GUIDE+'CountryEasterEgg.kt', 'app/src/main/java/app/dirhamrub/ui/main/MainScreen.kt'])
        assert needs_screens(['app/src/main/res/values/strings.xml'])
        assert needs_screens(['docs/PLAN.md'])
        assert needs_screens([])
        print('UI scope: 7 cases passed; unknown/main changes preserve screenshot gates.')
        return
    head = os.environ.get('SCOPE_HEAD', 'HEAD')
    base = os.environ.get('SCOPE_BASE', '')
    if not base or set(base) == {'0'}:
        try:
            base = subprocess.check_output(['git', 'rev-parse', head+'^'], text=True).strip()
        except subprocess.CalledProcessError:
            base = subprocess.check_output(['git', 'hash-object', '-t', 'tree', '/dev/null'], text=True).strip()
    paths = subprocess.check_output(['git', 'diff', '--name-only', '-z', base, head]).decode().strip('\0').split('\0')
    diff = subprocess.check_output(['git', 'diff', '--unified=0', base, head, '--', 'app/build.gradle.kts'], text=True)
    changed = [s[1:].strip() for s in diff.splitlines() if s[:1] in ('+', '-') and not s.startswith(('+++','---'))]
    version_only = bool(changed) and all(s.startswith('versionName = ') for s in changed)
    result = str(needs_screens(paths, version_only)).lower()
    print('screens='+result)
    if result == 'false':
        print('Only the country guide changed. Its screenshots are excluded by the owner; other screens are unchanged.')
    if os.environ.get('GITHUB_OUTPUT'):
        with Path(os.environ['GITHUB_OUTPUT']).open('a') as f:
            f.write('screens='+result+'\n')

if __name__ == '__main__':
    main()
