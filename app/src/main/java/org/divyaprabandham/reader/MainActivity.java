package org.divyaprabandham.reader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.widget.Spinner;
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
    private final ExecutorService updateWorker=Executors.newSingleThreadExecutor();
    private String correctionImage=null;
    private TextView correctionImageStatus;
    private static final int PICK_CORRECTION_IMAGE=481;
    private LinearLayout root, body, bar;
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
    private boolean bookmarked(int n){return preferences.getBoolean("saved-"+n,false);}
    private void setBookmark(int n,boolean value){preferences.edit().putBoolean("saved-"+n,value).apply();}

    private boolean transliteration=false;
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
        theme=preferences.getInt("theme",0); selected=preferences.getInt("selected",0);textSize=preferences.getInt("size",21);
        elderMode=preferences.getBoolean("elder",false);
        transliteration=preferences.getBoolean("transliteration",false);
        try { JSONArray updated=contentUpdates.activeBooks();books=updated!=null?updated:new JSONArray(readAsset("books/manifest.json")); }catch(Exception e){throw new IllegalStateException("Book index missing",e);}
        bookIndex=preferences.getInt("book",2); loadBook(bookIndex); if(selected<0||selected>=verses.size())selected=0; showHome();
        // The production publisher route remains unset pending deploy and verification.
        if(ContentUpdates.configured()&&contentUpdates.checkDue()){
            getSharedPreferences("content-settings",MODE_PRIVATE).edit().putString("last-result","checking").remove("last-error").apply();showHome();
            updateWorker.execute(()->{try{String state=contentUpdates.check();
            runOnUiThread(()->{if("app-update-required".equals(state))showHome();else if("updated".equals(state))applyContentUpdate();else showHome();});
        }catch(Exception ex){getSharedPreferences("content-settings",MODE_PRIVATE).edit().putString("last-error",ex.getClass().getSimpleName()).apply();
            android.util.Log.w("ContentUpdates","Check deferred",ex);runOnUiThread(this::showHome);}});}
        else if(ContentUpdates.configured()){showHome();}
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
            int first=row.getInt(0),last=row.getInt(1);if(last>first){
                // The site's madals are long single rows that represent numbered line ranges.
                // Preserve the full source as one passage; do not invent split boundaries.
                parsed.add(new Verse(first,tamil,latin,audio));
            }else parsed.add(new Verse(first,tamil,latin,audio));
        }}
        verses.clear();verses.addAll(parsed);bookIndex=which;bookName=data.getString("name");bookAlvar=data.getString("alvar");bookTamil=data.getString("nameTa");
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
        root=column();root.setBackgroundColor(bg());
        root.setOnApplyWindowInsetsListener((v,insets)->{
            // Android 15+ enforces edge-to-edge at this target SDK; older releases lay out below bars.
            if(Build.VERSION.SDK_INT>=35)root.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());
            else root.setPadding(0,0,0,0);
            return insets;
        });
        // Navigation replaces this view within MainActivity, not the launch intro.
        // Keep the whole screen opaque at its final position; fading its root from 50%
        // caused a black flash on AMOLED every time Home or another tab was tapped.
        setContentView(root);
        if(correctionMode){Button exit=button("Exit correction mode",this::exitCorrectionMode,true);
            LinearLayout top=column();top.setGravity(Gravity.RIGHT);top.setBackground(shape(surface(),18));
            LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-2,dp(56));ep.setMargins(0,0,dp(12),0);top.addView(exit,ep);
            root.addView(top,new LinearLayout.LayoutParams(-1,dp(60)));
        }
        if(!page.equals("Home")){
            TextView up=text("‹  "+upLabel(),16,ac(),true);pad(up,20,12,20,8);
            up.setGravity(Gravity.CENTER_VERTICAL);up.setMinimumHeight(dp(48));
            up.setContentDescription("Back to "+upLabel());up.setOnClickListener(v->navigateUp());add(root,up);
        }
        TextView heading=text(title,27,fg(),true);pad(heading,20,15,20,0);add(root,heading);
        TextView sub=text(subtitle,12,muted(),false);pad(sub,20,3,20,13);add(root,sub);
        ScrollView scroll=new ScrollView(this);currentScroll=scroll;scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);body=column();scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER);bar.setBackgroundColor(bg());pad(bar,7,8,7,8);add(root,bar);
        String[] tabs={"Home","Recite","Learn","Explore","Search"};for(String tab:tabs){
            TextView link=text(tab,11,active.equals(tab)?ac():muted(),active.equals(tab));link.setGravity(Gravity.CENTER);link.setMinimumHeight(dp(elderMode?56:48));
            bar.addView(link,new LinearLayout.LayoutParams(0,dp(elderMode?58:52),1));link.setOnClickListener(v->{switch(tab){case "Home":showHome();break;case "Search":showSearch();break;case "Recite":showBooks();break;default:showNotice(tab);}});
        }
    }
    private void showHome(){page="Home";start("Divya Prabandham","Offline · 25 prabandhams","Home");
        if(ContentUpdates.configured()){
            try{int version=contentUpdates.activeVersion();LinearLayout status=card(body);
                add(status,text(version>0?"Reading text · verified update "+version:"Reading text · offline edition",13,fg(),true));
            }catch(Exception ex){android.util.Log.w("ContentUpdates","Status unavailable",ex);}
        }
        String gate=ContentUpdates.configured()?getSharedPreferences("content-settings",MODE_PRIVATE).getString("update-required",null):null;
        if(gate!=null){LinearLayout warning=card(body);add(warning,text("App update required for new content",16,ac(),true));
            add(warning,text("You can keep reading the saved offline edition. New content needs a newer app version.",13,fg(),false));
            add(warning,button("Update app",this::showAppUpdate,false));}
        add(card(body),button("Content settings",this::showContentSettings,false));
        TextView invocation=text("ஸ்ரீ:",24,ac(),true);invocation.setGravity(Gravity.CENTER);pad(invocation,0,20,0,10);add(body,invocation);
        LinearLayout box=card(body);add(box,text("CONTINUE READING",11,ac(),true));
        Verse verse=verses.get(selected);TextView line=text(verse.opening(),20,fg(),false);pad(line,0,12,0,12);add(box,line);
        add(box,text(bookName+" · "+bookAlvar+" · "+verse.number+" of 4,000",12,muted(),false));
        Button cont=button("Read pasuram",()->showReader(selected),true);pad(cont,12,4,12,4);add(box,cont);
        LinearLayout b=card(body);add(b,text(bookName,21,fg(),true));add(b,text(bookTamil+" · "+bookAlvar+" · "+verses.size()+" passages",13,muted(),false));
        add(b,button("Continue in "+bookName,this::showIndex,false));
        add(card(body),button("Browse all 25 prabandhams",this::showBooks,true));
        LinearLayout journey=card(body);add(journey,text("MY 4,000 JOURNEY",11,ac(),true));
        int read=readCount();add(journey,text(read+" of 4,000 marked read",17,fg(),true));
        add(journey,text("Your progress stays on this phone. Listening alone never marks a passage read.",12,muted(),false));
        add(journey,button("Open my journey",this::showJourney,false));
        add(card(body),button("Saved pasurams",this::showSaved,false));
        add(card(body),button("Correction reports",this::showCorrectionReports,false));
        add(card(body),button(correctionMode?"Exit correction mode":"Enter correction mode",()->{
            if(correctionMode)exitCorrectionMode();else enterCorrectionMode();},correctionMode));
        LinearLayout themes=card(body);add(themes,text("Appearance · same navigation in every theme",14,fg(),true));
        LinearLayout choices=new LinearLayout(this);pad(choices,0,10,0,0);add(themes,choices);
        for(int i=0;i<NAMES.length;i++){final int t=i;Button pick=button(NAMES[i],()->{theme=t;preferences.edit().putInt("theme",theme).apply();showHome();},i==theme);
            pick.setTextSize(10);choices.addView(pick,new LinearLayout.LayoutParams(0,dp(48),1));}
        add(card(body),button(elderMode?"Elder mode on · turn off":"Elder mode · larger text and controls",()->{
            elderMode=!elderMode;preferences.edit().putBoolean("elder",elderMode).apply();showHome();
        },elderMode));
        add(card(body),text("All 4,000 numbered pasurams are available offline. Audio playback is coming in the next milestone.",13,muted(),false));
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
    private int loadedIndexCards=0; private int searchGeneration=0;
    private ScrollView currentScroll;
    private void showIndex(){if(!page.equals("Reader")&&!page.equals("Recite"))indexParent=page;
        page="Recite";start(bookName,bookAlvar+" · "+bookTamil+" · "+verses.size()+" passages","Recite");
        add(card(body),button("All prabandhams",this::showBooks,false));
        loadedIndexCards=0;appendIndexCards();
    }
    private void appendIndexCards(){int limit=Math.min(loadedIndexCards+35,verses.size());
        for(int i=loadedIndexCards;i<limit;i++){final int index=i;Verse v=verses.get(i);LinearLayout c=card(body);
            TextView line=text(v.number+"  "+v.opening(),16,fg(),false);pad(line,0,1,0,5);add(c,line);
            add(c,text(bookAlvar+" · "+bookName+" "+(i+1),11,muted(),false));
            c.setOnClickListener(w->showReader(index));c.setMinimumHeight(dp(72));
        }
        loadedIndexCards=limit;
        if(limit<verses.size())add(card(body),button("Show next "+Math.min(35,verses.size()-limit)+" of "+verses.size(),()->{
            if(body.getChildCount()>0)body.removeViewAt(body.getChildCount()-1);int y=currentScroll.getScrollY();appendIndexCards();currentScroll.post(()->currentScroll.scrollTo(0,y));},false));
    }
    private void showReader(int index){if(!page.equals("Reader"))readerParent=page;
        selected=Math.max(0,Math.min(verses.size()-1,index));preferences.edit().putInt("selected",selected).apply();page="Reader";
        Verse v=verses.get(selected);start(bookName+" "+(selected+1),bookAlvar+" · "+(bookIndex==21?"pasurams 2673–2712":bookIndex==22?"pasurams 2713–2790":"pasuram "+v.number+" of 4,000"),"Recite");
        LinearLayout c=card(body);add(c,text(bookTamil+" · "+bookAlvar,15,ac(),true));
        TextView verse=text(transliteration?v.latin:v.tamil,textSize,fg(),false);verse.setTextSize(textSize+(elderMode?4:0));verse.setLineSpacing(dp(elderMode?12:7),elderMode?1.5f:1.28f);pad(verse,0,22,0,20);add(c,verse);
        if(bookIndex==21||bookIndex==22)add(c,text("This complete madal is one source passage covering a numbered range; individual verse boundaries are not marked in the site data.",11,muted(),false));
        if(!focusMode)
            add(c,text("Text: bundled verbatim from the site edition · recitation marks kept",11,muted(),false));
        if(correctionMode){LinearLayout mode=card(body);add(mode,text("CORRECTION MODE · reading text is unchanged",14,ac(),true));
            add(mode,text("The highlighted correction controls are separate from ordinary reading.",12,muted(),false));}
        LinearLayout reading=card(body);
        boolean focused=focusMode;
        add(reading,button(focused?"Exit focus":"Focus on this pasuram",()->{
            focusMode=!focusMode;showReader(selected);
        },focused));
        if(bookIndex==21||bookIndex==22)add(reading,text("For this long madal, marking read applies to the entire source passage, not each number in its range.",11,muted(),false));
        if(correctionMode)add(reading,button(correctionDrafts.has(v.number)?"Continue correction draft":"Suggest a correction",()->showCorrectionSheet(v),true));
        add(reading,button(bookmarked(v.number)?"★ Saved · tap to remove":"☆ Save pasuram",()->{
            setBookmark(v.number,!bookmarked(v.number));showReader(selected);
        },bookmarked(v.number)));
        add(reading,button(isRead(v.number)?"✓ Marked read · tap to undo":"Mark as read",()->{
            boolean now=!isRead(v.number);markRead(v.number,now);showReader(selected);
        },isRead(v.number)));
        LinearLayout controls=card(body);add(controls,button(transliteration?"தமிழ்":"English transliteration",()->{transliteration=!transliteration;preferences.edit().putBoolean("transliteration",transliteration).apply();showReader(selected);},false));
        LinearLayout size=new LinearLayout(this);size.setGravity(Gravity.CENTER_VERTICAL);pad(size,0,9,0,0);add(controls,size);
        TextView sizeLabel=text("Text size",13,fg(),false);size.addView(sizeLabel,new LinearLayout.LayoutParams(0,-2,1));
        size.addView(button("A−",()->{textSize=Math.max(16,textSize-2);preferences.edit().putInt("size",textSize).apply();showReader(selected);},false));
        size.addView(button("A+",()->{textSize=Math.min(34,textSize+2);preferences.edit().putInt("size",textSize).apply();showReader(selected);},false));
        LinearLayout move=card(body);LinearLayout buttons=new LinearLayout(this);add(move,buttons);
        Button previous=button("‹ Previous",()->showReader(selected-1),false);previous.setEnabled(selected>0);buttons.addView(previous,new LinearLayout.LayoutParams(0,dp(48),1));
        Button next=button("Next ›",()->showReader(selected+1),true);next.setEnabled(selected<verses.size()-1);buttons.addView(next,new LinearLayout.LayoutParams(0,dp(48),1));
        if(!focused)add(card(body),text("Audio playback is coming in the next milestone.",11,muted(),false));

    }
    private void enterCorrectionMode(){
        new AlertDialog.Builder(this).setTitle("Correction mode")
            .setMessage("Correction controls are highlighted while this mode is on. Reading text is unchanged. Reports cannot be sent from this build yet.")
            .setPositiveButton("Continue",(d,w)->{correctionMode=true;if(page.equals("Reader"))showReader(selected);else showHome();
                CorrectionIntro.show(this);})
            .setNegativeButton("Cancel",null).show();
    }
    private void exitCorrectionMode(){correctionMode=false;
        if(page.equals("Reader"))showReader(selected);else showHome();
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
        private void showContentSettings(){page="ContentSettings";start("Content settings","Automatic updates · every 8 hours","Home");
        add(card(body),text("Reading text updates automatically when the phone has data. The saved offline edition always remains available. There is no manual check button.",14,fg(),false));
        add(card(body),text("Images use full resolution on Wi-Fi and a lighter version on mobile data by default. Your choice below only changes image downloads.",14,fg(),false));
        android.content.SharedPreferences prefs=getSharedPreferences("content-settings",MODE_PRIVATE);
        String mode=prefs.getString("image-network","auto");String[] choices={"auto","wifi","light","off"};
        String[] labels={"Auto: full Wi-Fi, light mobile","Wi-Fi only","Light images on any network","No image downloads"};
        for(int i=0;i<choices.length;i++){final String choice=choices[i];add(card(body),button((mode.equals(choice)?"✓ ":"")+labels[i],()->{
            prefs.edit().putString("image-network",choice).apply();showContentSettings();
        },mode.equals(choice)));}
        add(card(body),text("Images will appear here once sourced and published. Changing this setting never removes bundled reading text.",12,muted(),false));
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
        add(overview,text("The two long madals count as one complete source passage each. Audio playback will not change this count.",13,muted(),false));
        try{for(int i=0;i<books.length();i++){JSONObject meta=books.getJSONObject(i);int n=0;
            for(int id=meta.getInt("start");id<=meta.getInt("end");id++)if(isRead(id))n++;
            LinearLayout c=card(body);add(c,text(meta.getString("name"),17,fg(),true));
            int bookTotal=(meta.getInt("end")-meta.getInt("start")+1);
            if(i==21||i==22)add(c,text(isRead(meta.getInt("start"))?"Complete madal marked read":"Madal not marked read",12,muted(),false));
            else add(c,text(n+" of "+bookTotal+" numbered pasurams",12,muted(),false));
            final int book=i;c.setOnClickListener(v->{loadBook(book);selected=0;preferences.edit().putInt("book",book).putInt("selected",0).apply();showIndex();});
        }}catch(Exception e){throw new IllegalStateException("Journey book metadata unavailable",e);}
    }
    private void showNotice(String tab){page=tab;start(tab,"Coming after the core reader",""+tab);
        add(card(body),text("The "+tab.toLowerCase(Locale.ROOT)+" screens are coming in a later update. Reading works offline.",15,fg(),false));
    }
    @Override protected void onDestroy(){if(Build.VERSION.SDK_INT>=33&&systemBackCallback!=null)
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(systemBackCallback);
        searchGeneration++;mainHandler.removeCallbacksAndMessages(null);if(currentSearch!=null)currentSearch.cancel(true);searchWorker.shutdownNow();correctionWorker.shutdownNow();updateWorker.shutdownNow();super.onDestroy();}
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
