"""Binary manifest permission insertion keeps all existing string/resource indices stable."""
from pathlib import Path
import struct,sys,unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
from car_dialer_manifest import add_overlay_permission,PERMISSION

def start(name):return struct.pack('<HHI2I2I6H',0x102,16,36,0,0xffffffff,0xffffffff,name,20,20,0,0,0,0)
def fixture(utf8):
    strings=['uses-permission','application','http://schemas.android.com/apk/res/android','name','manifest'];body=b'';offsets=[]
    for value in strings:
        offsets.append(len(body));body+=(bytes([len(value),len(value)])+value.encode()+b'\0')if utf8 else struct.pack('<H',len(value))+value.encode('utf-16le')+b'\0\0'
    body+=b'\0'*((-len(body))%4);at=28+4*len(strings)
    pool=struct.pack('<HHI5I',1,28,at+len(body),len(strings),0,0x100 if utf8 else 0,at,0)+struct.pack('<5I',*offsets)+body
    chunks=pool+start(4)+start(1);return struct.pack('<HHI',3,8,len(chunks)+8)+chunks

class ManifestTests(unittest.TestCase):
    def test_permission_before_application_preserves_old_elements(self):
        for utf8 in [False,True]:
            before=fixture(utf8);after=add_overlay_permission(before)
            self.assertEqual(struct.unpack_from('<I',after,4)[0],len(after));self.assertTrue(after.endswith(start(1)))
            self.assertIn(start(4),after);self.assertEqual(struct.unpack_from('<I',after,16)[0],6)
            at=8+struct.unpack_from('<I',after,12)[0]+len(start(4))
            self.assertEqual(struct.unpack_from('<HHI',after,at),(0x102,16,56))
            self.assertEqual(struct.unpack_from('<3IHBBI',after,at+36),(2,3,5,8,0,3,5))
            self.assertIn(PERMISSION.encode('utf-8' if utf8 else 'utf-16le'),after)
            with self.assertRaises(ValueError):add_overlay_permission(after)
    def test_rejects_truncation(self):
        data=fixture(False)
        with self.assertRaises(ValueError):add_overlay_permission(data[:-1])
        bad=bytearray(data);struct.pack_into('<I',bad,12,0)
        with self.assertRaises(ValueError):add_overlay_permission(bytes(bad))

if __name__=='__main__':unittest.main()
