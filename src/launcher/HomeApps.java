package org.bench.boxhome;
import java.util.*;

final class HomeApps {
    static final int SLOTS=6;
    static String[] load(String saved,String[] defaults){
        if(saved==null)return defaults.clone();
        String[] parts=saved.split("\\|",-1);
        if(parts.length!=SLOTS)return defaults.clone();
        HashSet<String> seen=new HashSet<>();
        for(int i=0;i<parts.length;i++){
            if(parts[i].isEmpty())continue;
            if(!parts[i].matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")||!seen.add(parts[i])||parts[i].equals("com.android.vending"))parts[i]="";
        }
        return parts;
    }
    static String save(String[] slots){if(slots.length!=SLOTS)throw new IllegalArgumentException("Six home slots required");return String.join("|",slots);}
    static void set(String[] slots,int slot,String pkg){
        if(slot<0||slot>=SLOTS)throw new IllegalArgumentException("slot");
        if(pkg.equals("com.android.vending"))throw new IllegalArgumentException("Play Store belongs on Apps");
        int previous=Arrays.asList(slots).indexOf(pkg);
        if(!pkg.isEmpty()&&previous>=0&&previous!=slot)slots[previous]=slots[slot];
        slots[slot]=pkg;
    }
}
