"""Validate an image's DOS partition table, including its logical-partition chain."""
import argparse,gzip,json,struct

EXTENDED={0x05,0x0f,0x85}

def partition_table(stream,image_bytes):
    if image_bytes<=0 or image_bytes%512:raise ValueError('Invalid image length')
    limit=image_bytes//512;metadata={};parts=[];containers=[]
    def sector(lba):
        if not 0<=lba<limit:raise ValueError('Partition table outside image')
        stream.seek(lba*512);b=stream.read(512)
        if len(b)!=512 or b[510:]!=b'\x55\xaa':raise ValueError('Missing partition-table signature at sector '+str(lba))
        metadata[lba]=b
        return [(b[446+i*16],b[450+i*16],*struct.unpack_from('<II',b,454+i*16)) for i in range(4)]
    def extent(kind,start,count,lower=1,upper=None):
        if not kind or count<=0 or start<lower or start+count>(limit if upper is None else upper):raise ValueError('Partition extent outside bounds')
    for i,(boot,kind,start,count) in enumerate(sector(0),1):
        if boot not in [0,128]:raise ValueError('Invalid partition boot flag')
        if not kind:
            if start or count:raise ValueError('Nonempty unused partition entry')
            continue
        extent(kind,start,count)
        if kind in EXTENDED:containers.append((start,count))
        else:parts.append({'number':i,'type':kind,'start_sector':start,'sectors':count})
    if len(containers)>1:raise ValueError('Multiple extended containers')
    primary=[(p['start_sector'],p['start_sector']+p['sectors']) for p in parts]+[(s,s+c) for s,c in containers]
    for a,b in zip(sorted(primary),sorted(primary)[1:]):
        if a[1]>b[0]:raise ValueError('Overlapping primary partitions')
    if containers:
        base,size=containers[0];current=base;number=5;visited=set()
        while True:
            if current in visited or not base<=current<base+size:raise ValueError('Invalid extended-partition chain')
            visited.add(current);rows=sector(current)
            boot,kind,offset,count=rows[0]
            if boot not in [0,128] or kind in EXTENDED or offset<1:raise ValueError('Invalid logical partition')
            start=current+offset;extent(kind,start,count,base,base+size)
            parts.append({'number':number,'type':kind,'start_sector':start,'sectors':count});number+=1
            if any(any(r) for r in rows[2:]):raise ValueError('Unexpected extended-table entries')
            boot,kind,offset,count=rows[1]
            if not kind:
                if boot or offset or count:raise ValueError('Invalid end of partition chain')
                break
            if boot or kind not in EXTENDED:raise ValueError('Invalid extended-partition link')
            next_sector=base+offset;extent(kind,next_sector,count,base,base+size)
            if next_sector<=current:raise ValueError('Cyclic/backward extended-partition link')
            current=next_sector
    ordered=sorted(parts,key=lambda p:p['start_sector'])
    for a,b in zip(ordered,ordered[1:]):
        if a['start_sector']+a['sectors']>b['start_sector']:raise ValueError('Overlapping logical/data partitions')
    for lba in metadata:
        if any(p['start_sector']<=lba<p['start_sector']+p['sectors'] for p in parts):raise ValueError('Partition table overlaps data')
    return sorted(parts,key=lambda p:p['number']),metadata

def validate_manifest(stream,manifest):
    parts,metadata=partition_table(stream,manifest['bytes'])
    actual={(p['start_sector'],p['sectors']*512) for p in parts}
    for p in manifest['partitions']:
        if (p['start_sector'],p['bytes']) not in actual:raise ValueError('Partition is not discoverable: '+p['name'])
    return parts,metadata

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('image');parser.add_argument('--manifest',required=True)
    args=parser.parse_args()
    with open(args.manifest) as f:manifest=json.load(f)
    with (gzip.open if args.image.endswith('.gz') else open)(args.image,'rb') as f:parts,metadata=validate_manifest(f,manifest)
    print('PASS:',len(parts),'discoverable partitions;',len(metadata),'partition-table sectors; all manifest extents match')
