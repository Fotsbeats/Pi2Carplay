import java.io.File;
import java.util.*;
import org.jf.dexlib2.*;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.writer.pool.DexPool;

public final class DexPolicyMerge {
    public static void main(String[] args) throws Exception {
        Opcodes opcodes=Opcodes.forApi(30);
        DexFile original=DexFileFactory.loadDexFile(new File(args[0]),opcodes);
        DexFile patch=DexFileFactory.loadDexFile(new File(args[1]),opcodes);
        if(patch.getClasses().size()!=1)throw new IllegalStateException("Expected one patched class");
        ClassDef replacement=patch.getClasses().iterator().next();
        if(!replacement.getType().equals("Lcom/android/server/wm/DisplayPolicy;"))throw new IllegalStateException("Unexpected class");
        DexPool pool=new DexPool(opcodes);
        int replaced=0;
        for(ClassDef cls:original.getClasses()) {
            if(cls.getType().equals(replacement.getType())){pool.internClass(replacement);replaced++;}
            else pool.internClass(cls);
        }
        if(replaced!=1)throw new IllegalStateException("Original class missing");
        org.jf.dexlib2.writer.io.FileDataStore output=new org.jf.dexlib2.writer.io.FileDataStore(new File(args[2]));
        try{pool.writeTo(output);}finally{output.close();}
        DexFile merged=DexFileFactory.loadDexFile(new File(args[2]),opcodes);
        if(merged.getClasses().size()!=original.getClasses().size())throw new IllegalStateException("Class count changed");
        System.out.println("Preserved "+merged.getClasses().size()+" classes; replaced only DisplayPolicy");
    }
}

