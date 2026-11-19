#!/usr/bin/env python3
"""Fetch pinned, checksum-verified Shizuku API jars into the ignored build cache."""
import hashlib, io, json, pathlib, urllib.request, zipfile
root=pathlib.Path(__file__).resolve().parent.parent
cache=root/'work/android-dependencies';cache.mkdir(parents=True,exist_ok=True)
for item in json.loads((root/'android/dependencies.json').read_text()):
    archive=cache/(item['name']+'.aar')
    if not archive.exists() or hashlib.sha256(archive.read_bytes()).hexdigest()!=item['sha256']:
        data=urllib.request.urlopen(item['url'],timeout=30).read()
        if hashlib.sha256(data).hexdigest()!=item['sha256']:raise RuntimeError('Dependency checksum mismatch: '+item['name'])
        archive.write_bytes(data)
    with zipfile.ZipFile(io.BytesIO(archive.read_bytes())) as aar:
        (cache/item['name']).write_bytes(aar.read('classes.jar'))
