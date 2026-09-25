package org.divyaprabandham.reader;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Private per-pasuram drafts. The UI never sends these while corrections are gated. */
final class CorrectionDrafts {
    private static final int MAX_DRAFT=2_500_000;
    private final File directory;
    CorrectionDrafts(Context context){directory=new File(context.getApplicationContext().getFilesDir(),"correction-drafts");}
    private AtomicFile file(int n){if(n<1||n>4000)throw new IllegalArgumentException("Invalid pasuram");return new AtomicFile(new File(directory,n+".json"));}
    JSONObject read(int n)throws Exception{
        AtomicFile f=file(n);if(!f.getBaseFile().isFile())return new JSONObject();
        try(FileInputStream in=f.openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int count;while((count=in.read(b))!=-1){if(out.size()+count>MAX_DRAFT)throw new IllegalStateException("Draft too large");out.write(b,0,count);}
            return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
        }
    }
    void save(int n,JSONObject draft)throws Exception{
        byte[] data=draft.toString().getBytes(StandardCharsets.UTF_8);if(data.length>MAX_DRAFT)throw new IllegalStateException("Draft too large");
        if(!directory.exists()&&!directory.mkdirs())throw new IllegalStateException("Draft storage unavailable");
        AtomicFile f=file(n);FileOutputStream out=f.startWrite();try{out.write(data);f.finishWrite(out);}catch(Exception e){f.failWrite(out);throw e;}
    }
    boolean has(int n){return file(n).getBaseFile().isFile();}
    void delete(int n){file(n).delete();}
}
