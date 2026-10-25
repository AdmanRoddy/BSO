# Old Electron (Chromium 56) exports its own copy of HarfBuzz, which crashes newer GTK text code.
# Renaming those exported names makes GTK use the system copy; Electron keeps its own internally.
import re, subprocess, sys
p = sys.argv[1]
out = subprocess.run(['readelf', '-SW', p], capture_output=True, text=True).stdout
m = re.search(r'\.dynstr\s+STRTAB\s+[0-9a-f]+\s+([0-9a-f]+)\s+([0-9a-f]+)', out)
off, size = int(m.group(1), 16), int(m.group(2), 16)
b = bytearray(open(p, 'rb').read())
seg = bytes(b[off:off + size]); n = seg.count(b'\x00hb_')
b[off:off + size] = seg.replace(b'\x00hb_', b'\x00xb_')
open(p, 'wb').write(b); print('renamed', n, 'symbols')
