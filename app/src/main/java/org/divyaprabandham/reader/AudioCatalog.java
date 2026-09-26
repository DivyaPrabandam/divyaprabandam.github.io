package org.divyaprabandham.reader;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;

/** Exact audio links from the published Divya Prabandham site, never inferred filenames. */
final class AudioCatalog {
    private final JSONObject verses, groups;
    static final class Track {
        final int verse;final String title,url;
        Track(int verse,String title,String url){this.verse=verse;this.title=title;this.url=url;}
    }
    AudioCatalog(Context context)throws Exception{
        verses=new JSONObject(asset(context,"audio/verses.json"));
        groups=new JSONObject(asset(context,"audio/groups.json"));
    }
    private static String asset(Context context,String path)throws Exception{
        try(InputStream in=context.getAssets().open(path);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
    static boolean valid(String url){return url.matches("https://media\\.divyaprabandam\\.workers\\.dev/media/[a-f0-9]{12}\\.(mp3|m4a)");}
    Track verse(int number){String url=verses.optString(String.valueOf(number),"");
        return valid(url)?new Track(number,"Pasuram "+number,url):null;}
    int[] groupRange(String bookId,int number,int max){
        int start=-1,end=max+1;
        java.util.Iterator<String> keys=groups.keys();
        while(keys.hasNext()){
            String key=keys.next();if(!key.startsWith(bookId+":"))continue;
            try{int first=Integer.parseInt(key.substring(key.indexOf(':')+1));
                if(first<=number&&first>start)start=first;
                if(first>number&&first<end)end=first;
            }catch(NumberFormatException ignored){}
        }
        // The madals have only one full recording at their first number, not a clip per verse.
        if(bookId.equals("22-siriya-thirumadal")||bookId.equals("23-periya-thirumadal"))
            return new int[]{start<0?number:start,max};
        return start<0?new int[]{number,number}:new int[]{start,Math.min(max,end-1)};
    }
    ArrayList<Track> recordings(String bookId,int first){
        ArrayList<Track> tracks=new ArrayList<>();JSONArray array=groups.optJSONArray(bookId+":"+first);
        if(array==null)return tracks;
        for(int i=0;i<array.length();i++){JSONArray row=array.optJSONArray(i);if(row==null)continue;
            String url=row.optString(1,"");if(valid(url))tracks.add(new Track(first,row.optString(0,"Group recording"),url));}
        return tracks;
    }
}
