"""Add the overlay permission to the pinned manifest without reassigning any resource IDs."""
import struct

PERMISSION='android.permission.SYSTEM_ALERT_WINDOW'
def add_overlay_permission(data):
    if len(data)<8 or struct.unpack_from('<HHI',data)!=(3,8,len(data)):raise ValueError('Invalid binary XML header')
    chunks=[];at=8
    while at<len(data):
        if at+8>len(data):raise ValueError('Truncated XML chunk')
        kind,header,size=struct.unpack_from('<HHI',data,at)
        if size<header or header<8 or at+size>len(data):raise ValueError('Invalid XML chunk bounds')
        chunks.append([kind,data[at:at+size]]);at+=size
    pool_index=next(i for i,(kind,_)in enumerate(chunks)if kind==1)
    pool=chunks[pool_index][1];_,header,size,count,styles,flags,start,style_start=struct.unpack_from('<HHI5I',pool)
    if header!=28 or styles or style_start or start!=28+count*4:raise ValueError('Unsupported manifest string pool')
    strings=[]
    def length(offset,utf8):
        value=pool[offset] if utf8 else struct.unpack_from('<H',pool,offset)[0];offset+=1 if utf8 else 2
        mask=0x80 if utf8 else 0x8000
        if value&mask:
            low=pool[offset] if utf8 else struct.unpack_from('<H',pool,offset)[0];offset+=1 if utf8 else 2
            value=((value&~mask)<<(8 if utf8 else 16))|low
        return value,offset
    for i in range(count):
        offset=start+struct.unpack_from('<I',pool,28+i*4)[0]
        chars,offset=length(offset,bool(flags&0x100))
        if flags&0x100:n,offset=length(offset,True);strings.append(pool[offset:offset+n].decode('utf-8'))
        else:strings.append(pool[offset:offset+chars*2].decode('utf-16le'))
    if PERMISSION in strings:raise ValueError('Overlay permission already present')
    value_index=len(strings);body=pool[start:]
    encoded=(bytes([len(PERMISSION),len(PERMISSION)])+PERMISSION.encode()+b'\0') if flags&0x100 else struct.pack('<H',len(PERMISSION))+PERMISSION.encode('utf-16le')+b'\0\0'
    new_body=body+encoded;new_body+=b'\0'*((-len(new_body))%4)
    new_pool=struct.pack('<HHI5I',1,28,start+4+len(new_body),count+1,0,flags,start+4,0)+pool[28:start]+struct.pack('<I',len(body))+new_body
    chunks[pool_index][1]=new_pool
    element=strings.index('uses-permission');uri=strings.index('http://schemas.android.com/apk/res/android');name=strings.index('name')
    tag=struct.pack('<HHI2I2I6H',0x102,16,56,0,0xffffffff,0xffffffff,element,20,20,1,0,0,0)
    tag+=struct.pack('<3IHBBI',uri,name,value_index,8,0,3,value_index)
    tag+=struct.pack('<HHI4I',0x103,16,24,0,0xffffffff,0xffffffff,element)
    application=strings.index('application');insert=next(i for i,(kind,chunk)in enumerate(chunks)if kind==0x102 and struct.unpack_from('<I',chunk,20)[0]==application)
    parts=[chunk for _,chunk in chunks];parts.insert(insert,tag);result=b''.join(parts)
    return struct.pack('<HHI',3,8,len(result)+8)+result
