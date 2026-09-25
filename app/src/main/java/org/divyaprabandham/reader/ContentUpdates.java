package org.divyaprabandham.reader;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.util.AtomicFile;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Verified content snapshots. Network activation requires a publisher URL and public key. */
final class ContentUpdates {
    static final int SCHEMA=1;
    static final int MAX_MANIFEST=65536,MAX_BOOK=2_000_000,MAX_LIBRARY=16_000_000;
    private static final long EIGHT_HOURS_MS=8L*60*60*1000;
    private static final String WORK="verified-content-every-8h";
    private static final int MAX_IMAGES_CACHED=50,MAX_IMAGE_CACHE_BYTES=24_000_000;
    private static final int MAX_IMAGES_PER_WIFI_PASS=8,MAX_IMAGES_PER_CELL_PASS=2;
    private static final int MAX_IMAGE_BYTES_PER_WIFI_PASS=6_000_000,MAX_IMAGE_BYTES_PER_CELL_PASS=600_000;
    // Production publisher URL/key are intentionally unset until independently verified.
    private static final String MANIFEST_URL="",PUBLIC_KEY_X509_BASE64="";
    static boolean configured(){return !MANIFEST_URL.isEmpty()&&!PUBLIC_KEY_X509_BASE64.isEmpty();}
    private final Context context;
    private final File snapshots;
    ContentUpdates(Context context){this.context=context.getApplicationContext();snapshots=new File(this.context.getFilesDir(),"verified-content");}
    static void schedule(Context context){
        if(!configured())return;
        Constraints constraints=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest request=new PeriodicWorkRequest.Builder(ContentUpdateWorker.class,8,TimeUnit.HOURS)
            .setConstraints(constraints).build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK,ExistingPeriodicWorkPolicy.KEEP,request);
    }
    boolean checkDue(){return configured()&&System.currentTimeMillis()-context.getSharedPreferences("content-settings",0).getLong("last-good-check",0)>=EIGHT_HOURS_MS;}
    boolean isWifi(){ConnectivityManager manager=context.getSystemService(ConnectivityManager.class);
        if(manager==null)return false;android.net.Network network=manager.getActiveNetwork();
        NetworkCapabilities caps=network==null?null:manager.getNetworkCapabilities(network);
        return caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }
    boolean imageDownloadAllowed(){SharedPreferences p=context.getSharedPreferences("content-settings",Context.MODE_PRIVATE);
        String mode=p.getString("image-network","auto");return !"off".equals(mode)&&(!"wifi".equals(mode)||isWifi());}
    String imageVariant(){SharedPreferences p=context.getSharedPreferences("content-settings",Context.MODE_PRIVATE);
        String mode=p.getString("image-network","auto");return "light".equals(mode)?"light":isWifi()?"full":"light";}
    String readBook(String name)throws Exception{
        File active=activeDirectory();if(active==null||!name.matches("[a-zA-Z0-9._-]+\\.json"))return null;
        File file=new File(active,name);if(!file.isFile())return null;
        return new String(readBounded(file,MAX_BOOK),StandardCharsets.UTF_8);
    }
    private File activeDirectory()throws Exception{
        if(!configured())return null; // Never load an old synthetic QA snapshot into a production candidate.
        AtomicFile pointer=new AtomicFile(new File(snapshots,"active"));if(!pointer.getBaseFile().exists())return null;
        String id=new String(readBounded(pointer.openRead(),128),StandardCharsets.UTF_8).trim();
        if(!id.matches("[a-f0-9]{64}"))return null;
        File active=new File(snapshots,id);
        return new File(active,"complete").isFile()?active:null;
    }
    JSONArray activeBooks()throws Exception{
        JSONArray bundled=new JSONArray(new String(readBounded(context.getAssets().open("books/manifest.json"),MAX_MANIFEST),StandardCharsets.UTF_8));
        JSONObject active=metadata();if(active==null)return bundled;
        JSONArray descriptors=active.getJSONArray("books"),merged=new JSONArray();
        if(descriptors.length()!=bundled.length())throw new IllegalArgumentException("Book index count changed");
        for(int i=0;i<bundled.length();i++){
            JSONObject base=bundled.getJSONObject(i),descriptor=descriptors.getJSONObject(i);
            if(!base.getString("id").equals(descriptor.getString("id")))throw new IllegalArgumentException("Book order or ID changed");
            JSONObject meta=new JSONObject(base.toString());
            meta.put("file",descriptor.getString("file"));
            // Download descriptors contain hashes and filenames, not reader-facing labels.
            merged.put(meta);
        }
        return merged;
    }
    int activeVersion()throws Exception{JSONObject m=metadata();return m==null?0:m.getInt("contentVersion");}
    File imageFile(String imageId){if(!imageId.matches("[a-zA-Z0-9_-]{1,80}"))return null;
        File f=new File(new File(snapshots,"images"),imageId+".jpg");return f.isFile()?f:null;}
    JSONObject metadata()throws Exception{
        File active=activeDirectory();if(active==null)return null;
        return new JSONObject(new String(readBounded(new File(active,"metadata.json"),MAX_MANIFEST),StandardCharsets.UTF_8));
    }
    private static byte[] readBounded(File f,int limit)throws Exception{try(InputStream in=new FileInputStream(f)){return readBounded(in,limit);}}
    static byte[] readBounded(InputStream in,int limit)throws Exception{
        try(InputStream input=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[8192];int n;while((n=input.read(buf))!=-1){if(out.size()+n>limit)throw new IllegalArgumentException("Resource too large");out.write(buf,0,n);}return out.toByteArray();
        }
    }
    static String sha256(byte[] bytes)throws Exception{byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder hex=new StringBuilder();for(byte b:digest)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return hex.toString();}
    static void verifySignature(byte[] payload,byte[] signature,String publicKeyBase64)throws Exception{
        byte[] keyBytes=android.util.Base64.decode(publicKeyBase64,android.util.Base64.DEFAULT);
        PublicKey key=KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(keyBytes));
        Signature verifier=Signature.getInstance("SHA256withECDSA");verifier.initVerify(key);verifier.update(payload);
        if(!verifier.verify(signature))throw new SecurityException("Content manifest signature failed");
    }
    static JSONObject validateManifest(JSONObject manifest)throws Exception{
        if(manifest.getInt("schemaVersion")!=SCHEMA)throw new IllegalArgumentException("Unsupported manifest schema");
        if(manifest.getInt("minAppSchema")<1||manifest.getInt("maxAppSchema")<manifest.getInt("minAppSchema"))throw new IllegalArgumentException("Invalid app schema range");
        int version=manifest.getInt("contentVersion");if(version<1)throw new IllegalArgumentException("Missing content version");
        JSONArray books=manifest.getJSONArray("books");if(books.length()!=25)throw new IllegalArgumentException("Expected 25 books");
        JSONArray images=manifest.optJSONArray("images");if(images!=null)validateImages(images);
        Set<String> ids=new HashSet<>(),names=new HashSet<>();int total=0;
        for(int i=0;i<books.length();i++){
            JSONObject b=books.getJSONObject(i);String id=b.getString("id"),name=b.getString("file"),hash=b.getString("sha256");int size=b.getInt("bytes");
            if(!id.matches("[0-9]{2}-[a-z0-9-]+")||!name.matches("[a-zA-Z0-9._-]+\\.json")||!hash.matches("[a-f0-9]{64}")||size<1||size>MAX_BOOK||!ids.add(id)||!names.add(name))throw new IllegalArgumentException("Invalid book descriptor");
            total+=size;if(total>MAX_LIBRARY)throw new IllegalArgumentException("Library exceeds storage budget");
            String url=b.getString("url");if(!url.startsWith("https://"))throw new IllegalArgumentException("Book URL must be HTTPS");
        }
        return manifest;
    }
    static void validateImages(JSONArray images)throws Exception{
        if(images.length()>2000)throw new IllegalArgumentException("Too many images");
        Set<String> ids=new HashSet<>();
        for(int i=0;i<images.length();i++){
            JSONObject image=images.getJSONObject(i);String id=image.getString("id");
            if(!id.matches("[a-zA-Z0-9_-]{1,80}")||!ids.add(id))throw new IllegalArgumentException("Invalid image ID");
            if(image.optString("credit","").trim().isEmpty()||image.optString("license","").trim().isEmpty())throw new IllegalArgumentException("Image source credit missing");
            for(String variant:new String[]{"full","light"}){
                JSONObject item=image.getJSONObject(variant);if(!item.getString("sha256").matches("[a-f0-9]{64}")||
                    item.getInt("bytes")<1||item.getInt("bytes")>1_500_000||!item.getString("url").startsWith("https://"))
                    throw new IllegalArgumentException("Invalid image variant");
            }
        }
    }
    static void validateLibrary(JSONArray descriptors,File dir)throws Exception{
        boolean[] seen=new boolean[4001];
        for(int i=0;i<descriptors.length();i++){
            JSONObject b=descriptors.getJSONObject(i);byte[] raw=readBounded(new File(dir,b.getString("file")),MAX_BOOK);
            if(raw.length!=b.getInt("bytes")||!sha256(raw).equals(b.getString("sha256")))throw new SecurityException("Book checksum mismatch");
            JSONObject book=new JSONObject(new String(raw,StandardCharsets.UTF_8));if(!book.getString("id").equals(b.getString("id")))throw new IllegalArgumentException("Book identifier changed");
            JSONArray sections=book.getJSONArray("sections");
            for(int k=0;k<sections.length();k++){JSONArray rows=sections.getJSONObject(k).getJSONArray("p");
                for(int r=0;r<rows.length();r++){JSONArray row=rows.getJSONArray(r);int first=row.getInt(0),last=row.getInt(1);
                    if(first<1||last>4000||last<first||row.getJSONArray(2).length()==0||row.getJSONArray(3).length()==0)throw new IllegalArgumentException("Invalid pasuram");
                    for(int n=first;n<=last;n++){if(seen[n])throw new IllegalArgumentException("Duplicate pasuram "+n);seen[n]=true;}
                }}
        }
        for(int n=1;n<=4000;n++)if(!seen[n])throw new IllegalArgumentException("Missing pasuram "+n);
    }
    String check()throws Exception{
        if(!configured())return "unconfigured";
        if(!checkDue())return "not-due";
        HttpURLConnection conn=(HttpURLConnection)new URL(MANIFEST_URL).openConnection();conn.setInstanceFollowRedirects(false);conn.setConnectTimeout(10000);conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent","DivyaPrabandham-Android-QA/0.3");
        try{
            int status=conn.getResponseCode();if(status!=200)throw new IllegalStateException("Manifest HTTP "+status);
            byte[] payload=readBounded(conn.getInputStream(),MAX_MANIFEST);String header=conn.getHeaderField("X-Content-Signature");
            if(header==null)throw new SecurityException("Unsigned manifest");
            verifySignature(payload,android.util.Base64.decode(header,android.util.Base64.DEFAULT),PUBLIC_KEY_X509_BASE64);
            String result=activate(validateManifest(new JSONObject(new String(payload,StandardCharsets.UTF_8))),payload);
            context.getSharedPreferences("content-settings",0).edit().putLong("last-good-check",System.currentTimeMillis()).putString("last-result",result).remove("last-error").apply();
            if(("updated".equals(result)||"current".equals(result))&&imageDownloadAllowed())syncImages(manifestForImages());
            return result;
        }finally{conn.disconnect();}
    }
    private JSONObject manifestForImages()throws Exception{return metadata();}
    private void syncImages(JSONObject manifest){if(manifest==null)return;
        JSONArray images=manifest.optJSONArray("images");if(images==null||!imageDownloadAllowed())return;
        File cache=new File(snapshots,"images");if(!cache.exists()&&!cache.mkdirs())return;
        String variant=imageVariant();int scanned=0,usedBytes=0;
        final boolean wifi=isWifi();final int maxCount=wifi?MAX_IMAGES_PER_WIFI_PASS:MAX_IMAGES_PER_CELL_PASS;
        final int maxBytes=wifi?MAX_IMAGE_BYTES_PER_WIFI_PASS:MAX_IMAGE_BYTES_PER_CELL_PASS;
        for(int i=0;i<images.length();i++){
            try{JSONObject image=images.getJSONObject(i),v=image.getJSONObject(variant);String id=image.getString("id");
                if(!id.matches("[a-zA-Z0-9_-]{1,80}"))continue;
                File target=new File(cache,id+".jpg");String hash=v.getString("sha256");
                if(target.isFile()&&sha256(readBounded(target,1_500_000)).equals(hash))continue;
                int size=v.getInt("bytes");if(scanned>=maxCount||usedBytes+size>maxBytes)break;
                scanned++;usedBytes+=size;byte[] data=download(v.getString("url"),size);
                if(data.length!=size||!sha256(data).equals(hash))continue;
                android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;
                android.graphics.BitmapFactory.decodeByteArray(data,0,data.length,bounds);
                if(bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>4000||bounds.outHeight>4000)continue;
                evictImages(cache,target,size);File tmp=new File(cache,id+".tmp");writeFile(tmp,data);if(!tmp.renameTo(target))tmp.delete();
            }catch(Exception ex){android.util.Log.w("ContentUpdates","Image deferred",ex);}
        }
    }
    private static void evictImages(File cache,File target,int incoming){
        File[] files=cache.listFiles((dir,name)->name.endsWith(".jpg"));if(files==null)return;
        java.util.Arrays.sort(files,(a,b)->Long.compare(a.lastModified(),b.lastModified()));
        long total=0;int count=0;for(File f:files){total+=f.length();count++;}
        for(File old:files){if(total+incoming<=MAX_IMAGE_CACHE_BYTES&&count<MAX_IMAGES_CACHED)break;
            if(old.equals(target))continue;long bytes=old.length();if(old.delete()){total-=bytes;count--;}}
    }
    private String activate(JSONObject manifest,byte[] rawManifest)throws Exception{
        int min=manifest.getInt("minAppSchema"),max=manifest.getInt("maxAppSchema");
        if(SCHEMA<min||SCHEMA>max){JSONObject apk=manifest.getJSONObject("appUpdate");
            if(!apk.getString("url").startsWith("https://")||!apk.getString("sha256").matches("[a-f0-9]{64}")||
                apk.getInt("versionCode")<=context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionCode||
                apk.getInt("bytes")<1||apk.getInt("bytes")>120_000_000)throw new IllegalArgumentException("Invalid app update metadata");
            context.getSharedPreferences("content-settings",0).edit().putString("update-required",apk.toString()).apply();return "app-update-required";}
        context.getSharedPreferences("content-settings",0).edit().remove("update-required").apply();
        String id=sha256(rawManifest);File current=activeDirectory();if(current!=null){
            JSONObject old=metadata();if(old!=null&&manifest.getInt("contentVersion")<old.getInt("contentVersion"))throw new SecurityException("Content version rolled back");
            if(current.getName().equals(id))return "current";
            if(old!=null&&manifest.getInt("contentVersion")==old.getInt("contentVersion"))throw new SecurityException("Content version reused");
        }
        if(!snapshots.exists()&&!snapshots.mkdirs())throw new IllegalStateException("Storage unavailable");
        File stage=new File(snapshots,id+".staging"),complete=new File(snapshots,id);
        if(!stage.exists()&&!stage.mkdirs())throw new IllegalStateException("Cannot stage update");
        JSONArray descriptors=manifest.getJSONArray("books");
        // Reject an incompatible book order before touching active storage. The current UI
        // keeps reader-facing labels/ranges from the bundled index keyed by these IDs.
        JSONArray originalIndex=new JSONArray(new String(readBounded(context.getAssets().open("books/manifest.json"),MAX_MANIFEST),StandardCharsets.UTF_8));
        if(originalIndex.length()!=descriptors.length())throw new IllegalArgumentException("Book count changed");
        for(int i=0;i<descriptors.length();i++)if(!originalIndex.getJSONObject(i).getString("id").equals(descriptors.getJSONObject(i).getString("id")))
            throw new IllegalArgumentException("Book order changed");
        try{
            for(int i=0;i<descriptors.length();i++){
                JSONObject b=descriptors.getJSONObject(i);String name=b.getString("file");File destination=new File(stage,name);
                if(destination.isFile()&&sha256(readBounded(destination,MAX_BOOK)).equals(b.getString("sha256")))continue;
                byte[] data=null;File previous=current==null?null:new File(current,name);
                if(previous!=null&&previous.isFile()){byte[] old=readBounded(previous,MAX_BOOK);if(old.length==b.getInt("bytes")&&sha256(old).equals(b.getString("sha256")))data=old;}
                if(data==null){String originalName=bundledName(b.getString("id"));
                    if(originalName!=null)try(InputStream bundled=context.getAssets().open("books/"+originalName)){byte[] original=readBounded(bundled,MAX_BOOK);
                    if(original.length==b.getInt("bytes")&&sha256(original).equals(b.getString("sha256")))data=original;
                }catch(java.io.FileNotFoundException ignored){}}
                if(data==null)data=download(b.getString("url"),b.getInt("bytes"));
                if(data.length!=b.getInt("bytes")||!sha256(data).equals(b.getString("sha256")))throw new SecurityException("Download checksum mismatch");
                writeFile(destination,data);
            }
            validateLibrary(descriptors,stage);
            writeFile(new File(stage,"metadata.json"),rawManifest);writeFile(new File(stage,"complete"),new byte[]{1});
            if(complete.exists())throw new IllegalStateException("Snapshot collision");
            if(!stage.renameTo(complete))throw new IllegalStateException("Snapshot rename failed");
            AtomicFile pointer=new AtomicFile(new File(snapshots,"active"));FileOutputStream out=pointer.startWrite();
            try{out.write(id.getBytes(StandardCharsets.UTF_8));pointer.finishWrite(out);}catch(Exception e){pointer.failWrite(out);throw e;}
            // Keep current and previous only. Never remove the bundled APK fallback.
            File[] dirs=snapshots.listFiles();if(dirs!=null)for(File candidate:dirs){if(candidate.isDirectory()&&!candidate.equals(complete)&&!candidate.equals(current))deleteTree(candidate);}
            return "updated";
        }catch(Exception ex){deleteTree(stage);throw ex;}
    }
    private String bundledName(String id)throws Exception{
        JSONArray original=new JSONArray(new String(readBounded(context.getAssets().open("books/manifest.json"),MAX_MANIFEST),StandardCharsets.UTF_8));
        for(int i=0;i<original.length();i++)if(id.equals(original.getJSONObject(i).getString("id")))return original.getJSONObject(i).getString("file");
        return null;
    }
    private static void writeFile(File file,byte[] bytes)throws Exception{try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);out.getFD().sync();}}
    private static void deleteTree(File file){if(file.isDirectory()){File[] children=file.listFiles();if(children!=null)for(File child:children)deleteTree(child);}file.delete();}
    private static byte[] download(String url,int expected)throws Exception{
        if(!url.startsWith("https://"))throw new SecurityException("Insecure resource URL");
        HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();conn.setInstanceFollowRedirects(false);conn.setConnectTimeout(10000);conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent","DivyaPrabandham-Android-QA/0.3");
        try{if(conn.getResponseCode()!=200)throw new IllegalStateException("Resource HTTP "+conn.getResponseCode());return readBounded(conn.getInputStream(),Math.min(MAX_BOOK,expected));}
        finally{conn.disconnect();}
    }
}
