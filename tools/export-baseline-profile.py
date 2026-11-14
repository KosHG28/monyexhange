#!/usr/bin/env python3
"""Export actually recorded application profiles; a dependency-only profile is not sufficient."""
from pathlib import Path
import shutil

out = Path('baseline-out')
out.mkdir(exist_ok=True)
for name in ['baseline-prof.txt', 'startup-prof.txt']:
    candidates = [p for p in Path('app/src').rglob(name) if 'Lapp/dirhamrub/' in p.read_text()]
    assert candidates, f'No recorded application rules in {name}'
    path = max(candidates, key=lambda p: p.stat().st_mtime)
    text = path.read_text()
    rules = [line for line in text.splitlines() if line and not line.startswith('#')]
    own = [line for line in rules if 'Lapp/dirhamrub/' in line]
    assert len(own) >= 10, f'Only {len(own)} app rules in {name}'
    assert any('Lapp/dirhamrub/MainActivity;' in line for line in own), 'Startup Activity missing'
    shutil.copy2(path, out / name)
    print(f'{name}: {len(rules)} recorded rules, {len(own)} app rules; {path}')
