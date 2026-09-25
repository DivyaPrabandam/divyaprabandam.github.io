package org.divyaprabandham.reader;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Device-private durable queue. Submissions are removed only after a confirmed 200 response. */
final class CorrectionQueue {
    private static final String FILE="corrections-queue.json";
    private static final int MAX_ENTRIES=12;
    private final AtomicFile file;
    CorrectionQueue(Context context){file=new AtomicFile(new File(context.getApplicationContext().getFilesDir(),FILE));}
    synchronized JSONArray read() throws Exception {
        if(!file.getBaseFile().exists())return new JSONArray();
        try(FileInputStream input=file.openRead()){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
            while((n=input.read(buffer))!=-1)bytes.write(buffer,0,n);
            return new JSONArray(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
        }
    }
    synchronized String append(JSONObject payload) throws Exception {
        JSONArray queue=read();
        if(queue.length()>=MAX_ENTRIES)throw new IllegalStateException("The corrections queue is full. Please retry pending reports before adding another.");
        String id=UUID.randomUUID().toString();
        JSONObject entry=new JSONObject();entry.put("id",id);entry.put("payload",payload);entry.put("status","pending");
        entry.put("savedAt",System.currentTimeMillis());queue.put(entry);write(queue);return id;
    }
    private void write(JSONArray queue)throws Exception {
        FileOutputStream out=file.startWrite();
        try{out.write(queue.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}
        catch(Exception ex){file.failWrite(out);throw ex;}
    }
    synchronized int count() throws Exception{return read().length();}
    synchronized void remove(String id)throws Exception {
        JSONArray before=read(),after=new JSONArray();
        for(int i=0;i<before.length();i++){JSONObject entry=before.getJSONObject(i);if(!id.equals(entry.optString("id")))after.put(entry);}
        write(after);
    }
    synchronized void markRejected(String id,String error)throws Exception {
        JSONArray entries=read();
        for(int i=0;i<entries.length();i++){JSONObject entry=entries.getJSONObject(i);
            if(id.equals(entry.optString("id"))){entry.put("status","needs-review");entry.put("error",error);break;}}
        write(entries);
    }
}
