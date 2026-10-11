"""Check the known source provenance boundaries without treating license texts as app code."""
from pathlib import Path
import hashlib, json, re, sys

root = Path(__file__).resolve().parent.parent
manifest = json.loads((root / 'docs/licenses/miuix-example-provenance.json').read_text(encoding='utf-8'))
errors = []
for entry in manifest['files']:
    path = root / entry['path']
    if not path.is_file() or hashlib.sha256(path.read_bytes().replace(b'\r\n', b'\n')).hexdigest() != entry['adaptedSha256']:
        errors.append('Review and update Miuix provenance for ' + entry['path'])
for source in (root / 'app/src/main').rglob('*'):
    if source.suffix not in {'.kt', '.java', '.cc', '.cpp', '.h'}: continue
    header = '\n'.join(source.read_text(encoding='utf-8', errors='replace').splitlines()[:35])
    if re.search(r'SPDX-License-Identifier:\s*(?:AGPL|GPL)-', header):
        errors.append('Copyleft application source requires explicit integration review: ' + str(source.relative_to(root)))
    if 'KernelSU' in header or 'InstallerX Revived' in header:
        errors.append('Removed GPL navigation provenance reintroduced: ' + str(source.relative_to(root)))
if (root / 'app/src/main/java/com/ella/music/ui/util/DragGestureInspector.kt').exists():
    errors.append('The retired GPL gesture inspector must remain absent')
if errors:
    print('\n'.join(errors)); sys.exit(1)
print('Application headers and pinned Miuix adaptation provenance passed')
