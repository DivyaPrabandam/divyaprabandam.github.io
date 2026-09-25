package org.divyaprabandham.reader;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.InputStream;

/** Skippable, full-song opening using the owner's four images and recording. */
public final class LaunchIntro extends Activity {
    private static final String[] FRAMES={"01.jpg","02.jpg","03.jpg","04.jpg"};
    // First three use arrival order provisionally. The owner explicitly cued image 04 around 6 seconds.
    private static final long[] START_MS={0,1900,3800,5850};
    private final Handler handler=new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private boolean leaving;
    private int shown=-1;
    private final Bitmap[] photos=new Bitmap[FRAMES.length];
    private ImageView front,back;
    private Runnable frameTick;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        FrameLayout stage=new FrameLayout(this);stage.setBackgroundColor(0xff190f09);
        front=new ImageView(this);back=new ImageView(this);
        front.setScaleType(ImageView.ScaleType.FIT_CENTER);back.setScaleType(ImageView.ScaleType.FIT_CENTER);
        stage.addView(front,new FrameLayout.LayoutParams(-1,-1));
        stage.addView(back,new FrameLayout.LayoutParams(-1,-1));back.setAlpha(0f);
        TextView skip=new TextView(this);skip.setText("Skip intro");skip.setTextSize(14);skip.setTextColor(Color.WHITE);
        skip.setGravity(Gravity.CENTER);skip.setContentDescription("Skip opening intro");
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(dp(110),dp(48),Gravity.TOP|Gravity.END);
        sp.topMargin=dp(24);sp.rightMargin=dp(16);stage.addView(skip,sp);
        skip.setOnClickListener(v->enterApp());setContentView(stage);
        try{
            // Decode before playback so image I/O never delays a musical cue.
            for(int i=0;i<FRAMES.length;i++)try(InputStream in=getAssets().open("splash/"+FRAMES[i])){
                BitmapFactory.Options options=new BitmapFactory.Options();options.inPreferredConfig=Bitmap.Config.RGB_565;
                photos[i]=BitmapFactory.decodeStream(in,null,options);
                if(photos[i]==null)throw new IllegalStateException("Image decoding failed");
            }
            showFrame(0);
            player=MediaPlayer.create(this,R.raw.opening_song);
            if(player==null){enterApp();return;}
            player.setOnCompletionListener(mp->enterApp());
            player.setOnErrorListener((mp,what,extra)->{enterApp();return true;});
            player.start();
            frameTick=new Runnable(){public void run(){
                if(!leaving&&player!=null){
                    long position=player.getCurrentPosition();int index=0;
                    for(int i=1;i<START_MS.length;i++)if(position>=START_MS[i])index=i;
                    if(index!=shown)showFrame(index);
                    handler.postDelayed(this,20);
                }
            }};
            handler.post(frameTick);
            int duration=player.getDuration();
            // Completion normally controls exit; safety only handles a stalled decoder.
            handler.postDelayed(this::enterApp,duration>0?duration+1200:10000);
        }catch(Exception ex){android.util.Log.w("LaunchIntro","Opening failed",ex);enterApp();}
    }
    private void showFrame(int index){
        if(shown==index)return;
        if(shown<0){front.setImageBitmap(photos[index]);shown=index;return;}
        back.animate().cancel();front.animate().cancel();
        back.setImageBitmap(photos[index]);back.setAlpha(0f);
        back.animate().alpha(1f).setDuration(140).withEndAction(()->{
            ImageView old=front;front=back;back=old;
            back.setAlpha(0f);back.setImageDrawable(null);
        }).start();
        shown=index;
    }
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private void enterApp(){if(leaving)return;leaving=true;handler.removeCallbacksAndMessages(null);
        if(player!=null){player.release();player=null;}
        startActivity(new Intent(this,MainActivity.class));finish();
    }
    @Override public void onBackPressed(){enterApp();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);
        if(player!=null){player.release();player=null;}
        front.animate().cancel();back.animate().cancel();front.setImageDrawable(null);back.setImageDrawable(null);
        for(Bitmap photo:photos)if(photo!=null)photo.recycle();super.onDestroy();
    }
}
