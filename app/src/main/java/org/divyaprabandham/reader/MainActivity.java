package org.divyaprabandham.reader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.widget.Spinner;
import android.widget.SeekBar;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.view.WindowManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import java.io.IOException;
import android.os.Bundle;
import android.os.Build;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.text.TextWatcher;
import android.text.Editable;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Offline reader. The devotional text is copied only from the bundled site JSON. */
public final class MainActivity extends Activity {
    private static final String[] NAMES={"Sannidhi","Olai","Tulasi","Ardhajamam"};
    private static final int[][] PALETTE={
        {0xff190f09,0xfff4e8d0,0xffd6ad5f,0xff302216,0xffae9272},
        {0xfff3e9d4,0xff2a1c11,0xff9e2a1c,0xffefe1c4,0xff745f48},
        {0xfff7f6f2,0xff1b1d1a,0xff1f4d3a,0xffffffff,0xff697269},
        {0xff000000,0xffffffff,0xffffb74d,0xff101010,0xffbbbbbb}
    };
    private final ArrayList<Verse> verses=new ArrayList<>();
    private JSONArray books; private int bookIndex=2; private String bookName="Thiruppavai", bookAlvar="Andal", bookTamil="திருப்பாவை";
    private final ExecutorService searchWorker=Executors.newSingleThreadExecutor();
    private final ExecutorService correctionWorker=Executors.newSingleThreadExecutor();
    private volatile ArrayList<SearchEntry> searchIndex;
    private java.util.concurrent.Future<?> currentSearch;
    private SharedPreferences preferences;
    private CorrectionQueue correctionQueue;
    private CorrectionDrafts correctionDrafts;
    private ContentUpdates contentUpdates;
    private AudioCatalog audioCatalog;
    private AudioController audioController;
    private TextView audioTitle,audioTime,audioStatus,miniAudioTitle,miniAudioTime;
    private SeekBar miniAudioSeek;
    private boolean miniAudioSeekDragging=false;
    private Dialog audioSheet;
    private boolean dockNavShown=true;
    private int navExpandedHeight=0;
    private Button audioToggle,audioLoop,audioGroupLoop,audioAB,miniAudioToggle;
    private SeekBar audioSeek;
    private boolean audioSeekDragging;
    private final ExecutorService updateWorker=Executors.newSingleThreadExecutor();
    private String correctionImage=null;
    private TextView correctionImageStatus;
    private static final int PICK_CORRECTION_IMAGE=481;
    private LinearLayout root, body, bar;
    private boolean landscapePlayer=false;
    private int theme=0, selected=0, textSize=21;
    private long lastSearchElapsedMs=0;
    private android.os.Handler mainHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private String page="Home";
    private String readerParent="Recite",indexParent="Books";
    private long lastRootBackAt=0;
    private android.window.OnBackInvokedCallback systemBackCallback;
    private boolean focusMode=false;
    private boolean correctionMode=false;
    private boolean elderMode=false;
    private boolean manualCheckRunning=false;
    private boolean bookmarked(int n){return preferences.getBoolean("saved-"+n,false);}
    private void setBookmark(int n,boolean value){preferences.edit().putBoolean("saved-"+n,value).apply();}

