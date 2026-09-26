#!/usr/bin/env python3
"""Audit split madals against the frozen canonical site source and numbered edition cursors.

Primary numbered edition:
https://www.prapatti.com/slokas/english/naalaayiram/tirumangaiyaazvaar/shiriyatirumadal.pdf
https://www.prapatti.com/slokas/english/naalaayiram/tirumangaiyaazvaar/periyatirumadal.pdf
The canonical text prior to splitting is frozen in commit 1dc6663.
"""
import json, subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[2]
audit=json.loads((root/'scripts/madal_mapping_audit.json').read_text())
for key,first,last in [('siriya',2673,2712),('periya',2713,2790)]:
 entry=audit[key];file=entry['source'];rel='app/src/main/assets/books/'+file
 raw=subprocess.check_output(['git','show','1dc6663:'+rel],cwd=root)
 original=json.loads(raw)['sections'][0]['p'][0]
 updated=json.loads((root/rel).read_text())['sections'][0]
 rows=updated['p'];cursors=entry['boundaries']
 assert len(rows)==len(cursors)==last-first+1
 assert [x['n'] for x in cursors]==list(range(first,last+1))
 assert [x[:2] for x in rows]==[[n,n] for n in range(first,last+1)]
 assert all(x['match_score']>=80 for x in cursors)
 assert all(cursors[i]['line']<cursors[i+1]['line'] or
   cursors[i]['line']==cursors[i+1]['line'] and cursors[i]['latin_col']<cursors[i+1]['latin_col']
   for i in range(len(cursors)-1))
 ending=entry['epilogue_first_line']
 for index,keyname,column in [(2,'le','latin_col'),(3,'ta','tamil_col')]:
  lines=original[index]
  cuts=[(x['line'],x[column]) for x in cursors]+[(ending,0)]
  fragments={i:[] for i in range(len(lines))}
  for j,row in enumerate(rows):
   line,col=cuts[j];nextline,nextcol=cuts[j+1]
   if line==nextline:expected=[lines[line][col:nextcol]]
   else:expected=[lines[line][col:]]+lines[line+1:nextline]+([lines[nextline][:nextcol]] if nextcol else [])
   expected=[x for x in expected if x]
   assert row[index]==expected, (key,row[0],index)
   if line==nextline:fragments[line].append(lines[line][col:nextcol])
   else:
    fragments[line].append(lines[line][col:])
    for i in range(line+1,nextline):fragments[i].append(lines[i])
    if nextcol:fragments[nextline].append(lines[nextline][:nextcol])
  assert updated['epilogue'][keyname]==lines[ending:]
  for i in range(ending,len(lines)):fragments[i].append(lines[i])
  assert all(''.join(fragments[i])==line for i,line in enumerate(lines))
 print(key,len(rows),'boundaries, original Latin/Tamil line arrays reconstructed exactly, ending kept separately')
