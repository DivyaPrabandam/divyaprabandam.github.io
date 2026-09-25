#!/usr/bin/env python3
"""Production publisher QA: verify raw signed manifest and all shard bytes."""
import base64, hashlib, json, subprocess, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
URL = 'https://publisher.divyaprabandam.workers.dev/manifest'
SPKI_B64 = 'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEtu9UWY5Csac9VQXqRxY3g3P+sshwrvTQ45gKQRpyXtVoyw/QA/hxSk+H2QWplRORdZsZE6AEazZLFap8IDsrVw=='

def fetch(url, headers=False):
    if not url.startswith('https://publisher.divyaprabandam.workers.dev/'):
        raise ValueError('Unexpected publisher host')
    with tempfile.TemporaryDirectory() as temp:
        data = Path(temp)/'body'
        args = ['curl', '--fail', '--silent', '--show-error', '--max-time', '20', '-o', str(data)]
        if headers:
            head = Path(temp)/'headers'
            args += ['-D', str(head)]
        subprocess.run(args + [url], check=True)
        return data.read_bytes(), head.read_text() if headers else None

body, headers = fetch(URL, True)
signature = next(value.strip() for line in headers.splitlines() if line.lower().startswith('x-content-signature:') for value in [line.split(':',1)[1]])
with tempfile.TemporaryDirectory() as temp:
    path = Path(temp)
    (path/'body').write_bytes(body)
    (path/'key').write_bytes(base64.b64decode(SPKI_B64))
    (path/'sig').write_bytes(base64.b64decode(signature))
    subprocess.run(['openssl','pkeyutl','-verify','-pubin','-inkey',str(path/'key'),'-keyform','DER','-sigfile',str(path/'sig'),'-in',str(path/'body'),'-rawin','-digest','sha256'],check=True,stdout=subprocess.DEVNULL)
manifest = json.loads(body)
assert (manifest['schemaVersion'], manifest['contentVersion'], manifest['minAppSchema'], manifest['maxAppSchema']) == (1,1,1,1)
assert manifest['images'] == []
books = json.loads((ROOT/'app/src/main/assets/books/manifest.json').read_text())
assert len(manifest['books']) == len(books) == 25
assert [x['id'] for x in manifest['books']] == [x['id'] for x in books]
total = 0
for item, original in zip(manifest['books'],books):
    raw,_ = fetch(item['url'])
    assert len(raw) == item['bytes'] and hashlib.sha256(raw).hexdigest() == item['sha256']
    bundled = (ROOT/'app/src/main/assets/books'/original['file']).read_bytes()
    assert bundled == raw
    total += len(raw)
print(json.dumps({'manifest_sha256':hashlib.sha256(body).hexdigest(),'manifest_bytes':len(body),'signature':'verified','books':len(books),'shard_bytes':total,'bundled_match':True}))
