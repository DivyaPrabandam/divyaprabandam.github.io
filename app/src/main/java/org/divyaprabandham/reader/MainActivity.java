package org.divyaprabandham.reader;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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

/** Alpha core reader. The devotional text is copied only from the bundled site JSON. */
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
    private ArrayList<SearchEntry> searchIndex;
    private SharedPreferences preferences;
    private LinearLayout root, body, bar;
    private int theme=0, selected=0, textSize=21;
    private android.os.Handler mainHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private String page="Home";
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
        theme=preferences.getInt("theme",0); selected=preferences.getInt("selected",0);textSize=preferences.getInt("size",21);
        transliteration=preferences.getBoolean("transliteration",false);
        try { books=new JSONArray(readAsset("books/manifest.json")); }catch(Exception e){throw new IllegalStateException("Book index missing",e);}
        bookIndex=preferences.getInt("book",2); loadBook(bookIndex); if(selected<0||selected>=verses.size())selected=0; showHome();
    }
    private String readAsset(String name)throws Exception{try(InputStream in=getAssets().open(name);ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
        byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);return bytes.toString("UTF-8");}}
    private void loadBook(int which){try{
        JSONObject meta=books.getJSONObject(which);JSONObject data=new JSONObject(readAsset("books/"+meta.getString("file")));
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
    private TextView text(String str,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(str);t.setTextSize(sp);t.setTextColor(color);t.setLineSpacing(dp(3),1.12f);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private void pad(View v,int a,int b,int c,int d){v.setPadding(dp(a),dp(b),dp(c),dp(d));}
    private void add(LinearLayout into,View child){into.addView(child,new LinearLayout.LayoutParams(-1,-2));}
    private LinearLayout card(LinearLayout into){LinearLayout c=column();c.setBackground(shape(surface(),theme==1?8:18));pad(c,16,15,16,15);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(16),dp(8),dp(16),dp(4));into.addView(c,p);return c;}
    private Button button(String label,Runnable run,boolean strong){Button b=new Button(this);b.setText(label);b.setTextSize(12);b.setAllCaps(false);b.setMinimumHeight(dp(48));b.setTextColor(strong?bg():ac());b.setBackground(shape(strong?ac():surface(),theme==1?7:23));b.setOnClickListener(v->run.run());return b;}
    private void start(String title,String subtitle,String active){
        getWindow().setStatusBarColor(bg());getWindow().setNavigationBarColor(bg());
        getWindow().getDecorView().setSystemUiVisibility(theme==1||theme==2?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0);
        root=column();root.setBackgroundColor(bg());
        root.setOnApplyWindowInsetsListener((v,insets)->{
            // Handle forced edge-to-edge at target 37, without double-padding older system layouts.
            boolean laidOutEdgeToEdge=(getWindow().getDecorView().getSystemUiVisibility()&View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)!=0 || Build.VERSION.SDK_INT>=35;
            root.setPadding(0,laidOutEdgeToEdge?insets.getSystemWindowInsetTop():0,0,
                laidOutEdgeToEdge?insets.getSystemWindowInsetBottom():0);
            return insets.consumeSystemWindowInsets();
        });
        setContentView(root);
        TextView heading=text(title,27,fg(),true);pad(heading,20,15,20,0);add(root,heading);
        TextView sub=text(subtitle,12,muted(),false);pad(sub,20,3,20,13);add(root,sub);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);body=column();scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER);bar.setBackgroundColor(bg());pad(bar,7,8,7,8);add(root,bar);
        String[] tabs={"Home","Recite","Learn","Explore","Search"};for(String tab:tabs){
            TextView link=text(tab,11,active.equals(tab)?ac():muted(),active.equals(tab));link.setGravity(Gravity.CENTER);link.setMinimumHeight(dp(48));
            bar.addView(link,new LinearLayout.LayoutParams(0,dp(52),1));link.setOnClickListener(v->{switch(tab){case "Home":showHome();break;case "Search":showSearch();break;case "Recite":showBooks();break;default:showNotice(tab);}});
        }
    }
    private void showHome(){page="Home";start("Divya Prabandham","Offline reader · first milestone","Home");
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
        LinearLayout themes=card(body);add(themes,text("Appearance · same navigation in every theme",14,fg(),true));
        LinearLayout choices=new LinearLayout(this);pad(choices,0,10,0,0);add(themes,choices);
        for(int i=0;i<NAMES.length;i++){final int t=i;Button pick=button(NAMES[i],()->{theme=t;preferences.edit().putInt("theme",theme).apply();showHome();},i==theme);
            pick.setTextSize(10);choices.addView(pick,new LinearLayout.LayoutParams(0,dp(48),1));}
        add(card(body),text("All 4,000 numbered pasurams are bundled across 25 books. Meanings, audio, and the other approved screens are not yet implemented.",13,muted(),false));
    }
    private void showBooks(){page="Books";start("The 4,000 pasurams","25 prabandhams · offline","Recite");
        try{for(int i=0;i<books.length();i++){final int idx=i;JSONObject meta=books.getJSONObject(i);LinearLayout c=card(body);
            add(c,text(meta.getString("name"),19,fg(),true));add(c,text(meta.getString("alvar")+" · "+meta.getInt("start")+"–"+meta.getInt("end"),12,muted(),false));
            c.setMinimumHeight(dp(72));c.setOnClickListener(v->{loadBook(idx);selected=0;preferences.edit().putInt("book",idx).putInt("selected",0).apply();showIndex();});
        }}catch(Exception e){throw new IllegalStateException("Book list unreadable",e);}}
    private int loadedIndexCards=0; private int searchGeneration=0;
    private void showIndex(){page="Recite";start(bookName,bookAlvar+" · "+bookTamil+" · "+verses.size()+" passages","Recite");
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
            if(body.getChildCount()>0)body.removeViewAt(body.getChildCount()-1);appendIndexCards();},false));
    }
    private void showReader(int index){selected=Math.max(0,Math.min(verses.size()-1,index));preferences.edit().putInt("selected",selected).apply();page="Reader";
        Verse v=verses.get(selected);start(bookName+" "+(selected+1),bookAlvar+" · "+(bookIndex==21?"pasurams 2673–2712":bookIndex==22?"pasurams 2713–2790":"pasuram "+v.number+" of 4,000"),"Recite");
        LinearLayout c=card(body);add(c,text(bookTamil+" · "+bookAlvar,15,ac(),true));
        TextView verse=text(transliteration?v.latin:v.tamil,textSize,fg(),false);verse.setLineSpacing(dp(7),1.28f);pad(verse,0,22,0,20);add(c,verse);
        if(bookIndex==21||bookIndex==22)add(c,text("This complete madal is one source passage covering a numbered range; individual verse boundaries are not marked in the site data.",11,muted(),false));
        add(c,text("Text: bundled verbatim from the site edition · recitation marks kept",11,muted(),false));
        LinearLayout reading=card(body);if(bookIndex==21||bookIndex==22)add(reading,text("For this long madal, marking read applies to the entire source passage, not each number in its range.",11,muted(),false));
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
        add(card(body),text("Audio is not packaged in this alpha. Playback will come in a tested later milestone.",11,muted(),false));
    }
    private void showSearch(){page="Search";start("Find a pasuram","Search all 4,000 · offline","Search");
        EditText input=new EditText(this);input.setTextColor(fg());input.setHintTextColor(muted());input.setSingleLine(true);input.setTextSize(16);input.setHint("Tamil, transliteration, number");pad(input,18,8,18,8);add(body,input);
        LinearLayout results=column();add(body,results);
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){
            final String query=s.toString(); final int generation=++searchGeneration;
            results.removeAllViews();add(card(results),text("Searching the offline library…",13,muted(),false));
            mainHandler.postDelayed(()->{
                if(generation!=searchGeneration)return;
                if(query.trim().length()<2){renderSearchMatches(results,new ArrayList<>(),false,query,generation);return;}
                searchWorker.execute(()->{
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
            JSONObject meta=books.getJSONObject(bi);JSONObject data=new JSONObject(readAsset("books/"+meta.getString("file")));
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
        String q=query.trim().toLowerCase(Locale.ROOT);boolean numeric=q.matches("[0-9]{1,4}");int queryNumber=numeric?Integer.parseInt(q):-1;
        ArrayList<Match> matches=new ArrayList<>();boolean more=false;
        for(SearchEntry entry:getSearchIndex()){
            if(numeric){if(queryNumber<entry.number||queryNumber>entry.last)continue;}
            else if(!entry.tamilLower.contains(q)&&!entry.latinLower.contains(q))continue;
            if(matches.size()>=20){more=true;break;}
            matches.add(new Match(entry.book,entry.number,entry.last,entry.opening,entry.name,entry.alvar));
        }
        return new SearchResult(matches,more);
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
    private void showJourney(){page="Journey";start("My 4,000 journey","Private on this device · no streaks","Home");
        int total=readCount();LinearLayout overview=card(body);add(overview,text(total+" marks across 4,000 numbered pasurams",23,ac(),true));
        add(overview,text("The two long madals count as one complete passage each in this alpha. Audio playback does not change this count.",13,muted(),false));
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
        add(card(body),text("This is an early build. The approved "+tab.toLowerCase(Locale.ROOT)+" screens are not yet implemented. The reading experience remains usable offline.",15,fg(),false));
    }
    @Override protected void onDestroy(){searchGeneration++;mainHandler.removeCallbacksAndMessages(null);searchWorker.shutdownNow();super.onDestroy();}
    @Override public void onBackPressed(){if(page.equals("Reader"))showIndex();else if(page.equals("Recite"))showBooks();else showHome();}
}
