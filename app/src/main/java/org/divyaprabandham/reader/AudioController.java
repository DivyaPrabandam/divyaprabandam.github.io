package org.divyaprabandham.reader;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One playback owner for the reader. Audio never advances read progress automatically. */
final class AudioController {
    interface Listener {void changed();}
    private final Context context;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService downloads=Executors.newSingleThreadExecutor();
    private MediaPlayer player;
    private ArrayList<AudioCatalog.Track> queue=new ArrayList<>();
    private int current=0,generation=0;
    private boolean prepared=false,playing=false,repeatOne=false,repeatGroup=false,stopped=false;
    private boolean resumeAfterFocus=false;
    private boolean pausedByUser=false;
    private android.media.AudioManager focusManager;
    private final android.media.AudioManager.OnAudioFocusChangeListener focusChange=change->{
        if(player==null||!prepared)return;
        if(change==android.media.AudioManager.AUDIOFOCUS_LOSS||change==android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT){
            try{resumeAfterFocus=player.isPlaying();player.pause();playing=false;changed();}catch(Exception ignored){}
        }else if(change==android.media.AudioManager.AUDIOFOCUS_GAIN&&resumeAfterFocus){
            resumeAfterFocus=false;try{player.start();playing=true;changed();}catch(Exception ignored){}
        }
    };
    private long a=-1,b=-1;
    private float speed=1f;
    private String error="";
    private Listener listener;
    private int lastRenderedSecond=-1;
    AudioController(Context context){this.context=context.getApplicationContext();
        focusManager=this.context.getSystemService(android.media.AudioManager.class);
    }
    void listen(Listener listener){this.listener=listener;changed();}
    private void changed(){if(listener!=null)listener.changed();}
    AudioCatalog.Track track(){return current>=0&&current<queue.size()?queue.get(current):null;}
    boolean prepared(){return prepared;}boolean playing(){return playing;}float speed(){return speed;}
    boolean repeatOne(){return repeatOne;}boolean repeatGroup(){return repeatGroup;}
    long markA(){return a;}long markB(){return b;}
    String error(){return error;}
    int position(){try{return prepared&&player!=null?player.getCurrentPosition():0;}catch(Exception ignored){return 0;}}
    int duration(){try{return prepared&&player!=null?player.getDuration():0;}catch(Exception ignored){return 0;}}
    int queueSize(){return queue.size();}int queueIndex(){return current;}
    boolean preparing(){return player!=null&&!prepared;}
    boolean cached(){AudioCatalog.Track t=track();return t!=null&&cachedFile(t).isFile();}
    private File cachedFile(AudioCatalog.Track t){String filename=t.url.substring(t.url.lastIndexOf('/')+1);
        return new File(new File(context.getFilesDir(),"audio-cache"),filename);}
    void play(ArrayList<AudioCatalog.Track> tracks,int index){
        if(tracks==null||tracks.isEmpty())return;
        queue=new ArrayList<>(tracks);current=Math.max(0,Math.min(index,queue.size()-1));repeatOne=false;repeatGroup=false;startCurrent();
    }
    private void releasePlayer(){if(player!=null){try{player.release();}catch(Exception ignored){}player=null;}}
    private void startCurrent(){
        generation++;int token=generation;releasePlayer();prepared=false;playing=false;pausedByUser=false;lastRenderedSecond=-1;a=-1;b=-1;error="";changed();
        AudioCatalog.Track t=track();if(t==null)return;
        try{
            player=new MediaPlayer();
            if(focusManager!=null)focusManager.requestAudioFocus(focusChange,android.media.AudioManager.STREAM_MUSIC,android.media.AudioManager.AUDIOFOCUS_GAIN);
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            File local=cachedFile(t);if(local.isFile())player.setDataSource(local.getAbsolutePath());else player.setDataSource(t.url);
            player.setOnPreparedListener(p->{if(stopped||token!=generation)return;
                prepared=true;try{
                    p.setPlaybackParams(new PlaybackParams().setSpeed(speed));
                    if(pausedByUser){p.pause();playing=false;}else{p.start();playing=true;}
                }catch(Exception ex){error="Could not play this recording.";}
                changed();});
            player.setOnCompletionListener(p->{if(stopped||token!=generation)return;
                playing=false;if(a>=0&&b>a){try{p.seekTo((int)a);p.start();playing=true;}catch(Exception ex){error="A–B replay failed.";}changed();return;}
                if(repeatOne){try{p.seekTo(0);p.start();playing=true;}catch(Exception ex){error="Replay failed.";}changed();return;}
                if(current+1<queue.size()){current++;startCurrent();return;}
                if(repeatGroup){current=0;startCurrent();return;}changed();});
            player.setOnErrorListener((p,what,extra)->{if(token==generation){playing=false;prepared=false;error="Recording unavailable. Check your connection or download it for offline use.";changed();}return true;});
            player.prepareAsync();
        }catch(Exception ex){releasePlayer();error="Recording unavailable. Check your connection or download it for offline use.";changed();}
    }
    void toggle(){if(player==null)return;
        if(!prepared){pausedByUser=!pausedByUser;changed();return;}
        try{if(player.isPlaying()){player.pause();playing=false;pausedByUser=true;}else{player.start();playing=true;pausedByUser=false;}changed();}
        catch(Exception ex){error="Playback failed.";changed();}}
    void next(){if(current+1<queue.size()){current++;startCurrent();}}
    void previous(){if(current>0){current--;startCurrent();}else seek(0);}
    void seek(long millis){if(!prepared||player==null)return;try{player.seekTo((int)Math.max(0,Math.min(millis,duration())));changed();}catch(Exception ignored){}}
    void speed(float value){speed=Math.max(.5f,Math.min(2f,Math.round(value*100f)/100f));
        if(prepared&&player!=null)try{boolean was=player.isPlaying();player.setPlaybackParams(new PlaybackParams().setSpeed(speed));if(!was)player.pause();}catch(Exception ex){error="Speed adjustment unavailable on this device.";}
        changed();}
    void one(){repeatOne=!repeatOne;if(repeatOne){repeatGroup=false;a=-1;b=-1;}changed();}
    void group(){repeatGroup=!repeatGroup;if(repeatGroup){repeatOne=false;a=-1;b=-1;}changed();}
    void repeatGroupOn(){repeatGroup=true;repeatOne=false;a=-1;b=-1;changed();}
    void setA(){if(prepared){a=position();b=-1;changed();}}
    void setB(){if(prepared&&a>=0&&position()>a+200){b=position();repeatOne=false;repeatGroup=false;changed();}}
    void clearAB(){a=-1;b=-1;changed();}
    void download(){AudioCatalog.Track t=track();if(t==null||!AudioCatalog.valid(t.url))return;
        if(cachedFile(t).isFile()){error="Already saved for offline listening.";changed();return;}
        error="Downloading this recording…";changed();
        downloads.execute(()->{
            String failure=null;File target=cachedFile(t),part=new File(target.getParentFile(),target.getName()+".part");
            HttpURLConnection conn=null;
            try{
                if(!target.getParentFile().exists()&&!target.getParentFile().mkdirs())throw new Exception("Storage unavailable");
                conn=(HttpURLConnection)new URL(t.url).openConnection();conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(10000);conn.setReadTimeout(20000);
                if(conn.getResponseCode()!=200||(conn.getContentType()==null||!(conn.getContentType().toLowerCase(java.util.Locale.ROOT).startsWith("audio/mpeg")||conn.getContentType().toLowerCase(java.util.Locale.ROOT).startsWith("audio/mp4")||conn.getContentType().toLowerCase(java.util.Locale.ROOT).startsWith("audio/x-m4a"))))throw new Exception("Audio source unavailable");
                int expected=conn.getContentLength();if(expected<=0||expected>120_000_000)throw new Exception("Recording too large to save");
                long size=0;
                try(java.io.InputStream in=conn.getInputStream();FileOutputStream out=new FileOutputStream(part)){
                    byte[] buffer=new byte[16384];int n;while((n=in.read(buffer))!=-1){size+=n;if(size>120_000_000)throw new Exception("Recording too large");out.write(buffer,0,n);}out.getFD().sync();
                }
                if(size!=expected||!part.renameTo(target))throw new Exception("Incomplete download");
            }catch(Exception ex){failure=ex.getMessage()==null?"Download failed":ex.getMessage();part.delete();}
            finally{if(conn!=null)conn.disconnect();}
            final String result=failure;handler.post(()->{error=result==null?"Saved for offline listening.":"Download failed: "+result;changed();});
        });
    }
    void tick(){if(stopped)return;
        if(prepared&&player!=null){if(a>=0&&b>a&&position()>=b)seek(a);
            int second=position()/1000;if(second!=lastRenderedSecond){lastRenderedSecond=second;changed();}}
        handler.postDelayed(this::tick,100);
    }
    void close(){stopped=true;generation++;handler.removeCallbacksAndMessages(null);downloads.shutdownNow();releasePlayer();
        if(focusManager!=null)focusManager.abandonAudioFocus(focusChange);listener=null;}
}
