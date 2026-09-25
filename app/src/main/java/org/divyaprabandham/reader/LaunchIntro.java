package org.divyaprabandham.reader;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
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
    // Bind owner-provided media resources only after both files have been inspected.
    private int pendingAudio=0,pendingPhoto=0;
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        FrameLayout stage=new FrameLayout(this);stage.setBackgroundColor(0xff190f09);
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if(pendingPhoto!=0)image.setImageResource(pendingPhoto);
        stage.addView(image,new FrameLayout.LayoutParams(-1,-1));
        TextView skip=new TextView(this);skip.setText("Skip intro");skip.setTextSize(14);skip.setTextColor(Color.WHITE);
        skip.setGravity(Gravity.CENTER);skip.setContentDescription("Skip opening intro");
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(dp(110),dp(48),Gravity.TOP|Gravity.END);
        sp.topMargin=dp(24);sp.rightMargin=dp(16);stage.addView(skip,sp);
        stage.setOnClickListener(v->enterApp());skip.setOnClickListener(v->enterApp());
        setContentView(stage);
        // Until BOTH owner assets are supplied, open the reader immediately.
        if(pendingAudio==0||pendingPhoto==0){enterApp();return;}
        image.setAlpha(0f);image.animate().alpha(1f).setDuration(450).start();
        try{player=MediaPlayer.create(this,pendingAudio);
            if(player==null){enterApp();return;}
            player.setOnCompletionListener(mp->enterApp());player.setOnErrorListener((mp,what,extra)->{enterApp();return true;});
            player.start();
            handler.postDelayed(this::enterApp,4000); // bounded even if completion fails
        }catch(Exception ex){enterApp();}
    }
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private void enterApp(){if(opening)return;opening=true;handler.removeCallbacksAndMessages(null);
        if(player!=null){player.release();player=null;}
        startActivity(new Intent(this,MainActivity.class));finish();
    }
    @Override public void onBackPressed(){enterApp();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);
        if(player!=null){player.release();player=null;}super.onDestroy();}
}
