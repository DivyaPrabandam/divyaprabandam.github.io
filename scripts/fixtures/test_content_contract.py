#!/usr/bin/env python3
"""Offline publisher-contract fixture. All keys here are ephemeral and never shipped."""
import hashlib,json,pathlib,copy
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import hashes,serialization
from cryptography.exceptions import InvalidSignature
root=pathlib.Path(__file__).resolve().parents[2]
assets=root/'app/src/main/assets/books'
source=json.loads((assets/'manifest.json').read_text())
books=[];coverage={}
for entry in source:
    data=(assets/entry['file']).read_bytes()
    books.append({k:entry[k] for k in ('id','file','sha256','bytes')})
    books[-1]['url']='https://fixture.invalid/shards/'+entry['sha256']+'.json'
    doc=json.loads(data)
    for section in doc['sections']:
        for row in section['p']:
            for n in range(row[0],row[1]+1):coverage[n]=coverage.get(n,0)+1
assert len(books)==25 and set(coverage)==set(range(1,4001)) and max(coverage.values())==1
manifest={'schemaVersion':1,'contentVersion':1,'minAppSchema':1,'maxAppSchema':1,'books':books,'images':[]}
raw=json.dumps(manifest,separators=(',',':'),ensure_ascii=False).encode()
private=ec.generate_private_key(ec.SECP256R1())
public=private.public_key().public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)
sig=private.sign(raw,ec.ECDSA(hashes.SHA256()))
private.public_key().verify(sig,raw,ec.ECDSA(hashes.SHA256()))
try:
    private.public_key().verify(sig,raw+b' ',ec.ECDSA(hashes.SHA256()))
    raise AssertionError('tamper accepted')
except InvalidSignature:pass
changed=copy.deepcopy(manifest);changed['contentVersion']=2
file=assets/source[2]['file'];book=json.loads(file.read_text());book['sections'][0]['p'][0][3][0]+=' [FIXTURE ONLY]'
newbytes=json.dumps(book,ensure_ascii=False,separators=(',',':')).encode()
changed['books'][2]['bytes']=len(newbytes)
changed['books'][2]['sha256']=hashlib.sha256(newbytes).hexdigest()
assert changed['books'][2]['sha256']!=source[2]['sha256']
changed['minAppSchema']=2;changed['maxAppSchema']=2
changed['appUpdate']={'versionCode':5,'bytes':5000000,'sha256':'a'*64,'url':'https://fixture.invalid/app.apk'}
assert not changed['minAppSchema']<=1<=changed['maxAppSchema']
print(json.dumps({'books':len(books),'covered':len(coverage),'fixtureChangedBook':books[2]['id'],'signature':'positive/tamper-negative','schemaGate':'requires-update'}))