    private boolean transliteration=false;
    private String madalEpilogueTamil="",madalEpilogueLatin="";
    private boolean isRead(int n){return preferences.getBoolean("read-"+n,false);}
    private void markRead(int n,boolean value){preferences.edit().putBoolean("read-"+n,value).apply();}
    private int readCount(){int count=0;for(int n=1;n<=4000;n++)if(isRead(n))count++;return count;}
    private static final class Verse {
        int number; String tamil, latin, audio;
        Verse(int n,String t,String l,String a){number=n;tamil=t;latin=l;audio=a;}
        String opening(){int end=tamil.indexOf('\n');return end<0?tamil:tamil.substring(0,end);}
    }
    @Override public void onCreate(Bundle saved){super.onCreate(saved);getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        preferences=getSharedPreferences("reading",MODE_PRIVATE);
        // Android 13+ predictive/system back does not reliably call onBackPressed.
        if(Build.VERSION.SDK_INT>=33){systemBackCallback=this::navigateUp;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,systemBackCallback);
        }
        correctionQueue=new CorrectionQueue(this);correctionDrafts=new CorrectionDrafts(this);contentUpdates=new ContentUpdates(this);ContentUpdates.schedule(this);
        try{audioCatalog=new AudioCatalog(this);audioController=new AudioController(this);audioController.listen(this::updateAudioControls);audioController.tick();}
        catch(Exception ex){android.util.Log.w("Audio","Catalog unavailable",ex);}
        theme=preferences.getInt("theme",0); selected=preferences.getInt("selected",0);textSize=preferences.getInt("size",21);
        elderMode=preferences.getBoolean("elder",false);
        transliteration=preferences.getBoolean("transliteration",false);
        try { JSONArray updated=contentUpdates.activeBooks();books=updated!=null?updated:new JSONArray(readAsset("books/manifest.json")); }catch(Exception e){throw new IllegalStateException("Book index missing",e);}
        bookIndex=preferences.getInt("book",2); loadBook(bookIndex); if(selected<0||selected>=verses.size())selected=0; showHome();
        // Check the signed publisher in the background; never pull the reader away from
        // a screen the user opened while the network call was in flight.
        if(ContentUpdates.configured()&&contentUpdates.checkDue()){
            getSharedPreferences("content-settings",MODE_PRIVATE).edit().putString("last-result","checking").remove("last-error").apply();
            updateWorker.execute(()->{try{String state=contentUpdates.check();
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    if("updated".equals(state)){if("Home".equals(page))applyContentUpdate();else searchIndex=null;}
                    else if("app-update-required".equals(state)&&"Home".equals(page))showHome();
                });
            }catch(Exception ex){getSharedPreferences("content-settings",MODE_PRIVATE).edit().putString("last-error",ex.getClass().getSimpleName()).apply();
                android.util.Log.w("ContentUpdates","Check deferred",ex);}});
        }
    }
    private void applyContentUpdate(){try{JSONArray refreshed=contentUpdates.activeBooks();if(refreshed==null)return;
        books=refreshed;searchIndex=null;bookIndex=Math.min(bookIndex,books.length()-1);int number=verses.get(selected).number;
        loadBook(bookIndex);for(int i=0;i<verses.size();i++)if(verses.get(i).number==number){selected=i;break;}
        showHome();
    }catch(Exception e){android.util.Log.w("ContentUpdates","New snapshot could not be shown",e);}}
    private String readAsset(String name)throws Exception{try(InputStream in=getAssets().open(name);ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
        byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);return bytes.toString("UTF-8");}}
    private void loadBook(int which){try{
        JSONObject meta=books.getJSONObject(which);String file=meta.getString("file");String newer=contentUpdates.readBook(file);
        JSONObject data=new JSONObject(newer==null?readAsset("books/"+file):newer);
        ArrayList<Verse> parsed=new ArrayList<>();JSONArray sections=data.getJSONArray("sections");
        for(int s=0;s<sections.length();s++){JSONArray ps=sections.getJSONObject(s).getJSONArray("p");for(int i=0;i<ps.length();i++){
            JSONArray row=ps.getJSONArray(i);String tamil=join(row.getJSONArray(3)),latin=join(row.getJSONArray(2)),audio=row.optString(5,"");
            int first=row.getInt(0),last=row.getInt(1);
            parsed.add(new Verse(first,tamil,latin,audio));
        }}
        verses.clear();verses.addAll(parsed);bookIndex=which;bookName=data.getString("name");bookAlvar=data.getString("alvar");bookTamil=data.getString("nameTa");
        JSONObject epilogue=sections.getJSONObject(0).optJSONObject("epilogue");
        madalEpilogueTamil=epilogue==null?"":join(epilogue.getJSONArray("ta"));
        madalEpilogueLatin=epilogue==null?"":join(epilogue.getJSONArray("le"));
    }catch(Exception e){throw new IllegalStateException("Bundled text missing or unreadable",e);}}
    private static String join(JSONArray lines)throws Exception{StringBuilder b=new StringBuilder();for(int i=0;i<lines.length();i++){if(i>0)b.append('\n');b.append(lines.getString(i));}return b.toString();}
    private int bg(){return PALETTE[theme][0];} private int fg(){return PALETTE[theme][1];} private int ac(){return PALETTE[theme][2];} private int surface(){return PALETTE[theme][3];} private int muted(){return PALETTE[theme][4];}
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String str,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(str);t.setTextSize(elderMode?Math.max(sp,Math.min(sp+3,25)):sp);t.setTextColor(color);t.setLineSpacing(dp(3),1.12f);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private void pad(View v,int a,int b,int c,int d){v.setPadding(dp(a),dp(b),dp(c),dp(d));}
    private void add(LinearLayout into,View child){into.addView(child,new LinearLayout.LayoutParams(-1,-2));}
    private LinearLayout card(LinearLayout into){LinearLayout c=column();c.setBackground(shape(surface(),theme==1?8:18));pad(c,16,15,16,15);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(16),dp(8),dp(16),dp(4));into.addView(c,p);return c;}
    private Button button(String label,Runnable run,boolean strong){Button b=new Button(this);b.setText(label);b.setTextSize(elderMode?15:12);b.setAllCaps(false);b.setMinimumHeight(dp(elderMode?56:48));b.setTextColor(strong?bg():ac());b.setBackground(shape(strong?ac():surface(),theme==1?7:23));b.setOnClickListener(v->run.run());return b;}
    private void start(String title,String subtitle,String active){
        getWindow().setStatusBarColor(bg());getWindow().setNavigationBarColor(bg());
        getWindow().getDecorView().setSystemUiVisibility(theme==1||theme==2?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0);
        audioTitle=null;audioTime=null;audioStatus=null;audioToggle=null;audioLoop=null;audioGroupLoop=null;audioAB=null;audioSeek=null;miniAudioTitle=null;miniAudioTime=null;miniAudioSeek=null;miniAudioToggle=null;
        root=column();root.setBackgroundColor(bg());
        landscapePlayer=getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE
            &&audioController!=null&&audioController.track()!=null;
        root.setOnApplyWindowInsetsListener((v,insets)->{
            // Android 15+ enforces edge-to-edge at this target SDK; older releases lay out below bars.
            if(Build.VERSION.SDK_INT>=35)root.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());
            else root.setPadding(0,0,0,0);
            return insets;
        });
        // Navigation replaces this view within MainActivity, not the launch intro.
        // Keep the whole screen opaque at its final position; fading its root from 50%
        // caused a black flash on AMOLED every time Home or another tab was tapped.
        if(landscapePlayer){
            LinearLayout frame=new LinearLayout(this);frame.setBackgroundColor(bg());
            LinearLayout side=buildLandscapePlayer();
            // Match the portrait dock's 94dp thickness, rotated into a narrow side rail.
            int sideWidth=dp(94);
            if(preferences.getBoolean("player-side-right",false)){
                frame.addView(root,new LinearLayout.LayoutParams(0,-1,1));
                frame.addView(side,new LinearLayout.LayoutParams(sideWidth,-1));
            }else{
                frame.addView(side,new LinearLayout.LayoutParams(sideWidth,-1));
                frame.addView(root,new LinearLayout.LayoutParams(0,-1,1));
            }
            setContentView(frame);
        }else setContentView(root);
        if(correctionMode){Button exit=button("Exit correction mode",this::exitCorrectionMode,true);
            LinearLayout top=column();top.setGravity(Gravity.RIGHT);top.setBackground(shape(surface(),18));
            LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-2,dp(56));ep.setMargins(0,0,dp(12),0);top.addView(exit,ep);
            root.addView(top,new LinearLayout.LayoutParams(-1,dp(60)));
        }
        ScrollView scroll=new ScrollView(this);currentScroll=scroll;scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);body=column();scroll.addView(body);
        // The reader header belongs to scroll content, never a fixed pane above the verse.
        LinearLayout headingHost=page.equals("Reader")?body:root;
        if(!page.equals("Home")){
            TextView up=text("‹  "+upLabel(),16,ac(),true);pad(up,20,12,20,8);
            up.setGravity(Gravity.CENTER_VERTICAL);up.setMinimumHeight(dp(48));
            up.setContentDescription("Back to "+upLabel());up.setOnClickListener(v->navigateUp());add(headingHost,up);
        }
        TextView heading=text(title,27,fg(),true);pad(heading,20,15,20,0);add(headingHost,heading);
        TextView sub=text(subtitle,12,muted(),false);pad(sub,20,3,20,13);add(headingHost,sub);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(audioController!=null&&audioController.track()!=null&&!landscapePlayer)showMiniAudio();
        bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER);bar.setBackgroundColor(bg());pad(bar,7,8,7,8);
        navExpandedHeight=dp(elderMode?74:68);
        if(!page.equals("Reader"))dockNavShown=true;
        else dockNavShown=true;
        root.addView(bar,new LinearLayout.LayoutParams(-1,dockNavShown?navExpandedHeight:0));
        scroll.setOnScrollChangeListener((View v,int x,int y,int oldX,int oldY)->{
            if(audioController==null||audioController.track()==null)return;
            int delta=y-oldY;
            if(Math.abs(delta)<dp(5))return;
            setDockNavShown(delta<0);
        });
        String[] tabs={"Home","Recite","Learn","Explore","Search","Settings"};for(String tab:tabs){
            TextView link=text(tab,11,active.equals(tab)?ac():muted(),active.equals(tab));link.setGravity(Gravity.CENTER);link.setMinimumHeight(dp(elderMode?56:48));
            bar.addView(link,new LinearLayout.LayoutParams(0,dp(elderMode?58:52),1));link.setOnClickListener(v->{switch(tab){case "Home":showHome();break;case "Search":showSearch();break;case "Recite":showBooks();break;case "Settings":showSettings();break;default:showNotice(tab);}});
        }
    }
    private void setDockNavShown(boolean visible){
        if(bar==null||dockNavShown==visible)return;
        dockNavShown=visible;
        int from=bar.getLayoutParams().height,to=visible?navExpandedHeight:0;
        ValueAnimator animator=ValueAnimator.ofInt(from,to);animator.setDuration(180);
        animator.addUpdateListener(a->{if(bar==null)return;android.view.ViewGroup.LayoutParams params=bar.getLayoutParams();
            params.height=(int)a.getAnimatedValue();bar.setLayoutParams(params);});
        animator.start();
    }
    private void showHome(){page="Home";start("Divya Prabandham","Available offline · 25 prabandhams","Home");
        TextView invocation=text("ஸ்ரீ:",24,ac(),true);invocation.setGravity(Gravity.CENTER);pad(invocation,0,20,0,10);add(body,invocation);
        LinearLayout box=card(body);add(box,text("CONTINUE READING",11,ac(),true));
        Verse verse=verses.get(selected);TextView line=text(verse.opening(),20,fg(),false);pad(line,0,12,0,12);add(box,line);
        add(box,text(bookName+" · "+bookAlvar+" · "+verse.number+" of 4,000",12,muted(),false));
        Button cont=button("Read pasuram",()->showReader(selected),true);pad(cont,12,4,12,4);add(box,cont);
        LinearLayout b=card(body);add(b,text(bookName,21,fg(),true));add(b,text(bookTamil+" · "+bookAlvar+" · "+passageCountLabel(),13,muted(),false));
        add(b,button("Continue in "+bookName,this::showIndex,false));
        add(card(body),button("Browse all 25 prabandhams",this::showBooks,true));
    }
    private void showBooks(){page="Books";start("The 4,000 pasurams","25 prabandhams · offline","Recite");
        int shown=0;
        for(int i=0;i<books.length();i++){final int idx=i;
            try{JSONObject meta=books.getJSONObject(i);String name=meta.optString("name","").trim();
                if(name.isEmpty())name=meta.optString("id","Book "+(i+1));
                LinearLayout c=card(body);add(c,text(name,19,fg(),true));
                String alvar=meta.optString("alvar","");
                add(c,text(alvar+" · "+meta.getInt("start")+"–"+meta.getInt("end"),12,muted(),false));
                c.setMinimumHeight(dp(72));c.setOnClickListener(v->{try{loadBook(idx);selected=0;
                    preferences.edit().putInt("book",idx).putInt("selected",0).apply();showIndex();}
                    catch(Exception ex){new AlertDialog.Builder(this).setMessage("This book could not open. The rest of the library is still available.")
                        .setPositiveButton("OK",null).show();}});shown++;
            }catch(Exception ex){android.util.Log.w("Books","Skipping malformed list entry "+i,ex);}
        }
        if(shown==0)add(card(body),text("The book list could not be shown. Reading your last open book remains available.",13,muted(),false));}
    private boolean madalBook(){return bookIndex==21||bookIndex==22;}
    private int madalFirst(){return bookIndex==21?2673:2713;}
    private int madalLast(){return bookIndex==21?2712:2790;}
    private int madalNumberedCount(){return madalLast()-madalFirst()+1;}
    private String passageCountLabel(){return madalBook()?madalNumberedCount()+" numbered pasurams":verses.size()+" passages";}
    private int loadedIndexCards=0; private int searchGeneration=0;
    private ScrollView currentScroll;
    private void showIndex(){if(!page.equals("Reader")&&!page.equals("Recite"))indexParent=page;
        page="Recite";start(bookName,bookAlvar+" · "+bookTamil+" · "+passageCountLabel(),"Recite");
        loadedIndexCards=0;appendIndexCards();
    }
    private void appendIndexCards(){int limit=Math.min(loadedIndexCards+35,verses.size());
        for(int i=loadedIndexCards;i<limit;i++){final int index=i;Verse v=verses.get(i);LinearLayout c=card(body);
            TextView line=text("Pasuram "+v.number+"  "+v.opening(),16,fg(),false);
            pad(line,0,1,0,5);add(c,line);
            add(c,text(bookAlvar+" · "+bookName+" "+(i+1),11,muted(),false));
            c.setOnClickListener(w->showReader(index));c.setMinimumHeight(dp(72));
        }
        loadedIndexCards=limit;
        if(limit<verses.size())add(card(body),button("Show next "+Math.min(35,verses.size()-limit)+" of "+verses.size(),()->{
            if(body.getChildCount()>0)body.removeViewAt(body.getChildCount()-1);int y=currentScroll.getScrollY();appendIndexCards();currentScroll.post(()->currentScroll.scrollTo(0,y));},false));
    }
    private void showReader(int index){if(!page.equals("Reader"))readerParent=page;
        selected=Math.max(0,Math.min(verses.size()-1,index));preferences.edit().putInt("selected",selected).apply();page="Reader";
        Verse v=verses.get(selected);start(bookName+" · "+v.number,
            bookAlvar+" · pasuram "+v.number+" of 4,000","Recite");
        LinearLayout c=card(body);
        LinearLayout verseHeading=new LinearLayout(this);verseHeading.setGravity(Gravity.CENTER_VERTICAL);add(c,verseHeading);
        TextView source=text(bookTamil+" · "+bookAlvar,15,ac(),true);
        verseHeading.addView(source,new LinearLayout.LayoutParams(0,-2,1));
        AudioCatalog.Track currentTrack=audioCatalog==null?null:audioCatalog.verse(v.number);
        if(currentTrack!=null||audioCatalog!=null&&!audioCatalog.recordings(books.optJSONObject(bookIndex).optString("id"),
            audioCatalog.groupRange(books.optJSONObject(bookIndex).optString("id"),v.number,books.optJSONObject(bookIndex).optInt("end"))[0]).isEmpty()){
            Button play=button("▶",()->{
                AudioCatalog.Track one=audioCatalog.verse(v.number);
                ArrayList<AudioCatalog.Track> tracks=new ArrayList<>();
                if(one!=null)tracks.add(one);
                else tracks=audioCatalog.recordings(books.optJSONObject(bookIndex).optString("id"),
                    audioCatalog.groupRange(books.optJSONObject(bookIndex).optString("id"),v.number,books.optJSONObject(bookIndex).optInt("end"))[0]);
                if(!tracks.isEmpty()){ArrayList<AudioCatalog.Track> single=new ArrayList<>();single.add(tracks.get(0));playAudio(single,0);}
            },true);
            play.setContentDescription(madalBook()?"Play full madal recording":"Play pasuram "+v.number);
            GradientDrawable circle=shape(ac(),48);play.setBackground(circle);
            verseHeading.addView(play,new LinearLayout.LayoutParams(dp(46),dp(46)));
        }
        if(getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE){
            TextView smaller=text("A−",15,ac(),true);smaller.setGravity(Gravity.CENTER);
            smaller.setContentDescription("Decrease pasuram font size");
            smaller.setBackground(shape(surface(),24));
            LinearLayout.LayoutParams smallParams=new LinearLayout.LayoutParams(dp(44),dp(46));smallParams.leftMargin=dp(5);
            verseHeading.addView(smaller,smallParams);
            TextView bigger=text("A+",15,ac(),true);bigger.setGravity(Gravity.CENTER);
            bigger.setContentDescription("Increase pasuram font size");
            bigger.setBackground(shape(surface(),24));
            LinearLayout.LayoutParams bigParams=new LinearLayout.LayoutParams(dp(44),dp(46));bigParams.leftMargin=dp(4);
            verseHeading.addView(bigger,bigParams);
            smaller.setOnClickListener(clicked->{textSize=Math.max(16,textSize-2);
                preferences.edit().putInt("size",textSize).apply();showReader(selected);});
            bigger.setOnClickListener(clicked->{textSize=Math.min(34,textSize+2);
                preferences.edit().putInt("size",textSize).apply();showReader(selected);});
        }
        TextView verse=text(transliteration?v.latin:v.tamil,textSize,fg(),false);verse.setTextSize(textSize+(elderMode?4:0));verse.setLineSpacing(dp(elderMode?12:7),elderMode?1.5f:1.28f);pad(verse,0,22,0,20);add(c,verse);
        if(madalBook()&&selected==verses.size()-1&&!madalEpilogueTamil.isEmpty()){
            add(c,text("Closing lines · unnumbered",13,ac(),true));
            add(c,text(transliteration?madalEpilogueLatin:madalEpilogueTamil,textSize,fg(),false));
        }
        if(!focusMode)
            add(c,text(madalBook()?"Text: canonical site edition, numbered divisions cross-checked with Prapatti · source marks retained":"Text: bundled verbatim from the site edition · recitation marks kept",11,muted(),false));
        if(!focusMode&&audioCatalog!=null&&audioController!=null)addReaderAudio(v);
        if(correctionMode){LinearLayout mode=card(body);add(mode,text("CORRECTION MODE · reading text is unchanged",14,ac(),true));
            add(mode,text("The highlighted correction controls are separate from ordinary reading.",12,muted(),false));}
        LinearLayout reading=card(body);pad(reading,9,8,9,8);
        LinearLayout readingActions=new LinearLayout(this);readingActions.setGravity(Gravity.CENTER);add(reading,readingActions);
        Button focus=button(focusMode?"● Focus":"● Focus",()->{
            focusMode=!focusMode;showReader(selected);
        },focusMode);focus.setContentDescription(focusMode?"Exit focus mode":"Focus on this pasuram");focus.setTextSize(11);
        readingActions.addView(focus,new LinearLayout.LayoutParams(0,dp(46),1));
        Button mark=button(isRead(v.number)?"✓ Read":"Mark as read",()->{
            boolean now=!isRead(v.number);markRead(v.number,now);showReader(selected);
        },isRead(v.number));mark.setContentDescription(isRead(v.number)?"Undo mark as read":"Mark pasuram as read");mark.setTextSize(11);
        readingActions.addView(mark,new LinearLayout.LayoutParams(0,dp(46),1));
        Button save=button(bookmarked(v.number)?"★ Saved":"☆ Save",()->{
            setBookmark(v.number,!bookmarked(v.number));showReader(selected);
        },bookmarked(v.number));save.setContentDescription(bookmarked(v.number)?"Remove saved pasuram":"Save pasuram");save.setTextSize(11);
        readingActions.addView(save,new LinearLayout.LayoutParams(0,dp(46),1));
        if(correctionMode)add(card(body),button(correctionDrafts.has(v.number)?"Continue correction draft":"Suggest a correction",()->showCorrectionSheet(v),true));
        LinearLayout controls=card(body);pad(controls,9,8,9,8);
        add(controls,button(transliteration?"தமிழ்":"English transliteration",()->{
            transliteration=!transliteration;preferences.edit().putBoolean("transliteration",transliteration).apply();showReader(selected);
        },false));
        LinearLayout size=new LinearLayout(this);size.setGravity(Gravity.CENTER_VERTICAL);add(controls,size);
        TextView sizeLabel=text("Text size",12,fg(),false);size.addView(sizeLabel,new LinearLayout.LayoutParams(0,-2,1));
        Button smallerSize=button("A−",()->{textSize=Math.max(16,textSize-2);preferences.edit().putInt("size",textSize).apply();showReader(selected);},false);
        size.addView(smallerSize,new LinearLayout.LayoutParams(dp(52),dp(43)));
        Button largerSize=button("A+",()->{textSize=Math.min(34,textSize+2);preferences.edit().putInt("size",textSize).apply();showReader(selected);},false);
        size.addView(largerSize,new LinearLayout.LayoutParams(dp(52),dp(43)));
        LinearLayout move=card(body);LinearLayout buttons=new LinearLayout(this);add(move,buttons);
        Button previous=button("‹ Previous",()->showReader(selected-1),false);previous.setEnabled(selected>0);buttons.addView(previous,new LinearLayout.LayoutParams(0,dp(48),1));
        Button next=button("Next ›",()->showReader(selected+1),true);next.setEnabled(selected<verses.size()-1);buttons.addView(next,new LinearLayout.LayoutParams(0,dp(48),1));
        Button jump=button("↗",this::showJumpSelector,false);jump.setContentDescription("Jump to a pasuram");
        buttons.addView(jump,new LinearLayout.LayoutParams(dp(50),dp(48)));
    }
    private void showJumpSelector(){
        LinearLayout fields=column();pad(fields,20,8,20,5);
        add(fields,text("Prabandham",13,fg(),true));
        ArrayList<String> names=new ArrayList<>();
        for(int i=0;i<books.length();i++)names.add(books.optJSONObject(i).optString("name","Book "+(i+1)));
        Spinner pick=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names);
        pick.setAdapter(adapter);pick.setSelection(bookIndex);add(fields,pick);
        add(fields,text("Pasuram in this prabandham (1–end)",13,fg(),true));
        EditText number=new EditText(this);number.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        number.setSingleLine(true);number.setText(String.valueOf(selected+1));number.setSelectAllOnFocus(true);
        add(fields,number);
        pick.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> parent){}
            public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){
                if(position!=bookIndex)number.setText("1");
                JSONObject meta=books.optJSONObject(position);int count=meta==null?1:meta.optInt("end")-meta.optInt("start")+1;
                number.setHint("1–"+count);
            }
        });
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Jump to pasuram").setView(fields)
            .setNegativeButton("Cancel",null).setPositiveButton("Go",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view->{
            int targetBook=pick.getSelectedItemPosition();JSONObject meta=books.optJSONObject(targetBook);
            if(meta==null)return;
            int count=meta.optInt("end")-meta.optInt("start")+1;
            int wanted;try{wanted=Integer.parseInt(number.getText().toString().trim());}
            catch(Exception ex){number.setError("Enter a pasuram number");return;}
            if(wanted<1||wanted>count){number.setError("Choose 1–"+count);return;}
            if(targetBook!=bookIndex){loadBook(targetBook);preferences.edit().putInt("book",targetBook).apply();}
            dialog.dismiss();showReader(wanted-1);
        }));
        dialog.show();
    }
    private LinearLayout buildLandscapePlayer(){
        LinearLayout rail=column();rail.setGravity(Gravity.CENTER_HORIZONTAL);
        rail.setBackground(shape(surface(),theme==1?8:16));pad(rail,5,5,5,5);
        TextView tune=text("♫",22,ac(),true);tune.setGravity(Gravity.CENTER);
        rail.addView(tune,new LinearLayout.LayoutParams(-1,dp(34)));
        miniAudioTitle=text(audioController.track().title,11,fg(),true);
        miniAudioTitle.setSingleLine(true);miniAudioTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        miniAudioTitle.setGravity(Gravity.CENTER);add(rail,miniAudioTitle);
        miniAudioTime=text("",10,muted(),false);miniAudioTime.setGravity(Gravity.CENTER);add(rail,miniAudioTime);
        ScrollView controls=new ScrollView(this);controls.setFillViewport(false);controls.setVerticalScrollBarEnabled(false);
        LinearLayout stack=column();controls.addView(stack);rail.addView(controls,new LinearLayout.LayoutParams(-1,0,1));
        miniAudioToggle=button("▶",audioController::toggle,true);
        miniAudioToggle.setContentDescription("Play or pause audio");add(stack,miniAudioToggle);
        Button previous=button("‹",audioController::previous,false);previous.setContentDescription("Previous recording");add(stack,previous);
        Button next=button("›",audioController::next,false);next.setContentDescription("Next recording");add(stack,next);
        Button more=button("⋯",this::showPlayerSheet,false);more.setContentDescription("All audio controls");add(stack,more);
        // Vertical seek is drawn in the narrow rail. Exact seeking and all presets remain in the full sheet.
        View progress=new View(this){
            private final android.graphics.Paint pen=new android.graphics.Paint(3);
            @Override protected void onDraw(android.graphics.Canvas canvas){
                float cx=getWidth()/2f,top=dp(9),bottom=getHeight()-dp(9);
                int duration=audioController.duration();float fraction=duration>0?Math.min(1f,(float)audioController.position()/duration):0f;
                pen.setStrokeWidth(dp(4));pen.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                pen.setColor(muted());canvas.drawLine(cx,top,cx,bottom,pen);
                pen.setColor(ac());float y=bottom-fraction*(bottom-top);canvas.drawLine(cx,y,cx,bottom,pen);
                canvas.drawCircle(cx,y,dp(7),pen);
            }
            @Override public boolean onTouchEvent(android.view.MotionEvent e){
                if(e.getAction()==android.view.MotionEvent.ACTION_DOWN||e.getAction()==android.view.MotionEvent.ACTION_MOVE||e.getAction()==android.view.MotionEvent.ACTION_UP){
                    float top=dp(9),bottom=getHeight()-dp(9);
                    if(bottom>top)audioController.seek((long)(audioController.duration()*Math.max(0f,Math.min(1f,(bottom-e.getY())/(bottom-top)))));
                    invalidate();return true;
                }
                return false;
            }
        };progress.setContentDescription("Vertical playback position; drag to seek");
        stack.addView(progress,new LinearLayout.LayoutParams(-1,dp(76)));
        mainHandler.post(new Runnable(){public void run(){if(landscapePlayer&&progress.isAttachedToWindow()){
            progress.invalidate();mainHandler.postDelayed(this,350);
        }}});
        boolean right=preferences.getBoolean("player-side-right",false);
        Button side=button(right?"⇦":"⇨",()->{
            preferences.edit().putBoolean("player-side-right",!right).apply();showCurrentPage();
        },false);side.setContentDescription(right?"Move player left":"Move player right");
        rail.addView(side,new LinearLayout.LayoutParams(-1,dp(46)));
        updateAudioControls();return rail;
    }
    private String audioClock(int milliseconds){int seconds=Math.max(0,milliseconds/1000);
        return String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}
    private void updateAudioControls(){
        if(audioController==null)return;
        AudioCatalog.Track track=audioController.track();
        if(audioTitle!=null)audioTitle.setText(track==null?"Audio":track.title+" · "+(audioController.queueIndex()+1)+"/"+audioController.queueSize());
        if(miniAudioTitle!=null)miniAudioTitle.setText(track==null?"Audio":track.title);
        if(miniAudioToggle!=null)miniAudioToggle.setText(audioController.playing()?"Ⅱ":"▶");
        if(miniAudioTime!=null)miniAudioTime.setText(audioClock(audioController.position())+" / "+audioClock(audioController.duration()));
        if(miniAudioSeek!=null&&!miniAudioSeekDragging){int duration=audioController.duration();miniAudioSeek.setProgress(duration>0?(int)(1000L*audioController.position()/duration):0);}
        if(audioToggle!=null)audioToggle.setText(audioController.playing()?"Pause":"Play");
        if(audioLoop!=null)audioLoop.setText(audioController.repeatOne()?"Repeat track ✓":"Repeat track");
        if(audioGroupLoop!=null)audioGroupLoop.setText(audioController.repeatGroup()?"Repeat group ✓":"Repeat group");
        if(audioAB!=null)audioAB.setText(audioController.markA()<0?"Set A":audioController.markB()<0?"Set B":"Clear A–B");
        if(audioTime!=null)audioTime.setText(audioClock(audioController.position())+" / "+audioClock(audioController.duration()));
        if(audioSeek!=null&&!audioSeekDragging){int d=audioController.duration();audioSeek.setProgress(d>0?(int)(1000L*audioController.position()/d):0);}
        if(audioStatus!=null)audioStatus.setText(audioController.error().isEmpty()?audioController.cached()?"Saved offline":"Streaming · save for offline use":audioController.error());
    }
    private void showMiniAudio(){
        // This dock is outside the scrolling reader, always in reach on every tab.
        LinearLayout dock=column();dock.setBackground(shape(surface(),theme==1?8:16));
        LinearLayout.LayoutParams dockParams=new LinearLayout.LayoutParams(-1,dp(94));
        dockParams.setMargins(dp(8),dp(2),dp(8),dp(2));root.addView(dock,dockParams);
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);pad(row,12,3,8,0);add(dock,row);
        TextView icon=text("♫",24,ac(),true);icon.setGravity(Gravity.CENTER);
        row.addView(icon,new LinearLayout.LayoutParams(dp(38),dp(50)));
        LinearLayout info=column();pad(info,5,0,0,0);row.addView(info,new LinearLayout.LayoutParams(0,dp(50),1));
        miniAudioTitle=text(audioController.track().title,14,fg(),true);miniAudioTitle.setSingleLine(true);
        miniAudioTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);add(info,miniAudioTitle);
        miniAudioTime=text("",11,muted(),false);add(info,miniAudioTime);
        info.setOnClickListener(v->showPlayerSheet());icon.setOnClickListener(v->showPlayerSheet());
        TextView expand=text("⌃",22,ac(),true);expand.setGravity(Gravity.CENTER);
        expand.setContentDescription("Open audio controls");row.addView(expand,new LinearLayout.LayoutParams(dp(42),dp(48)));
        expand.setOnClickListener(v->showPlayerSheet());
        miniAudioToggle=button("▶",audioController::toggle,true);
        miniAudioToggle.setContentDescription("Play or pause audio");
        row.addView(miniAudioToggle,new LinearLayout.LayoutParams(dp(46),dp(46)));
        miniAudioSeek=new SeekBar(this);miniAudioSeek.setMax(1000);miniAudioSeek.setPadding(dp(8),0,dp(8),0);
        dock.addView(miniAudioSeek,new LinearLayout.LayoutParams(-1,dp(36)));
        miniAudioSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar seek){miniAudioSeekDragging=true;}
            public void onStopTrackingTouch(SeekBar seek){miniAudioSeekDragging=false;audioController.seek(audioController.duration()*seek.getProgress()/1000L);}
            public void onProgressChanged(SeekBar seek,int progress,boolean user){}
        });
        updateAudioControls();
    }
    private void showPlayerSheet(){
        if(audioController==null||audioController.track()==null)return;
        if(audioSheet!=null&&audioSheet.isShowing())return;
        audioSheet=new Dialog(this);audioSheet.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=column();panel.setBackground(shape(surface(),theme==1?8:22));pad(panel,16,10,16,18);
        TextView header=text("LISTEN  ·  DRAG TO SEEK",11,ac(),true);add(panel,header);
        audioTitle=text("",18,fg(),true);pad(audioTitle,0,8,0,3);add(panel,audioTitle);
        audioStatus=text("",12,muted(),false);add(panel,audioStatus);
        audioTime=text("",12,fg(),false);pad(audioTime,0,8,0,0);add(panel,audioTime);
        audioSeek=new SeekBar(this);audioSeek.setMax(1000);add(panel,audioSeek);
        audioSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar seek){audioSeekDragging=true;}
            public void onStopTrackingTouch(SeekBar seek){audioSeekDragging=false;audioController.seek(audioController.duration()*seek.getProgress()/1000L);}
            public void onProgressChanged(SeekBar seek,int progress,boolean user){}
        });
        LinearLayout transport=new LinearLayout(this);add(panel,transport);
        transport.addView(button("‹",audioController::previous,false),new LinearLayout.LayoutParams(0,dp(48),1));
        audioToggle=button("Play",audioController::toggle,true);transport.addView(audioToggle,new LinearLayout.LayoutParams(0,dp(48),2));
        transport.addView(button("›",audioController::next,false),new LinearLayout.LayoutParams(0,dp(48),1));
        LinearLayout looping=new LinearLayout(this);add(panel,looping);
        audioLoop=button("Repeat track",audioController::one,false);looping.addView(audioLoop,new LinearLayout.LayoutParams(0,dp(48),1));
        audioGroupLoop=button("Repeat group",audioController::group,false);looping.addView(audioGroupLoop,new LinearLayout.LayoutParams(0,dp(48),1));
        LinearLayout ab=new LinearLayout(this);add(panel,ab);
        audioAB=button("Set A",()->{if(audioController.markA()<0)audioController.setA();else if(audioController.markB()<0)audioController.setB();else audioController.clearAB();},false);
        ab.addView(audioAB,new LinearLayout.LayoutParams(0,dp(48),1));
        ab.addView(button("Save offline",audioController::download,false),new LinearLayout.LayoutParams(0,dp(48),1));
        add(panel,text("Hold a speed for the precise dial",11,muted(),false));
        LinearLayout rates=new LinearLayout(this);add(panel,rates);
        for(float rate:new float[]{.5f,.75f,1f,1.25f,1.5f}){
            Button chip=button(String.format(java.util.Locale.ROOT,"%.2f×",rate),()->audioController.speed(rate),false);
            rates.addView(chip,new LinearLayout.LayoutParams(0,dp(48),1));
            chip.setOnLongClickListener(view->{audioController.speed(rate);SpeedDial dial=new SpeedDial(this,rate,audioController::speed);
                new AlertDialog.Builder(this).setTitle("Precise speed").setView(dial).setPositiveButton("Done",null).show();return true;});
        }
        if(audioController.queueSize()>1)add(panel,text("Repeat group loops the sequence. Save offline stores the current recording only.",11,muted(),false));
        ScrollView content=new ScrollView(this);content.setFillViewport(false);content.addView(panel);
        audioSheet.setContentView(content);
        Window window=audioSheet.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.BOTTOM);window.setLayout(-1,Math.min(dp(530),(int)(getResources().getDisplayMetrics().heightPixels*.74f)));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams p=window.getAttributes();p.dimAmount=.28f;window.setAttributes(p);}
        audioSheet.setOnDismissListener(d->{audioSheet=null;audioTitle=null;audioStatus=null;audioTime=null;audioSeek=null;
            audioToggle=null;audioLoop=null;audioGroupLoop=null;audioAB=null;});
        audioSheet.show();
        if(window!=null)window.setLayout(-1,Math.min(dp(530),(int)(getResources().getDisplayMetrics().heightPixels*.74f)));
        updateAudioControls();
    }
    private void showCurrentPage(){switch(page){case "Reader":showReader(selected);break;case "Home":showHome();break;case "Books":showBooks();break;case "ContentSettings":showContentSettings();break;case "Settings":showSettings();break;default:break;}}
    private void playAudio(ArrayList<AudioCatalog.Track> tracks,int start){
        if(tracks.isEmpty()){Toast.makeText(this,"No recording for this passage yet.",Toast.LENGTH_LONG).show();return;}
        audioController.play(tracks,start);showCurrentPage();
    }
    private void addReaderAudio(Verse currentVerse){
        LinearLayout panel=card(body);pad(panel,9,8,9,8);
        AudioCatalog.Track individual=audioCatalog.verse(currentVerse.number);
        String bookId=books.optJSONObject(bookIndex).optString("id");
        int[] range=audioCatalog.groupRange(bookId,currentVerse.number,books.optJSONObject(bookIndex).optInt("end"));
        ArrayList<AudioCatalog.Track> group=new ArrayList<>();int startIndex=0;
        for(int n=range[0];n<=range[1];n++){
            AudioCatalog.Track item=audioCatalog.verse(n);
            if(item!=null){if(n==currentVerse.number)startIndex=group.size();group.add(item);}
        }
        ArrayList<AudioCatalog.Track> recordings=audioCatalog.recordings(bookId,range[0]);
        LinearLayout options=new LinearLayout(this);options.setGravity(Gravity.CENTER_VERTICAL);add(panel,options);
        if(group.size()>1){final int from=startIndex;
            Button groupPlay=button("Group "+range[0]+"–"+range[1],()->{
                playAudio(group,from);audioController.repeatGroupOn();
            },false);
            groupPlay.setContentDescription("Play group "+range[0]+" to "+range[1]+" as "+group.size()+" separate tracks");
            groupPlay.setTextSize(10);options.addView(groupPlay,new LinearLayout.LayoutParams(0,dp(48),1));
        }
        if(!recordings.isEmpty()){
            AudioCatalog.Track recording=recordings.get(0);
            Button full=button(madalBook()?"Full madal recording":"Full group recording",()->{
                ArrayList<AudioCatalog.Track> one=new ArrayList<>();one.add(recording);playAudio(one,0);
            },false);
            full.setTextSize(10);options.addView(full,new LinearLayout.LayoutParams(0,dp(48),1));
            for(int i=1;i<recordings.size();i++){
                AudioCatalog.Track alternate=recordings.get(i);
                add(panel,button("Play "+alternate.title,()->{
                    ArrayList<AudioCatalog.Track> one=new ArrayList<>();one.add(alternate);playAudio(one,0);
                },false));
            }
        }
        if(audioController.track()!=null){Button player=button("Player ▶",this::showPlayerSheet,false);player.setTextSize(10);
            options.addView(player,new LinearLayout.LayoutParams(0,dp(48),1));}
        if(individual==null&&recordings.isEmpty())add(panel,text("No verified recording is mapped to this passage yet.",12,muted(),false));
        if(madalBook())add(panel,text("One full-madal recording is available. Per-pasuram audio boundaries are not verified; use seek or A–B on the full recording.",12,muted(),false));
    }
    private void enterCorrectionMode(){
        new AlertDialog.Builder(this).setTitle("Correction mode")
            .setMessage("Correction controls are highlighted while this mode is on. Reading text is unchanged. Reports cannot be sent from this build yet.")
            .setPositiveButton("Continue",(d,w)->{correctionMode=true;if(page.equals("Reader"))showReader(selected);else showSettings();
                CorrectionIntro.show(this);})
            .setNegativeButton("Cancel",null).show();
    }
    private void exitCorrectionMode(){correctionMode=false;
        if(page.equals("Reader"))showReader(selected);else showSettings();
        android.widget.Toast.makeText(this,"Correction mode off. Drafts remain on this phone.",android.widget.Toast.LENGTH_SHORT).show();
    }
    private EditText correctionInput(String label,String value,int max,LinearLayout container){
        TextView heading=text(label,13,fg(),true);pad(heading,0,12,0,0);add(container,heading);
        EditText field=new EditText(this);field.setTextColor(fg());field.setHintTextColor(muted());field.setTextSize(15);field.setHint(label);
        field.setText(value);if(max>0)field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(max)});
        add(container,field);return field;
    }
    private static String value(EditText field){return field.getText().toString().trim();}
    private void showCorrectionSheet(Verse verse){
        JSONObject savedDraft;
        try{savedDraft=correctionDrafts.read(verse.number);}catch(Exception ex){new AlertDialog.Builder(this).setMessage("Could not open the saved draft. Nothing was deleted.").setPositiveButton("OK",null).show();return;}
        correctionImage=savedDraft.optString("image",null);
        final long openedAt=android.os.SystemClock.elapsedRealtime();
        LinearLayout fields=column();pad(fields,18,10,18,20);
        add(fields,text("Suggest a correction for pasuram "+verse.number,20,fg(),true));
        add(fields,text(CorrectionSubmitter.enabled()?"Review before submitting. If offline, it stays queued and retries later.":"Draft only · correction sending is not ready in this build. Your draft stays privately on this phone; devotional text does not change.",13,muted(),false));
        String[] kinds={"text","image","audio","page","idea"};
        Spinner kind=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,kinds);kind.setAdapter(adapter);add(fields,kind);
        for(int i=0;i<kinds.length;i++)if(kinds[i].equals(savedDraft.optString("kind","text"))){kind.setSelection(i);break;}
        TextView categoryLabel=text("Issue category (image/audio)",13,fg(),true);add(fields,categoryLabel);
        String[] imageCategories={"Wrong temple or deity","Poor quality","Wrong credit","Other"};
        String[] audioCategories={"Does not play","Wrong pasuram","Poor quality"};
        Spinner category=new Spinner(this);add(fields,category);
        final String previousCategory=savedDraft.optString("category","");
        kind.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> parent){}
            public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){
                String selected=kinds[position];boolean required=selected.equals("image")||selected.equals("audio");
                category.setVisibility(required?View.VISIBLE:View.GONE);categoryLabel.setVisibility(required?View.VISIBLE:View.GONE);
                if(required){String[] options=selected.equals("image")?imageCategories:audioCategories;
                    category.setAdapter(new ArrayAdapter<>(MainActivity.this,android.R.layout.simple_spinner_dropdown_item,options));
                    for(int i=0;i<options.length;i++)if(options[i].equals(previousCategory)){category.setSelection(i);break;}
                }
            }
        });
        EditText screen=correctionInput("Screen name or route (page reports)", savedDraft.optString("page","app:/reader/"+verse.number),300,fields);
        EditText correctedTamil=correctionInput("Correct Tamil lines (if relevant)",savedDraft.optString("ta",""),0,fields);
        EditText correctedLatin=correctionInput("Correct transliteration (if relevant)",savedDraft.optString("en",""),0,fields);
        EditText note=correctionInput("Explain the issue (max 2000 characters)",savedDraft.optString("note",""),2000,fields);
        EditText source=correctionInput("Image source or credit (required for an attachment)",savedDraft.optString("source",""),500,fields);
        EditText name=correctionInput("Your name (optional)",savedDraft.optString("name",""),40,fields);
        add(fields,button("Choose image (JPEG, PNG or WebP)",()->{
            Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT);picker.setType("image/*");picker.addCategory(Intent.CATEGORY_OPENABLE);
            picker.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/jpeg","image/png","image/webp"});startActivityForResult(picker,PICK_CORRECTION_IMAGE);
        },false));
        correctionImageStatus=text(correctionImage==null?"No image selected":"Image saved in draft",12,muted(),false);add(fields,correctionImageStatus);
        ScrollView scroll=new ScrollView(this);scroll.addView(fields);
        AlertDialog dialog=new AlertDialog.Builder(this).setView(scroll).setNegativeButton("Close",(d,w)->{})
            .setPositiveButton(CorrectionSubmitter.enabled()?"Submit correction":"Save draft",null).create();
        final boolean[] submitted={false};
        final boolean[] draftSaved={false};
        dialog.setOnDismissListener(d->{if(!submitted[0]&&!draftSaved[0]){
                if(!saveCorrectionDraft(verse.number,kind,category,screen,correctedTamil,correctedLatin,note,source,name))
                    Toast.makeText(this,"Draft could not be saved",Toast.LENGTH_LONG).show();
            }
            correctionImage=null;correctionImageStatus=null;});
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!CorrectionSubmitter.enabled()){
                if(saveCorrectionDraft(verse.number,kind,category,screen,correctedTamil,correctedLatin,note,source,name)){
                    draftSaved[0]=true;dialog.dismiss();Toast.makeText(this,"Draft saved privately",Toast.LENGTH_SHORT).show();
                }else Toast.makeText(this,"Draft could not be saved",Toast.LENGTH_LONG).show();
                return;
            }
            try{
                String detail=value(note), imageSource=value(source), selectedKind=kind.getSelectedItem().toString();
                if(selectedKind.equals("text")&&value(correctedTamil).isEmpty()&&value(correctedLatin).isEmpty()){
                    correctedTamil.setError("Enter a corrected Tamil line or transliteration");return;
                }
                if(detail.isEmpty()&&value(correctedTamil).isEmpty()&&value(correctedLatin).isEmpty()){
                    note.setError("Explain the issue or enter corrected lines");return;
                }
                if(selectedKind.equals("page")&&value(screen).isEmpty()){screen.setError("Name the screen");return;}
                if(selectedKind.equals("image")&&correctionImage==null&&detail.isEmpty()){note.setError("Explain which image needs fixing");return;}
                if(correctionImage!=null&&imageSource.isEmpty()){source.setError("An image source is required");return;}
                long ms=android.os.SystemClock.elapsedRealtime()-openedAt;
                if(ms<3000){Toast.makeText(this,"Please check your report before saving",Toast.LENGTH_SHORT).show();return;}
                JSONObject report=new JSONObject();report.put("kind",selectedKind);report.put("n",verse.number);
                report.put("prab",bookName);report.put("img","");
                report.put("what",selectedKind.equals("image")||selectedKind.equals("audio")?category.getSelectedItem().toString():"");
                if(selectedKind.equals("page"))report.put("page",value(screen));
                report.put("ta",value(correctedTamil));report.put("en",value(correctedLatin));
                report.put("was_ta",selectedKind.equals("text")?verse.tamil:"");report.put("was_en",selectedKind.equals("text")?verse.latin:"");report.put("note",detail);
                report.put("source",imageSource);report.put("name",value(name));
                if(correctionImage!=null)report.put("image",correctionImage);
                report.put("hp","");report.put("ms",ms);
                correctionQueue.append(report);CorrectionSubmitter.schedule(this);submitted[0]=true;
                correctionDrafts.delete(verse.number);dialog.dismiss();
                Toast.makeText(this,"Report saved. Submitting in the background.",Toast.LENGTH_LONG).show();
                correctionWorker.execute(()->{CorrectionSubmitter.Outcome result=CorrectionSubmitter.submitPending(this);
                    if(result.queued>0)CorrectionSubmitter.schedule(this);
                    runOnUiThread(()->{String message=result.delivered>0?"Correction submitted":result.rejected>0?"Correction needs review: "+result.error:"Offline or service unavailable. Report queued to retry.";
                        Toast.makeText(this,message,Toast.LENGTH_LONG).show();});
                });
            }catch(Exception ex){new AlertDialog.Builder(this).setMessage("Could not save the report: "+ex.getMessage()).setPositiveButton("OK",null).show();}
        }));
        dialog.show();
        dialog.getWindow().setBackgroundDrawable(shape(surface(),18));
    }
    private boolean saveCorrectionDraft(int number,Spinner kind,Spinner category,EditText screen,
                                        EditText tamil,EditText latin,EditText note,EditText source,EditText name){
        try{JSONObject draft=new JSONObject();draft.put("kind",kind.getSelectedItem().toString());
            if(category.getVisibility()==View.VISIBLE&&category.getSelectedItem()!=null)
                draft.put("category",category.getSelectedItem().toString());
            draft.put("page",value(screen));draft.put("ta",value(tamil));draft.put("en",value(latin));
            draft.put("note",value(note));draft.put("source",value(source));draft.put("name",value(name));
            if(correctionImage!=null)draft.put("image",correctionImage);
            correctionDrafts.save(number,draft);return true;
        }catch(Exception ex){android.util.Log.w("Corrections","Draft save failed",ex);return false;}
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=PICK_CORRECTION_IMAGE||resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        if(correctionImageStatus==null)return;
        try{correctionImage=encodeImage(data.getData());correctionImageStatus.setText("Image attached and resized for report");}
        catch(Exception ex){correctionImage=null;correctionImageStatus.setText("Could not attach image: "+ex.getMessage());}
    }
    private String encodeImage(Uri uri)throws Exception{
        android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        try(InputStream input=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(input,null,bounds);}
        if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("Unsupported image");
        int sample=1;while(Math.max(bounds.outWidth/sample,bounds.outHeight/sample)>1600)sample*=2;
        android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inSampleSize=sample;
        Bitmap decoded;try(InputStream input=getContentResolver().openInputStream(uri)){decoded=BitmapFactory.decodeStream(input,null,options);}
        if(decoded==null)throw new IOException("Image could not be opened");
        int width=decoded.getWidth(),height=decoded.getHeight();double scale=Math.min(1.0,1600.0/Math.max(width,height));
        Bitmap resized=scale<1.0?Bitmap.createScaledBitmap(decoded,(int)(width*scale),(int)(height*scale),true):decoded;
        if(resized!=decoded)decoded.recycle();
        ByteArrayOutputStream out=new ByteArrayOutputStream();resized.compress(Bitmap.CompressFormat.JPEG,82,out);resized.recycle();
        if(out.size()>1500000)throw new IOException("Image remains above 1.5 MB after resizing. Choose a smaller image.");
        return "data:image/jpeg;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);
    }
    private void showCorrectionReports(){page="Corrections";start("Correction reports","Private queue on this device","Home");
        try{JSONArray queue=correctionQueue.read();
            if(queue.length()==0){add(card(body),text("No pending reports. Submitted reports leave this device's queue.",13,muted(),false));return;}
            add(card(body),text(queue.length()+" report(s) waiting or needing review",16,fg(),true));
            add(card(body),button("Retry pending reports",()->{
                correctionWorker.execute(()->{CorrectionSubmitter.Outcome outcome=CorrectionSubmitter.submitPending(this);
                    if(outcome.queued>0)CorrectionSubmitter.schedule(this);
                    runOnUiThread(()->{Toast.makeText(this,outcome.error.isEmpty()?"Submission check finished":outcome.error,Toast.LENGTH_LONG).show();showCorrectionReports();});
                });
            },true));
            for(int i=0;i<queue.length();i++){
                JSONObject entry=queue.getJSONObject(i),report=entry.getJSONObject("payload");LinearLayout c=card(body);
                add(c,text(report.optString("kind")+" · pasuram "+report.optInt("n"),16,fg(),true));
                add(c,text("needs-review".equals(entry.optString("status"))?"Needs review: "+entry.optString("error"):"Pending network submission",12,muted(),false));
                if("needs-review".equals(entry.optString("status"))){String id=entry.optString("id");
                    add(c,button("Delete rejected report",()->new AlertDialog.Builder(this).setMessage("Delete this rejected report from the device?")
                        .setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{try{correctionQueue.remove(id);showCorrectionReports();}
                            catch(Exception ex){Toast.makeText(this,"Could not delete report",Toast.LENGTH_LONG).show();}}).show(),false));}
            }
        }catch(Exception ex){add(card(body),text("Could not read reports on this device.",13,muted(),false));}
    }
    private void showSettings(){
        page="Settings";start("Settings","Reading, opening, audio and updates","Settings");
        LinearLayout library=card(body);add(library,text("YOUR LIBRARY",13,ac(),true));
        add(library,button("My 4,000 journey · "+readCount()+" marked",this::showJourney,false));
        add(library,button("☆ Saved pasurams",this::showSaved,false));
        add(library,button("Reports",this::showCorrectionReports,false));
        add(library,button(correctionMode?"Exit correction mode":"Correction mode",()->{
            if(correctionMode)exitCorrectionMode();else enterCorrectionMode();
        },correctionMode));
        LinearLayout update=card(body);add(update,text("CONTENT STATUS",13,ac(),true));
        if(ContentUpdates.configured()){
            try{int version=contentUpdates.activeVersion();
                add(update,text(version>0?"Reading text · verified update "+version:"Reading text · offline edition",13,fg(),true));
            }catch(Exception ex){android.util.Log.w("ContentUpdates","Status unavailable",ex);}
            String gate=getSharedPreferences("content-settings",MODE_PRIVATE).getString("update-required",null);
            if(gate!=null){add(update,text("App update required for new content. Saved reading text remains available.",13,fg(),false));
                add(update,button("Update app",this::showAppUpdate,false));}
        }
        add(update,button("Content settings",this::showContentSettings,false));
        LinearLayout opening=card(body);add(opening,text("OPENING",13,ac(),true));
        android.content.SharedPreferences intro=getSharedPreferences("intro",MODE_PRIVATE);
        boolean skip=intro.getBoolean("skip-future",false),muted=intro.getBoolean("music-off",false);
        add(opening,button(skip?"Opening animation: Off":"Opening animation: On",()->{
            intro.edit().putBoolean("skip-future",!skip).apply();showSettings();
        },false));
        add(opening,button(muted?"Opening music: Off":"Opening music: On",()->{
            intro.edit().putBoolean("music-off",!muted).apply();showSettings();
        },false));
        add(opening,text("Music controls only the opening song. The animation still plays silently.",11,muted(),false));
        LinearLayout audio=card(body);add(audio,text("PLAYER",13,ac(),true));
        if(audioController!=null){
            add(audio,button("Playback speed: "+String.format(java.util.Locale.ROOT,"%.2f×",audioController.speed()),()->{
                SpeedDial dial=new SpeedDial(this,audioController.speed(),audioController::speed);
                new AlertDialog.Builder(this).setTitle("Playback speed").setView(dial)
                    .setPositiveButton("Done",(d,w)->showSettings()).show();
            },false));
            add(audio,button("Open player controls",()->{
                if(audioController.track()!=null)showPlayerSheet();
                else Toast.makeText(this,"Play a pasuram first to open controls",Toast.LENGTH_SHORT).show();
            },false));
        }
        add(audio,text("Offline audio is saved per recording from the player. Repeat and A–B controls are in the player sheet.",11,muted(),false));
        LinearLayout rotation=card(body);add(rotation,text("ROTATION",13,ac(),true));
        boolean right=preferences.getBoolean("player-side-right",false);
        add(rotation,button("Landscape player: "+(right?"Right":"Left"),()->{
            preferences.edit().putBoolean("player-side-right",!right).apply();showSettings();
        },false));
        add(rotation,text("Rotate your phone for the slim side player. You can also change its side there.",11,muted(),false));
        LinearLayout reading=card(body);add(reading,text("READING",13,ac(),true));
        add(reading,button(transliteration?"Text: English transliteration":"Text: தமிழ்",()->{
            transliteration=!transliteration;preferences.edit().putBoolean("transliteration",transliteration).apply();showSettings();
        },false));
        LinearLayout size=new LinearLayout(this);add(reading,size);
        size.addView(button("A−",()->{textSize=Math.max(16,textSize-2);preferences.edit().putInt("size",textSize).apply();showSettings();},false),new LinearLayout.LayoutParams(0,dp(48),1));
        TextView current=text("Pasuram text "+textSize,12,fg(),false);current.setGravity(Gravity.CENTER);
        size.addView(current,new LinearLayout.LayoutParams(0,dp(48),2));
        size.addView(button("A+",()->{textSize=Math.min(34,textSize+2);preferences.edit().putInt("size",textSize).apply();showSettings();},false),new LinearLayout.LayoutParams(0,dp(48),1));
        add(reading,button(elderMode?"Elder mode: On":"Elder mode: Off",()->{
            elderMode=!elderMode;preferences.edit().putBoolean("elder",elderMode).apply();showSettings();
        },false));
        LinearLayout appearance=card(body);add(appearance,text("APPEARANCE",13,ac(),true));
        LinearLayout themes=new LinearLayout(this);add(appearance,themes);
        for(int i=0;i<NAMES.length;i++){final int t=i;Button pick=button(NAMES[i],()->{
            theme=t;preferences.edit().putInt("theme",theme).apply();showSettings();
        },i==theme);pick.setTextSize(10);themes.addView(pick,new LinearLayout.LayoutParams(0,dp(48),1));}
        LinearLayout content=card(body);add(content,text("CONTENT",13,ac(),true));
        add(content,button("Content updates and image network settings",this::showContentSettings,false));
        add(content,text("These settings also remain where you normally use them.",11,muted(),false));
    }
        private void showContentSettings(){page="ContentSettings";start("Content settings","Automatic updates · every 8 hours","Home");
        add(card(body),text("Reading text checks automatically every 8 hours when the phone has data. You can also check now. The saved edition remains available offline.",14,fg(),false));
        add(card(body),button(manualCheckRunning?"Checking for text updates…":"Check for text updates",this::checkContentNow,!manualCheckRunning));
        if(manualCheckRunning)add(card(body),text("Checking the publisher and verifying the update. You can keep reading while this runs.",13,fg(),false));
        add(card(body),text("Images use full resolution on Wi-Fi and a lighter version on mobile data by default. Your choice below only changes image downloads.",14,fg(),false));
        android.content.SharedPreferences prefs=getSharedPreferences("content-settings",MODE_PRIVATE);
        String mode=prefs.getString("image-network","auto");String[] choices={"auto","wifi","light","off"};
        String[] labels={"Auto: full Wi-Fi, light mobile","Wi-Fi only","Light images on any network","No image downloads"};
        for(int i=0;i<choices.length;i++){final String choice=choices[i];add(card(body),button((mode.equals(choice)?"✓ ":"")+labels[i],()->{
            prefs.edit().putString("image-network",choice).apply();showContentSettings();
        },mode.equals(choice)));}
        add(card(body),text("Images will appear here once sourced and published. Changing this setting never removes bundled reading text.",12,muted(),false));
        if(getSharedPreferences("intro",MODE_PRIVATE).getBoolean("skip-future",false))
            add(card(body),button("Show opening again",()->{
                getSharedPreferences("intro",MODE_PRIVATE).edit().putBoolean("skip-future",false).apply();
                Toast.makeText(this,"Opening restored for the next launch.",Toast.LENGTH_LONG).show();showContentSettings();
            },false));
    }
    private void checkContentNow(){
        if(manualCheckRunning)return;
        manualCheckRunning=true;showContentSettings();
        // Force bypasses only the eight-hour timer. The same signed-manifest,
        // schema, checksum and offline-fallback checks still apply.
        updateWorker.execute(()->{
            String result=null;Exception failure=null;
            try{result=contentUpdates.check(true);}
            catch(Exception ex){failure=ex;android.util.Log.w("ContentUpdates","Manual check deferred",ex);}
            final String outcome=result;final Exception error=failure;
            runOnUiThread(()->{
                manualCheckRunning=false;if(isFinishing()||isDestroyed())return;
                if(error!=null){
                    getSharedPreferences("content-settings",MODE_PRIVATE).edit().putString("last-error",error.getClass().getSimpleName()).apply();
                    Toast.makeText(this,"Couldn't check now. Saved reading text is still available.",Toast.LENGTH_LONG).show();
                }else if("updated".equals(outcome)){
                    try{JSONArray refreshed=contentUpdates.activeBooks();if(refreshed!=null){
                        int number=verses.isEmpty()?0:verses.get(Math.min(selected,verses.size()-1)).number;
                        books=refreshed;searchIndex=null;loadBook(bookIndex);selected=0;
                        for(int i=0;i<verses.size();i++)if(verses.get(i).number==number){selected=i;break;}
                    }}catch(Exception ex){android.util.Log.w("ContentUpdates","Updated index display deferred",ex);}
                    Toast.makeText(this,"Verified text update installed.",Toast.LENGTH_LONG).show();
                }else if("current".equals(outcome)){
                    Toast.makeText(this,"Text is up to date (verified update).",Toast.LENGTH_LONG).show();
                }else if("app-update-required".equals(outcome)){
                    Toast.makeText(this,"A newer app is needed for this text update.",Toast.LENGTH_LONG).show();
                }else{
                    Toast.makeText(this,"No text update was installed. Saved text is available.",Toast.LENGTH_LONG).show();
                }
                if("ContentSettings".equals(page))showContentSettings();
                else if("Home".equals(page))showHome();
            });
        });
    }
    private void showAppUpdate(){
        String value=getSharedPreferences("content-settings",MODE_PRIVATE).getString("update-required",null);
        if(value==null)return;new AlertDialog.Builder(this).setMessage("An app update is required for new content. The verified APK installer will be offered after its source, signature and download are checked.")
            .setPositiveButton("OK",null).show();
    }
    private void showSearch(){page="Search";start("Find a pasuram","Search all 4,000 · offline","Search");
        EditText input=new EditText(this);input.setTextColor(fg());input.setHintTextColor(muted());input.setSingleLine(true);input.setTextSize(16);input.setHint("Tamil, transliteration, number");pad(input,18,8,18,8);add(body,input);
        LinearLayout results=column();add(body,results);
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){
            final String query=s.toString(); final int generation=++searchGeneration;
            if(currentSearch!=null)currentSearch.cancel(true);
            results.removeAllViews();add(card(results),text("Searching the offline library…",13,muted(),false));
            mainHandler.postDelayed(()->{
                if(generation!=searchGeneration)return;
                if(query.trim().length()<2){renderSearchMatches(results,new ArrayList<>(),false,query,generation);return;}
                currentSearch=searchWorker.submit(()->{
                    try{SearchResult result=findMatches(query);runOnUiThread(()->{
                        if(generation==searchGeneration&&page.equals("Search"))renderSearchMatches(results,result.matches,result.more,query,generation);
                    });}catch(Exception error){runOnUiThread(()->{
                        if(generation==searchGeneration&&page.equals("Search")){
                            results.removeAllViews();add(card(results),text("Search is unavailable for this query.",13,muted(),false));
                        }
                    });}
                });
            },240);
        }public void afterTextChanged(Editable e){}});
        add(body,text("Results appear as you type; no full 4,000-card list on screen.",12,muted(),false));
    }
    private static final class SearchEntry {
        final int book,number,last;final String opening,name,alvar,tamilLower,latinLower;
        SearchEntry(int book,int number,int last,String opening,String name,String alvar,String tamilLower,String latinLower){
            this.book=book;this.number=number;this.last=last;this.opening=opening;this.name=name;this.alvar=alvar;
            this.tamilLower=tamilLower;this.latinLower=latinLower;
        }
    }
    private ArrayList<SearchEntry> getSearchIndex()throws Exception{
        if(searchIndex!=null)return searchIndex;
        ArrayList<SearchEntry> index=new ArrayList<>();
        for(int bi=0;bi<books.length();bi++){
            JSONObject meta=books.getJSONObject(bi);String fname=meta.getString("file");String newer=contentUpdates.readBook(fname);
            JSONObject data=new JSONObject(newer==null?readAsset("books/"+fname):newer);
            JSONArray sections=data.getJSONArray("sections");
            for(int sec=0;sec<sections.length();sec++){JSONArray ps=sections.getJSONObject(sec).getJSONArray("p");
                for(int i=0;i<ps.length();i++){JSONArray row=ps.getJSONArray(i);
                    String ta=join(row.getJSONArray(3)),en=join(row.getJSONArray(2));
                    index.add(new SearchEntry(bi,row.getInt(0),row.getInt(1),ta.split("\n",2)[0],meta.getString("name"),meta.getString("alvar"),ta.toLowerCase(Locale.ROOT),en.toLowerCase(Locale.ROOT)));
                }
            }
        }
        searchIndex=index;return index;
    }
    private static final class Match {
        final int book,number,last; final String opening,name,alvar;
        Match(int b,int n,int l,String o,String name,String alvar){book=b;number=n;last=l;opening=o;this.name=name;this.alvar=alvar;}
    }
    private static final class SearchResult {
        final ArrayList<Match> matches;final boolean more;
        SearchResult(ArrayList<Match> matches,boolean more){this.matches=matches;this.more=more;}
    }
    private SearchResult findMatches(String query)throws Exception {
        long started=android.os.SystemClock.elapsedRealtime();
        String q=query.trim().toLowerCase(Locale.ROOT);boolean numeric=q.matches("[0-9]{1,4}");int queryNumber=numeric?Integer.parseInt(q):-1;
        ArrayList<Match> matches=new ArrayList<>();boolean more=false;
        for(SearchEntry entry:getSearchIndex()){
            if(Thread.currentThread().isInterrupted())return new SearchResult(matches,false);
            if(numeric){if(queryNumber<entry.number||queryNumber>entry.last)continue;}
            else if(!entry.tamilLower.contains(q)&&!entry.latinLower.contains(q))continue;
            if(matches.size()>=20){more=true;break;}
            matches.add(new Match(entry.book,entry.number,entry.last,entry.opening,entry.name,entry.alvar));
        }
        lastSearchElapsedMs=android.os.SystemClock.elapsedRealtime()-started;android.util.Log.d("PrabandhamSearch","elapsedMs="+lastSearchElapsedMs+" queryLength="+q.length());return new SearchResult(matches,more);
    }
    private void renderSearchMatches(LinearLayout target,ArrayList<Match> matches,boolean more,String query,int generation){
        if(generation!=searchGeneration)return;target.removeAllViews();
        if(query.trim().length()<2){add(card(target),text("Enter at least two letters, or a pasuram number.",13,muted(),false));return;}
        for(Match match:matches){LinearLayout c=card(target);
            add(c,text(match.number+(match.last>match.number?"–"+match.last:"")+" · "+match.opening,16,fg(),false));
            add(c,text(match.name+" · "+match.alvar,11,muted(),false));c.setMinimumHeight(dp(72));
            c.setOnClickListener(v->{if(match.book!=bookIndex){loadBook(match.book);preferences.edit().putInt("book",match.book).apply();}
                int ix=0;for(int t=0;t<verses.size();t++)if(verses.get(t).number==match.number){ix=t;break;}showReader(ix);});
        }
        if(matches.isEmpty())add(card(target),text("No matches in the bundled 4,000.",13,muted(),false));
        if(more)add(card(target),text("Showing the first 20 matches. Add more letters to narrow the search.",12,muted(),false));
    }
    private void showSaved(){page="Saved";start("Saved pasurams","Private on this phone","Home");
        int count=0;
        try{for(int bi=0;bi<books.length();bi++){
            JSONObject meta=books.getJSONObject(bi);if(!hasBookmarkInRange(meta.getInt("start"),meta.getInt("end")))continue;
            String fname=meta.getString("file");String newer=contentUpdates.readBook(fname);
            JSONObject data=new JSONObject(newer==null?readAsset("books/"+fname):newer);
            for(int sec=0;sec<data.getJSONArray("sections").length();sec++){
                JSONArray rows=data.getJSONArray("sections").getJSONObject(sec).getJSONArray("p");
                for(int i=0;i<rows.length();i++){JSONArray row=rows.getJSONArray(i);int first=row.getInt(0);
                    if(!bookmarked(first))continue;
                    final int book=bi,number=first;LinearLayout c=card(body);
                    String opening=row.getJSONArray(3).getString(0);add(c,text(first+" · "+opening,16,fg(),false));
                    add(c,text(meta.getString("name")+" · "+meta.getString("alvar"),11,muted(),false));
                    c.setOnClickListener(v->{if(book!=bookIndex){loadBook(book);preferences.edit().putInt("book",book).apply();}
                        for(int ix=0;ix<verses.size();ix++)if(verses.get(ix).number==number){showReader(ix);return;}});
                    count++;
                }
            }
        }}catch(Exception e){throw new IllegalStateException("Saved pasuram index unavailable",e);}
        if(count==0)add(card(body),text("Nothing saved yet. Tap ☆ on a pasuram to keep it here.",13,muted(),false));
    }
    private boolean hasBookmarkInRange(int first,int last){for(int n=first;n<=last;n++)if(bookmarked(n))return true;return false;}
    private void showJourney(){page="Journey";start("My 4,000 journey","Private on this device · no streaks","Home");
        int total=readCount();LinearLayout overview=card(body);add(overview,text(total+" marks across 4,000 numbered pasurams",23,ac(),true));
        add(overview,text("Siriya and Periya Thirumadal are split into 40 and 78 numbered reading units. The closing lines remain unnumbered; recordings remain full-madal tracks.",13,muted(),false));
        try{for(int i=0;i<books.length();i++){JSONObject meta=books.getJSONObject(i);int n=0;
            for(int id=meta.getInt("start");id<=meta.getInt("end");id++)if(isRead(id))n++;
            LinearLayout c=card(body);add(c,text(meta.getString("name"),17,fg(),true));
            int bookTotal=(meta.getInt("end")-meta.getInt("start")+1);
            add(c,text(n+" of "+bookTotal+" numbered pasurams",12,muted(),false));
            final int book=i;c.setOnClickListener(v->{loadBook(book);selected=0;preferences.edit().putInt("book",book).putInt("selected",0).apply();showIndex();});
        }}catch(Exception e){throw new IllegalStateException("Journey book metadata unavailable",e);}
    }
    private void showNotice(String tab){page=tab;start(tab,"Coming after the core reader",""+tab);
        add(card(body),text("The "+tab.toLowerCase(Locale.ROOT)+" screens are coming in a later update. Reading works offline.",15,fg(),false));
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration configuration){
        super.onConfigurationChanged(configuration);
        // Keep the Activity-owned MediaPlayer alive across rotation.
        if(audioSheet!=null){audioSheet.dismiss();audioSheet=null;}
        showCurrentPage();
    }
    @Override protected void onDestroy(){if(Build.VERSION.SDK_INT>=33&&systemBackCallback!=null)
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(systemBackCallback);
        searchGeneration++;mainHandler.removeCallbacksAndMessages(null);if(currentSearch!=null)currentSearch.cancel(true);searchWorker.shutdownNow();correctionWorker.shutdownNow();updateWorker.shutdownNow();if(audioController!=null)audioController.close();super.onDestroy();}
    private String upLabel(){
        switch(page){
            case "Reader": return parentLabel(readerParent);
            case "Recite": return parentLabel(indexParent);
            default: return "Home";
        }
    }
    private String parentLabel(String parent){
        switch(parent){
            case "Books": return "All prabandhams";
            case "Recite": return bookName;
            case "Search": return "Search";
            case "Saved": return "Saved pasurams";
            case "Journey": return "My journey";
            default: return "Home";
        }
    }
    private void navigateUp(){
        switch(page){
            case "Reader":
                switch(readerParent){
                    case "Recite": showIndex();return;
                    case "Books": showBooks();return;
                    case "Search": showSearch();return;
                    case "Saved": showSaved();return;
                    case "Journey": showJourney();return;
                    default: showHome();return;
                }
            case "Recite":
                if(indexParent.equals("Journey"))showJourney();else if(indexParent.equals("Home"))showHome();else showBooks();
                return;
            case "Home":
                long now=android.os.SystemClock.elapsedRealtime();
                if(now-lastRootBackAt<2000){finish();return;}
                lastRootBackAt=now;Toast.makeText(this,"Press back again to exit",Toast.LENGTH_SHORT).show();return;
            default: showHome();
        }
    }
    @Override public void onBackPressed(){navigateUp();}
}
