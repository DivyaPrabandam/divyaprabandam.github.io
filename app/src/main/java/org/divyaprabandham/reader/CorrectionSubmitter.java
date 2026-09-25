package org.divyaprabandham.reader;

import android.content.Context;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Only this worker performs network I/O. Per-entry server rejections are kept for review. */
final class CorrectionSubmitter {
    private static final String ENDPOINT="https://fix.divyaprabandam.workers.dev/submit";
    // App identifier, not a credential. Server-side caps, honeypot and dedupe still apply.
    private static final String APP_ID="dpapp_9fc60bd17dee7b9b4723ca9d5e8d2954";
    private static final int JOB_ID=47921;
    static final class Outcome {int delivered,queued,rejected;String error="";}
    static void schedule(Context context){
        JobScheduler scheduler=context.getSystemService(JobScheduler.class);if(scheduler==null)return;
        JobInfo info=new JobInfo.Builder(JOB_ID,new ComponentName(context,CorrectionRetryJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
            .setBackoffCriteria(30*60*1000L,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build();
        scheduler.schedule(info);
    }
    private static int pendingCount(JSONArray entries)throws Exception{int pending=0;for(int i=0;i<entries.length();i++)if("pending".equals(entries.getJSONObject(i).optString("status")))pending++;return pending;}
    static synchronized Outcome submitPending(Context context){
        Outcome outcome=new Outcome();CorrectionQueue queue=new CorrectionQueue(context);
        try{
            JSONArray entries=queue.read();
            for(int i=0;i<entries.length();i++){
                JSONObject entry=entries.getJSONObject(i);if(!"pending".equals(entry.optString("status")))continue;
                String id=entry.getString("id");Response response=post(entry.getJSONObject("payload"));
                if(response.code==200 && response.accepted){queue.remove(id);outcome.delivered++;}
                else if(response.code==400||response.code==413){
                    String error=response.error.isEmpty()?"Server rejected this report ("+response.code+")":response.error;
                    queue.markRejected(id,error);outcome.rejected++;outcome.error=error;
                }else{outcome.queued++;outcome.error="Service unavailable ("+response.code+")";break;}
            }
            outcome.queued=pendingCount(queue.read());
        }catch(Exception ex){outcome.error="Network unavailable or submission could not finish";
            try{outcome.queued=pendingCount(queue.read());}catch(Exception ignored){} }
        return outcome;
    }
    private static final class Response {int code;String error;boolean accepted;Response(int c,String e,boolean a){code=c;error=e;accepted=a;}}
    private static Response post(JSONObject payload)throws Exception{
        HttpURLConnection connection=(HttpURLConnection)new URL(ENDPOINT).openConnection();
        try{
            connection.setRequestMethod("POST");connection.setConnectTimeout(12000);connection.setReadTimeout(18000);
            connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
            connection.setRequestProperty("x-dp-app",APP_ID);connection.setDoOutput(true);
            try(OutputStream out=connection.getOutputStream()){out.write(payload.toString().getBytes(StandardCharsets.UTF_8));}
            int code=connection.getResponseCode();String body="";
            try(InputStream in=(code>=400?connection.getErrorStream():connection.getInputStream())){
                if(in!=null){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[2048];int n;
                    while((n=in.read(buffer))!=-1&&bytes.size()<8192)bytes.write(buffer,0,n);
                    body=new String(bytes.toByteArray(),StandardCharsets.UTF_8);}
            }
            String error="";if(code==400||code==413){try{error=new JSONObject(body).optString("e","");}catch(Exception ignored){}}
            boolean accepted=false;if(code==200){try{accepted=new JSONObject(body).optBoolean("ok",false);}catch(Exception ignored){}}
            return new Response(code,error.length()>240?error.substring(0,240):error,accepted);
        }finally{connection.disconnect();}
    }
}
