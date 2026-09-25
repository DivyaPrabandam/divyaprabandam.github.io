package org.divyaprabandham.reader;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.InputStream;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** Short, skippable opening slot. The owner will supply the full photo and audio. */
public final class LaunchIntro extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private boolean opening;
    // The owner is supplying an audio clip and a lyric-to-image cue sheet.
    // Do not assign image order or song timing until both are available.
    private int pendingAudio=0;
    private String[] frameFiles=null;
    private long[] frameStartsMs=null;
    private int shownFrame=-1;
    private ImageView image;
    private Bitmap visible;
    private Runnable frameTick;
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        FrameLayout stage=new FrameLayout(this);stage.setBackgroundColor(0xff190f09);
        image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        stage.addView(image,new FrameLayout.LayoutParams(-1,-1));
        TextView skip=new TextView(this);skip.setText("Skip intro");skip.setTextSize(14);skip.setTextColor(Color.WHITE);
        skip.setGravity(Gravity.CENTER);skip.setContentDescription("Skip opening intro");
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(dp(110),dp(48),Gravity.TOP|Gravity.END);
        sp.topMargin=dp(24);sp.rightMargin=dp(16);stage.addView(skip,sp);
        stage.setOnClickListener(v->enterApp());skip.setOnClickListener(v->enterApp());
        setContentView(stage);
        // Until BOTH owner assets are supplied, open the reader immediately.
        if(pendingAudio==0||frameFiles==null||frameStartsMs==null||frameFiles.length==0||
            frameFiles.length!=frameStartsMs.length){enterApp();return;}
        showFrame(0);
        try{player=MediaPlayer.create(this,pendingAudio);
            if(player==null){enterApp();return;}
            player.setOnCompletionListener(mp->enterApp());player.setOnErrorListener((mp,what,extra)->{enterApp();return true;});
            player.start();
            frameTick=new Runnable(){public void run(){
                if(!opening&&player!=null){long position=player.getCurrentPosition();
                    int index=0;for(int i=1;i<frameStartsMs.length;i++)if(position>=frameStartsMs[i])index=i;
                    if(index!=shownFrame)showFrame(index);
                    handler.postDelayed(this,35);
                }
            }};
            handler.post(frameTick);
            int duration=player.getDuration();
            handler.postDelayed(this::enterApp,duration>0?duration+750:10000); // safety after song end
        }catch(Exception ex){enterApp();}
    }
    private void showFrame(int index){
        try(InputStream in=getAssets().open("splash/"+frameFiles[index])){
            BitmapFactory.Options options=new BitmapFactory.Options();options.inPreferredConfig=Bitmap.Config.RGB_565;
            Bitmap next=BitmapFactory.decodeStream(in,null,options);if(next==null)return;
            Bitmap previous=visible;visible=next;shownFrame=index;
            image.animate().cancel();image.setImageBitmap(next);image.setAlpha(0f);
            image.animate().alpha(1f).setDuration(180).start();
            if(previous!=null&&previous!=next)previous.recycle();
        }catch(Exception ex){android.util.Log.w("LaunchIntro","Frame unavailable",ex);}
    }
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private void enterApp(){if(opening)return;opening=true;handler.removeCallbacksAndMessages(null);
        if(player!=null){player.release();player=null;}
        startActivity(new Intent(this,MainActivity.class));finish();
    }
    @Override public void onBackPressed(){enterApp();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);
        if(player!=null){player.release();player=null;}
        image.setImageDrawable(null);if(visible!=null){visible.recycle();visible=null;}
        super.onDestroy();}
}
