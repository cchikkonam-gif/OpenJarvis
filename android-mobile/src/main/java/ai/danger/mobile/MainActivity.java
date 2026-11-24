package ai.danger.mobile;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private final int bg=0xff10121c, panel=0xff202334, ink=0xfff5f5fa, sub=0xffa3a8bf, accent=0xffff526d;
    private LinearLayout root, transcript;
    private ScrollView scroll;
    private EditText input;
    private TextView status;
    private Button send, settings, clear, voice, attach;
    private SharedPreferences prefs;
    private JSONArray history=new JSONArray();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private boolean busy=false;
    private TextToSpeech tts;
    private boolean ttsReady=false;
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private GradientDrawable rounded(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView label(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(ink);b.setTextSize(13);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(44));b.setBackground(rounded(panel,16));return b;}
    private EditText field(String hint,String value){EditText e=new EditText(this);e.setTextColor(ink);e.setHintTextColor(sub);e.setTextSize(15);e.setHint(hint);e.setText(value);e.setPadding(dp(14),dp(10),dp(14),dp(10));e.setBackground(rounded(panel,16));return e;}
    private void add(LinearLayout l,View v,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);l.addView(v,p);}
    private void rowItem(LinearLayout l,View v,float w){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,w);p.setMargins(dp(3),0,dp(3),0);l.addView(v,p);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
        prefs=getSharedPreferences("danger",MODE_PRIVATE);
        try{history=new JSONArray(prefs.getString("history","[]"));}catch(Exception ignored){}
        tts=new TextToSpeech(this,s->{ttsReady=s==TextToSpeech.SUCCESS;if(ttsReady)tts.setLanguage(Locale.getDefault());});
        root=column();root.setBackgroundColor(bg);root.setPadding(dp(16),dp(12),dp(16),dp(12));
        setContentView(root);
        root.setOnApplyWindowInsetsListener((v,in)->{
            if(Build.VERSION.SDK_INT<30)return in;
            android.graphics.Insets bars=in.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());
            v.setPadding(dp(16)+bars.left,dp(12)+bars.top,dp(16)+bars.right,dp(12)+bars.bottom);return in;
        });
        if(Build.VERSION.SDK_INT<30)root.setOnApplyWindowInsetsListener((v,in)->in);
        TextView title=label("DANGER AI",27,ink);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);add(root,title,0);
        status=label("OpenJarvis  /  Mobile client",13,sub);add(root,status,4);
        LinearLayout actions=new LinearLayout(this);
        settings=button("Settings");clear=button("New chat");rowItem(actions,settings,1);rowItem(actions,clear,1);add(root,actions,12);
        settings.setOnClickListener(v->configure());clear.setOnClickListener(v->new AlertDialog.Builder(this).setMessage("Clear local chat history?").setNegativeButton("Cancel",null).setPositiveButton("Clear",(d,w)->{history=new JSONArray();persist();render();}).show());
        scroll=new ScrollView(this);transcript=column();scroll.addView(transcript);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,0,1);sp.topMargin=dp(12);root.addView(scroll,sp);
        input=field("Ask anything…","");input.setMinLines(2);input.setMaxLines(5);add(root,input,6);
        LinearLayout composer=new LinearLayout(this);
        voice=button("Voice");attach=button("Text file");send=button("Send");send.setBackground(rounded(accent,16));rowItem(composer,voice,1);rowItem(composer,attach,1);rowItem(composer,send,1);add(root,composer,8);
        send.setOnClickListener(v->sendMessage());voice.setOnClickListener(v->dictate());attach.setOnClickListener(v->pickFile());
        render();
    }
    private void persist(){prefs.edit().putString("history",history.toString()).apply();}
    private void render(){
        transcript.removeAllViews();
        if(history.length()==0){TextView t=label("Your personal AI workspace\nConnect an OpenJarvis server or compatible AI endpoint in Settings.",18,sub);t.setPadding(dp(14),dp(20),dp(14),dp(20));add(transcript,t,0);}
        for(int i=0;i<history.length();i++){
            JSONObject m=history.optJSONObject(i);if(m==null)continue;
            boolean user="user".equals(m.optString("role"));
            String content=m.optString("content");
            LinearLayout bubble=column();bubble.setPadding(dp(14),dp(12),dp(14),dp(12));bubble.setBackground(rounded(user?0xff353047:panel,20));
            TextView name=label(user?"YOU":"DANGER AI",11,user?0xffffa5b3:sub);name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);add(bubble,name,0);
            TextView body=label(content,16,ink);body.setTextIsSelectable(true);add(bubble,body,6);
            if(!user){Button speak=button("Read aloud");speak.setOnClickListener(v->{if(ttsReady)tts.speak(content,TextToSpeech.QUEUE_FLUSH,null,"reply");else toast("Speech engine unavailable");});add(bubble,speak,5);}
            add(transcript,bubble,9);
        }
        scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
    }
    private void toast(String m){Toast.makeText(this,m,Toast.LENGTH_LONG).show();}
    private void configure(){
        LinearLayout form=column();form.setPadding(dp(20),dp(12),dp(20),dp(12));
        EditText url=field("Server URL (https://… or private LAN)",prefs.getString("url",""));
        url.setSingleLine(true);EditText model=field("Model ID",prefs.getString("model","qwen3:8b"));model.setSingleLine(true);
        EditText key=field("API key (optional, session only)",sessionKey);key.setSingleLine(true);key.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        add(form,label("OpenJarvis or OpenAI-compatible server. Local mode requires an external inference server. Use HTTPS for remote servers and credentials.",14,sub),0);
        add(form,url,10);add(form,model,10);add(form,key,10);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("AI connection").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        dialog.setOnShowListener(x->dialog.getButton(-1).setOnClickListener(v->{try{String u=validate(url.getText().toString().trim(),key.getText().toString());String m=model.getText().toString().trim();if(m.isEmpty())throw new Exception("Enter a model ID");prefs.edit().putString("url",u).putString("model",m).apply();sessionKey=key.getText().toString();status.setText("Endpoint configured • "+m);dialog.dismiss();}catch(Exception e){url.setError(e.getMessage());}}));dialog.show();
    }
    private String sessionKey="";
    private String validate(String value,String key)throws Exception{
        URI u=new URI(value);String host=u.getHost();String scheme=u.getScheme();
        if(host==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null)throw new Exception("Enter a valid server base URL");
        if(!"https".equalsIgnoreCase(scheme)){
            boolean local=host.equals("localhost")||host.equals("127.0.0.1")||host.matches("10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")||host.matches("192\\.168\\.\\d{1,3}\\.\\d{1,3}")||host.matches("172\\.(1[6-9]|2[0-9]|3[01])\\.\\d{1,3}\\.\\d{1,3}");
            if(!"http".equalsIgnoreCase(scheme)||!local||!key.isEmpty())throw new Exception("Use HTTPS except for keyless private LAN or localhost");
        }
        value=value.replaceAll("/+$","");return value.endsWith("/v1")?value:value+"/v1";
    }
    private void setBusy(boolean b){busy=b;send.setEnabled(!b);clear.setEnabled(!b);settings.setEnabled(!b);status.setText(b?"Thinking…":"OpenJarvis / Mobile client");}
    private void sendMessage(){
        if(busy)return;String prompt=input.getText().toString().trim();if(prompt.isEmpty())return;
        String endpoint;try{endpoint=validate(prefs.getString("url",""),sessionKey);}catch(Exception e){configure();return;}
        String model=prefs.getString("model","qwen3:8b"),key=sessionKey;
        JSONArray requestHistory;
        try{requestHistory=new JSONArray(history.toString());requestHistory.put(new JSONObject().put("role","user").put("content",prompt));}catch(Exception e){toast("Unable to prepare message");return;}
        input.setText("");setBusy(true);
        worker.execute(()->{
            HttpURLConnection c=null;
            try{
                JSONObject payload=new JSONObject().put("model",model).put("messages",requestHistory).put("stream",false).put("max_tokens",2048);
                c=(HttpURLConnection)new URL(endpoint+"/chat/completions").openConnection();
                c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(180000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");
                if(!key.isEmpty())c.setRequestProperty("Authorization","Bearer "+key);
                try(OutputStream out=c.getOutputStream()){out.write(payload.toString().getBytes(StandardCharsets.UTF_8));}
                int code=c.getResponseCode();if(code<200||code>=300)throw new IOException("Server returned HTTP "+code);
                String result;try(InputStream in=c.getInputStream()){result=read(in,4*1024*1024);}
                String answer=new JSONObject(result).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content","");
                if(answer.trim().isEmpty())throw new IOException("Server returned no text");
                requestHistory.put(new JSONObject().put("role","assistant").put("content",answer));
                runOnUiThread(()->{history=requestHistory;persist();render();setBusy(false);});
            }catch(Exception e){runOnUiThread(()->{input.setText(prompt);setBusy(false);toast("Request failed: "+e.getMessage());});}
            finally{if(c!=null)c.disconnect();}
        });
    }
    private String read(InputStream in,int max)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1){if(b.size()+n>max)throw new IOException("File or response too large");b.write(buf,0,n);}return b.toString("UTF-8");}
    private void dictate(){try{Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_PROMPT,"Speak Tamil or English");startActivityForResult(i,11);}catch(Exception e){toast("Speech recognition is not installed");}}
    private void pickFile(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("text/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,12);}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null)return;
        if(req==11){java.util.ArrayList<String> a=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(a!=null&&!a.isEmpty())input.append(a.get(0));}
        if(req==12&&data.getData()!=null){android.net.Uri uri=data.getData();worker.execute(()->{try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Cannot open file");String s=read(in,256*1024);runOnUiThread(()->input.append("\n\n"+s));}catch(Exception e){runOnUiThread(()->toast("Cannot read text file: "+e.getMessage()));}});}
    }
    @Override protected void onDestroy(){if(tts!=null){tts.stop();tts.shutdown();}worker.shutdownNow();super.onDestroy();}
}
