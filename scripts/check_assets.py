import json,glob,hashlib,sys
root=sys.argv[1]+'/app/src/main/assets/books';m=json.load(open(root+'/manifest.json')); assert len(m)==25
seen={};errors=[]
for b in m:
 p=root+'/'+b['file'];raw=open(p,'rb').read();j=json.loads(raw)
 if hashlib.sha256(raw).hexdigest()!=b['sha256']:errors.append('hash '+b['file'])
 if len(raw)!=b['bytes']:errors.append('size '+b['file'])
 if j['start']!=b['start'] or j['end']!=b['end']:errors.append('range '+b['file'])
 for section in j['sections']:
  for row in section['p']:
   a,z=row[:2]
   if len(row[2])!=len(row[3]):errors.append('line-pair '+b['file']+' '+str(a))
   for n in range(a,z+1):seen[n]=seen.get(n,0)+1
missing=[n for n in range(1,4001) if n not in seen];dup=[n for n,v in seen.items() if v!=1]
print(json.dumps({'books':len(m),'numbered_coverage':len(seen),'missing':len(missing),'duplicates':len(dup),'errors':errors[:10],'total_source_bytes':sum(b['bytes'] for b in m)}))
if missing or dup or errors:sys.exit(1)
