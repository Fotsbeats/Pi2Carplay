"""Catch images whose partition bytes exist but their EBR chain was omitted."""
import io,struct,sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
from image_layout import validate_manifest

def table(rows):
    b=bytearray(512);b[510:]=b'\x55\xaa'
    for i,(kind,start,count) in enumerate(rows):b[450+i*16]=kind;struct.pack_into('<II',b,454+i*16,start,count)
    return b

class LayoutTest(unittest.TestCase):
    def setUp(self):
        self.data=bytearray(128*512)
        self.data[:512]=table([(12,2,8),(5,10,80),(131,90,38)])
        self.data[10*512:11*512]=table([(131,2,30),(5,40,40)])
        self.data[50*512:51*512]=table([(131,2,30)])
        self.manifest={'bytes':len(self.data),'partitions':[{'name':'system','start_sector':12,'bytes':30*512},{'name':'vendor','start_sector':52,'bytes':30*512}]}
    def check(self):return validate_manifest(io.BytesIO(self.data),self.manifest)
    def test_complete_chain(self):
        parts,metadata=self.check();self.assertEqual([p['number'] for p in parts],[1,3,5,6]);self.assertEqual(set(metadata),{0,10,50})
    def test_missing_first_ebr(self):
        self.data[10*512:11*512]=bytes(512)
        with self.assertRaisesRegex(ValueError,'signature'):self.check()
    def test_missing_second_ebr(self):
        self.data[50*512:51*512]=bytes(512)
        with self.assertRaisesRegex(ValueError,'signature'):self.check()
    def test_partition_outside_container(self):
        self.data[50*512:51*512]=table([(131,2,70)])
        with self.assertRaisesRegex(ValueError,'bounds'):self.check()
    def test_backward_link(self):
        self.data[50*512:51*512]=table([(131,2,30),(5,0,80)])
        with self.assertRaisesRegex(ValueError,'backward'):self.check()
    def test_wrong_manifest_extent(self):
        self.manifest['partitions'][0]['start_sector']=13
        with self.assertRaisesRegex(ValueError,'discoverable'):self.check()
    def test_table_overlaps_data(self):
        self.data[10*512:11*512]=table([(131,2,50),(5,40,40)])
        with self.assertRaisesRegex(ValueError,'Overlapping|overlaps'):self.check()

if __name__=='__main__':unittest.main()
