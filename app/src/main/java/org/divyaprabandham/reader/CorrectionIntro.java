package org.divyaprabandham.reader;

import android.app.Activity;
import android.app.Dialog;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Native version of the site's 2.2s correction-mode introduction. */
final class CorrectionIntro {
    private CorrectionIntro(){}
    static void show(Activity activity){
        if(!ValueAnimator.areAnimatorsEnabled())return;
        Dialog dialog=new Dialog(activity,android.R.style.Theme_Material_Light_NoActionBar_Fullscreen);
        FrameLayout stage=new FrameLayout(activity);
        stage.setBackgroundColor(Color.rgb(217,173,133));
        View sweep=new View(activity);
        sweep.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xff57200f,0xff9c4a1d,0xffdb9652,0xff813715,0xff45190f}));
        stage.addView(sweep,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout center=new LinearLayout(activity);center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);center.setPadding(dp(activity,12),0,dp(activity,12),0);
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,-1);
        stage.addView(center,cp);
        Emblem emblem=new Emblem(activity);center.addView(emblem,new LinearLayout.LayoutParams(dp(activity,220),dp(activity,150)));
        TextView title=new TextView(activity);title.setText("Correction mode");title.setTextSize(38);
        title.setTypeface(android.graphics.Typeface.SERIF);title.setTextColor(0xfffff8ea);title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.topMargin=dp(activity,24);center.addView(title,tp);
        TextView skip=new TextView(activity);skip.setText("Tap anywhere to continue");skip.setTextSize(12);
        skip.setTextColor(0xfff9e6d1);skip.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-1,dp(activity,48),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        sp.bottomMargin=dp(activity,32);stage.addView(skip,sp);
        stage.setOnClickListener(v->dialog.dismiss());
        dialog.setContentView(stage);
        Window window=dialog.getWindow();if(window!=null){window.setLayout(-1,-1);
            window.setStatusBarColor(0xff57200f);window.setNavigationBarColor(0xff45190f);}
        dialog.show();
        if(window!=null)window.setLayout(-1,-1);
        Handler handler=new Handler(Looper.getMainLooper());
        Runnable finish=()->{if(dialog.isShowing())dialog.dismiss();};
        dialog.setOnDismissListener(d->handler.removeCallbacksAndMessages(null));
        stage.post(()->{
            if(!dialog.isShowing())return;
            sweep.setTranslationX(-stage.getWidth());
            sweep.animate().translationX(0).setDuration(400).withEndAction(()->{
                if(dialog.isShowing())sweep.animate().translationX(stage.getWidth()).setStartDelay(1350).setDuration(450).start();
            }).start();
        });
        emblem.setPhase(0);title.setAlpha(0f);title.setTranslationY(dp(activity,10));
        title.animate().alpha(1f).translationY(0f).setStartDelay(260).setDuration(360).start();
        handler.postDelayed(()->emblem.setPhase(1),440);
        handler.postDelayed(()->emblem.setPhase(2),1100);
        handler.postDelayed(()->emblem.setPhase(3),1510);
        handler.postDelayed(finish,2200);
    }
    private static int dp(Activity a,int n){return (int)(a.getResources().getDisplayMetrics().density*n+.5f);}
    private static final class Emblem extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private int phase=0;
        Emblem(Activity activity){super(activity);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        void setPhase(int value){phase=value;setAlpha(0f);setScaleX(.7f);setScaleY(.7f);
            animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280).start();invalidate();}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);
            canvas.save();canvas.scale(getWidth()/220f,getHeight()/150f);
            p.setColor(0xfff9d492);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            if(phase==0){canvas.drawRoundRect(9,8,211,142,15,15,p);p.setStrokeWidth(1);canvas.drawRoundRect(17,16,203,134,12,12,p);}
            else if(phase==1){canvas.drawCircle(110,75,36,p);canvas.drawCircle(110,75,8,p);
                for(int i=0;i<8;i++){canvas.save();canvas.rotate(i*45,110,75);Path blade=new Path();
                    blade.moveTo(110,28);blade.quadTo(124,54,110,66);blade.quadTo(96,54,110,28);canvas.drawPath(blade,p);canvas.restore();}}
            else if(phase==2){p.setStrokeWidth(10);canvas.drawCircle(101,65,34,p);
                canvas.drawLine(125,92,160,128,p);p.setStrokeWidth(2);canvas.drawLine(91,65,111,65,p);canvas.drawLine(101,55,101,75,p);}
            else {Path shell=new Path();shell.moveTo(110,18);shell.cubicTo(76,34,76,60,85,77);
                shell.cubicTo(53,92,69,121,102,108);shell.cubicTo(130,130,159,112,151,88);
                shell.cubicTo(166,58,145,38,117,42);canvas.drawPath(shell,p);
                p.setStrokeWidth(2.5f);canvas.drawLine(112,23,103,108,p);
                canvas.drawLine(91,56,139,101,p);canvas.drawLine(78,80,145,92,p);}
            canvas.restore();
        }
    }
}
