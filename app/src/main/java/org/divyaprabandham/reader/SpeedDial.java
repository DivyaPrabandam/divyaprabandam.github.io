package org.divyaprabandham.reader;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.view.View;

/** Circular, continuous speed control, invoked by a long press on any speed chip. */
final class SpeedDial extends View {
    interface Change {void speed(float value);}
    private final Paint paint=new Paint(3);
    private final Change change;
    private float value;
    private final int surfaceColor,textColor,accentColor,mutedColor;
    SpeedDial(Context context,float initial,Change change,int surfaceColor,int textColor,int accentColor,int mutedColor){
        super(context);this.value=initial;this.change=change;
        this.surfaceColor=surfaceColor;this.textColor=textColor;this.accentColor=accentColor;this.mutedColor=mutedColor;
        setMinimumWidth(dp(240));setMinimumHeight(dp(260));setContentDescription("Rotate to adjust playback speed");
        GradientDrawable surface=new GradientDrawable();surface.setColor(surfaceColor);surface.setCornerRadius(dp(14));setBackground(surface);}

    int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    @Override protected void onMeasure(int ws,int hs){int side=Math.max(dp(240),Math.min(MeasureSpec.getSize(ws),dp(300)));setMeasuredDimension(side,side);}
    @Override protected void onDraw(Canvas canvas){
        float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(cx,cy)-dp(30);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(14));paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(mutedColor);canvas.drawArc(cx-r,cy-r,cx+r,cy+r,135,270,false,paint);
        paint.setColor(accentColor);float progress=(value-.5f)/1.5f;
        canvas.drawArc(cx-r,cy-r,cx+r,cy+r,135,270*progress,false,paint);
        double angle=Math.toRadians(135+270*progress);
        paint.setStyle(Paint.Style.FILL);canvas.drawCircle(cx+(float)Math.cos(angle)*r,cy+(float)Math.sin(angle)*r,dp(14),paint);
        paint.setColor(textColor);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(36));paint.setFakeBoldText(true);
        canvas.drawText(String.format(java.util.Locale.ROOT,"%.2f×",value),cx,cy+dp(10),paint);
        paint.setFakeBoldText(false);paint.setTextSize(dp(13));paint.setColor(mutedColor);
        canvas.drawText("Roll the dial for precise speed",cx,cy+dp(42),paint);
        paint.setStyle(Paint.Style.FILL);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(event.getAction()!=MotionEvent.ACTION_DOWN&&event.getAction()!=MotionEvent.ACTION_MOVE)return event.getAction()==MotionEvent.ACTION_UP;
        float cx=getWidth()/2f,cy=getHeight()/2f;
        double degrees=Math.toDegrees(Math.atan2(event.getY()-cy,event.getX()-cx));
        double fromStart=(degrees-135+360)%360;
        if(fromStart>270)fromStart=fromStart>315?0:270;
        value=Math.round((.5+1.5*fromStart/270)*100)/100f;
        invalidate();change.speed(value);return true;
    }
}
