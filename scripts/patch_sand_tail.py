"""Correct three SAND30 tail registers; refuse any unrecognized instruction sequence."""
import hashlib
import json
from pathlib import Path
import sys

source=Path(sys.argv[1]);out=Path(sys.argv[2])
data=source.read_bytes()
assert data[:6]==b'\x7fELF\x02\x01' and int.from_bytes(data[18:20],'little')==183, 'Expected little-endian AArch64 ELF'
# st3 first48, branch, then the mistaken temporary-register moves in the y8 tail.
pattern=bytes.fromhex('10409f4c00ffff54d01eb64ef11eb74ea5c00051121fb84ebf200131')
assert data.count(pattern)==1, 'Unsupported libavutil; no patch written'
start=data.index(pattern)+8
patched=bytearray(data)
for relative,old,new in ((0,'d01eb64e','701eb34e'),(4,'f11eb74e','911eb44e'),(12,'121fb84e','b21eb54e')):
    assert data[start+relative:start+relative+4]==bytes.fromhex(old)
    patched[start+relative:start+relative+4]=bytes.fromhex(new)
assert len(patched)==len(data)
assert sum(a!=b for a,b in zip(data,patched))==7
out.write_bytes(patched)
out.with_suffix('.json').write_text(json.dumps({
    'original_sha256':hashlib.sha256(data).hexdigest(),
    'patched_sha256':hashlib.sha256(patched).hexdigest(),
    'instruction_offsets':[start,start+4,start+12],
    'fix':'Use second decoded output vectors v19/v20/v21 after the first48 tail pixels, instead of scratch vectors v22/v23/v24.',
    'source':'https://github.com/raspberry-vanilla/android_external_ffmpeg/blob/android-16.0/libavutil/aarch64/rpi_sand_neon.S',
},indent=2)+'\n')
print('Patched three instructions only:',out)
