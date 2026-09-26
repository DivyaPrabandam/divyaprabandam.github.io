#!/usr/bin/env python3
"""Validate the audio mapping against the bundled canonical verse rows."""
import json,glob,re
from pathlib import Path
base=Path(__file__).resolve().parents[2]/'app/src/main/assets'
verses=json.loads((base/'audio/verses.json').read_text())
groups=json.loads((base/'audio/groups.json').read_text())
pattern=re.compile(r'https://media\.divyaprabandam\.workers\.dev/media/[a-f0-9]{12}\.(?:mp3|m4a)\Z')
assert len(verses)==3882 and len(groups)==236
assert all(pattern.fullmatch(u) for u in verses.values())
assert all(pattern.fullmatch(u) for tracks in groups.values() for name,u in tracks)
rows={}
for path in (base/'books').glob('*.json'):
 if path.name=='manifest.json':continue
 book=json.loads(path.read_text())
 for section in book['sections']:
  for row in section['p']:rows[str(row[0])]=(book['id'],row[0],row[1])
assert set(verses)==set(rows)-{str(n) for n in list(range(2673,2713))+list(range(2713,2791))}
assert groups['22-siriya-thirumadal:2673'][0][0].endswith('2673–2712')
assert groups['23-periya-thirumadal:2713'][0][0].endswith('2713–2790')
assert len(rows)==4000
print({'individual_tracks':len(verses),'canonical_rows':len(rows),'group_recordings':sum(map(len,groups.values())),'madals_full_passage':2})
