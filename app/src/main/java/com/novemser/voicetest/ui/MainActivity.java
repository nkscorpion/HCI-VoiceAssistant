/*******************************************************************************
 * Copyright (c) <2016> <Novemser>
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 ******************************************************************************/

package com.novemser.voicetest.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.sqlite.SQLiteDatabase;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.LexiconListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.SpeechSynthesizer;
import com.iflytek.cloud.SpeechUtility;
import com.iflytek.cloud.SynthesizerListener;
import com.iflytek.cloud.TextUnderstander;
import com.iflytek.cloud.TextUnderstanderListener;
import com.iflytek.cloud.UnderstanderResult;
import com.iflytek.cloud.ui.RecognizerDialog;
import com.iflytek.cloud.ui.RecognizerDialogListener;
import com.iflytek.cloud.util.ContactManager;
import com.loopj.android.http.AsyncHttpClient;
import com.novemser.voicetest.R;
import com.novemser.voicetest.actions.AlarmListsActivity;
import com.novemser.voicetest.actions.BaseAction;
import com.novemser.voicetest.adapters.ListMessageAdapter;
import com.novemser.voicetest.handlers.Call;
import com.novemser.voicetest.handlers.LaunchApp;
import com.novemser.voicetest.handlers.NewsHandler;
import com.novemser.voicetest.handlers.Notification;
import com.novemser.voicetest.handlers.SendMsg;
import com.novemser.voicetest.utils.ChatMessage;
import com.novemser.voicetest.utils.HttpUtils;
import com.novemser.voicetest.utils.JsonParser;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener,
        DataApi.DataListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener{
    /**
     * 展示消息的listview
     */
    private ListView mChatView;
    /**
     * 文本域
     */
    public static EditText mMsg;
    /**
     * 存储聊天消息
     */
    private List<ChatMessage> mDatas = new ArrayList<ChatMessage>();
    /**
     * 适配器
     */
    private ListMessageAdapter mAdapter;
    private Button mStartVoiceRecord;
    private SpeechSynthesizer speechSynthesizer;
    private boolean isContentContainsIntent;
    private String msg;
    private PackageManager packageManager;
    private List<ResolveInfo> resolveInfoList;
    public static SQLiteDatabase db;

    // 语音听写对象
    private SpeechRecognizer mIat;
    // 语音听写UI
    private RecognizerDialog mDialog;
    // 用HashMap存储听写结果
    private HashMap<String, String> mIatResults = new LinkedHashMap<>();

    private TextUnderstander understander;

    private Handler mHandler = new Handler() {

        public void handleMessage(android.os.Message msg) {
            ChatMessage from = (ChatMessage) msg.obj;
            mDatas.add(from);
            mAdapter.notifyDataSetChanged();
            mChatView.setSelection(mDatas.size() - 1);
            if (from.getSpannedMsg() == null)
                speechSynthesizer.startSpeaking(from.getMsg().toString(), mSynListener);
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_template);

        initView();
        //打开或创建test.db数据库
        db = openOrCreateDatabase("alarm.db", Context.MODE_PRIVATE, null);

        mAdapter = new ListMessageAdapter(this, mDatas);
        mChatView.setAdapter(mAdapter);
        packageManager = getPackageManager();

        // 设置全局Context
        BaseAction.context = getApplicationContext();

        SpeechUtility.createUtility(getApplicationContext(), SpeechConstant.APPID + "=573d5744");
        //1.创建RecognizerDialog对象
        mDialog = new RecognizerDialog(this, null);
        mIat = SpeechRecognizer.createRecognizer(getApplicationContext(), null);
        //2.设置accent、 language等参数
        mDialog.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
        mDialog.setParameter(SpeechConstant.ACCENT, "mandarin");

        //若要将UI控件用于语义理解，必须添加以下参数设置，设置之后onResult回调返回将是语义理解
        //结果

//         mDialog.setParameter("asr_sch", "1");
//         mDialog.setParameter("nlp_version", "2.0");
        //3.设置回调接口
        mDialog.setListener(new RecognizerDialogListener() {
            @Override
            public void onResult(RecognizerResult recognizerResult, boolean b) {
                Log.d("VoiceResult", recognizerResult.getResultString());
                printResult(recognizerResult);
            }

            @Override
            public void onError(SpeechError speechError) {

            }
        });

        // 4.设置语音按钮
        mStartVoiceRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //显示dialog，接收语音输入
                mDialog.show();
            }
        });

        // 初始化TTS功能
        initTTS();

        // 5.初始化语义理解器
        understander = TextUnderstander.createTextUnderstander(this, null);

        // 6.上传联系人姓名列表
        SharedPreferences sharedPreferences = getSharedPreferences("user", MODE_PRIVATE);

        if (!sharedPreferences.getBoolean("isContactUploaded", false)) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            ContactManager manager = ContactManager.createManager(this, contactListener);
            manager.asyncQueryAllContactsName();
            editor.putBoolean("isContactUploaded", true);
            editor.apply();
        }

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(getString(R.string.title_toolbar));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            toolbar.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        }
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        new Thread(new Runnable() {
            /**
             * 检查系统应用程序，添加到应用列表中
             */
            @Override
            public void run() {
                //应用过滤条件
                Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
                mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                resolveInfoList = packageManager.queryIntentActivities(mainIntent, 0);
                //按报名排序
                Collections.sort(resolveInfoList, new ResolveInfo.DisplayNameComparator(packageManager));
                for (ResolveInfo res : resolveInfoList) {
                    String pkg = res.activityInfo.packageName;
                    String cls = res.activityInfo.name;
                    String name = res.loadLabel(packageManager).toString();
                    Log.d("ApplicationInfo:", "Pkg:" + pkg + "   Class:" + cls + "   Name:" + name);
                }
            }
        }).start();

        // 和手表交互数据
        mGoogleApiClient= new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Wearable.DataApi.removeListener(mGoogleApiClient, this);
        mGoogleApiClient.disconnect();
    }

    private ContactManager.ContactListener contactListener = new ContactManager.ContactListener() {
        @Override
        public void onContactQueryFinish(String s, boolean b) {
            //指定引擎类型
            mIat.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
            mIat.setParameter(SpeechConstant.TEXT_ENCODING, "utf-8");
            int ret = mIat.updateLexicon("contact", s, new LexiconListener() {
                @Override
                public void onLexiconUpdated(String s, SpeechError speechError) {
                    if (speechError != null) {
                        Log.d("contact", speechError.toString());
                    } else {
                        Log.d("contact", "上传成功！ ");
                    }
                }
            });
            if (ret != ErrorCode.SUCCESS) {
                Log.d("Contact", "上传联系人失败： " + ret);
            }
        }
    };

    private TextUnderstanderListener textUnderstanderListener = new TextUnderstanderListener() {
        AsyncHttpClient client = new AsyncHttpClient();

        @Override
        public void onResult(UnderstanderResult understanderResult) {
            Log.d("Understanding result", understanderResult.getResultString());
            HashMap map;
            map = JsonParser.parseSemanticResult(understanderResult.getResultString());
            if (map != null && map.size() > 0) {
                // 如果用户有各种类型的企图
                if (map.containsKey("operation")) {
                    isContentContainsIntent = true;

                    String op = (String) map.get("operation");
                    // 发短信
                    if (op.equals("SEND")) {
                        SendMsg sendMsg = new SendMsg();
                        sendMsg.doCMD(map, mHandler, getApplicationContext());
                    }
                    // 打电话
                    else if (op.equals("CALL")) {
                        Call call = new Call();
                        call.doCMD(map, mHandler, getApplicationContext());
                    }
                    // 设置提醒/闹钟
                    else if (op.equals("CREATE")) {
                        Notification notification = new Notification();
                        notification.doCMD(map, mHandler, getApplicationContext(), db);
                    }
                    // 打开应用
                    else if (op.equals("LAUNCH")) {
                        LaunchApp launchApp = new LaunchApp();
                        launchApp.doCMD(map, getApplicationContext(), packageManager, resolveInfoList);
                    }
                }
                // 如果用户没有各种被讯飞拦截的企图
                else {
                    // 如果想看通知
                    if (msg.contains("通知")) {
                        // TODO:Implementation
                        if (msg.contains("软件")) {
                            NewsHandler.doCMD("http://139.129.34.152:8000/jsondata/sse/10", client, mHandler);
                        }
                        if (msg.contains("电信")) {
                            NewsHandler.doCMD("http://139.129.34.152:8000/jsondata/see/10", client, mHandler);
                        }
                        if (msg.contains("经管")) {
                            NewsHandler.doCMD("http://139.129.34.152:8000/jsondata/sem/10", client, mHandler);
                        }
                        if (msg.contains("设计") || msg.contains("创意")) {
                            NewsHandler.doCMD("http://139.129.34.152:8000/jsondata/tjdi/10", client, mHandler);
                        }
                        if (msg.contains("土木")) {
                            NewsHandler.doCMD("http://139.129.34.152:8000/jsondata/clivileng/10", client, mHandler);
                        }
                        if (msg.contains("汽车")) {

                        }
                        if (msg.contains("交通")) {
                            NewsHandler.doCMD("http://139.129.34.152:8000/jsondata/tjjt/10", client, mHandler);
                        }
                        if (msg.contains("生命")) {
                            NewsHandler.doCMD("http://139.129.34.152:8000/jsondata/life/10", client, mHandler);
                        }
                        if (msg.contains("外国语")) {

                        }
                        if (msg.contains("材料")) {
                            NewsHandler.doCMD("http://139.129.34.152:8000/jsondata/smse/10", client, mHandler);
                        }
                        if (msg.contains("环境")) {
                            NewsHandler.doCMD("http://139.129.34.152:8000/jsondata/sese/10", client, mHandler);
                        }
                        if (msg.contains("测绘") || msg.contains("地理")) {
                            NewsHandler.doCMD("http://139.129.34.152:8000/jsondata/celiang/10", client, mHandler);
                        }
                    } else {
                        // 其他交给图灵机器人处理
                        new Thread() {
                            @Override
                            public void run() {
                                ChatMessage from;
                                try {
                                    from = HttpUtils.sendMsg(msg);
                                } catch (Exception e) {
                                    from = new ChatMessage(ChatMessage.Type.INPUT, "服务器正在做俯卧撑，估计累趴了~囧");
                                }
                                Message message = Message.obtain();
                                message.obj = from;
                                mHandler.sendMessage(message);
                            }
                        }.start();
                    }
                }

            }
        }

        @Override
        public void onError(SpeechError speechError) {

        }
    };

//    private void insertDate(SQLiteDatabase database, String content, Long time) {
//        database.execSQL("insert into alarm values(null, ?, ?)", new String[] {content, String.valueOf(time)});
//    }

    private SynthesizerListener mSynListener = new SynthesizerListener() {
        @Override
        public void onSpeakBegin() {
        }

        @Override
        public void onBufferProgress(int i, int i1, int i2, String s) {

        }

        @Override
        public void onSpeakPaused() {

        }

        @Override
        public void onSpeakResumed() {

        }

        @Override
        public void onSpeakProgress(int i, int i1, int i2) {

        }

        @Override
        public void onCompleted(SpeechError speechError) {

        }

        @Override
        public void onEvent(int i, int i1, int i2, Bundle bundle) {

        }
    };

    @Override
    protected void onDestroy() {
        db.close();
        super.onDestroy();
    }

    public void sendMessage(View view) {
        msg = mMsg.getText().toString();
        if (TextUtils.isEmpty(msg)) {
            Toast.makeText(this, getResources().getText(R.string.noTextWarning), Toast.LENGTH_LONG).show();
            return;
        }

        ChatMessage to = new ChatMessage(ChatMessage.Type.OUTPUT, msg);
        to.setDate(new Date());
        mDatas.add(to);

        mAdapter.notifyDataSetChanged();
        mChatView.setSelection(mDatas.size() - 1);

        mMsg.setText("");

        // 关闭软键盘
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        // 得到InputMethodManager的实例
        if (imm.isActive()) {
            // 如果开启
            imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, InputMethodManager.HIDE_NOT_ALWAYS);
            // 关闭软键盘，开启方法相同，这个方法是切换开启与关闭状态的
        }

        understander.understandText(msg, textUnderstanderListener);
    }

    private void printResult(RecognizerResult results) {
        String text = JsonParser.parseIatResult(results.getResultString());

        String sn = null;
        // 读取json结果中的sn字段
        try {
            JSONObject resultJson = new JSONObject(results.getResultString());
            sn = resultJson.optString("sn");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        try {
            if (sn.equals("2"))
                return;
        } catch (Exception e) {
            e.printStackTrace();
        }

        mIatResults.put(sn, text);

        StringBuffer resultBuffer = new StringBuffer();
        for (String key : mIatResults.keySet()) {
            resultBuffer.append(mIatResults.get(key));
        }

        mMsg.setText(resultBuffer.toString());
        mMsg.setSelection(mMsg.length());

        // 发送消息给同小济
        sendMessage(mStartVoiceRecord);
    }

    /**
     * 创建网络mp3
     *
     * @return
     */
    public MediaPlayer createNetMp3(String url) {
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setDataSource(url);
        } catch (IllegalArgumentException e) {
            return null;
        } catch (IllegalStateException e) {
            return null;
        } catch (IOException e) {
            return null;
        }
        return mp;
    }

    private void initTTS() {
        speechSynthesizer = SpeechSynthesizer.createSynthesizer(this, null);
        speechSynthesizer.setParameter(SpeechConstant.VOICE_NAME, "xiaoqi");
        speechSynthesizer.setParameter(SpeechConstant.SPEED, "60");
        speechSynthesizer.setParameter(SpeechConstant.VOLUME, "80");
        speechSynthesizer.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
    }

    private void initView() {
        mChatView = (ListView) findViewById(R.id.id_chat_listView);
        mMsg = (EditText) findViewById(R.id.id_chat_msg);
        mStartVoiceRecord = (Button) findViewById(R.id.btn_voice_input);
        mDatas.add(new ChatMessage(ChatMessage.Type.INPUT,
                getString(R.string.intro_text)));
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem menuItem) {
        // Handle navigation view item clicks here.
        int id = menuItem.getItemId();

        if (id == R.id.nav_alarm) {
            Intent intent = new Intent(MainActivity.this, AlarmListsActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getApplicationContext().startActivity(intent);
        } else if (id == R.id.listen) {
            Intent intent = new Intent(MainActivity.this, VoiceRecActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getApplicationContext().startActivity(intent);
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private GoogleApiClient mGoogleApiClient ;

    public static EditText getmMsg() {
        return mMsg;
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Wearable.DataApi.addListener(mGoogleApiClient, this);
        Log.e("onConnected", "onConnected:" + bundle);

    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.e("onConnectionSuspended", "onConnectionSuspended:" + i );
    }

    private static final String CONTENT_NAME = "content";
    private static final String DATA_PATH = "/wear_data";

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        Log.e("From phone:", "Start");
        for (DataEvent event : dataEventBuffer) {
            if (event.getType() == DataEvent.TYPE_DELETED) {
            } else if (event.getType() == DataEvent.TYPE_CHANGED) {
                DataMap dataMap= DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                if(event.getDataItem().getUri().getPath().equals(DATA_PATH)){
                    String content=dataMap.get(CONTENT_NAME);
                    Log.e("From phone:", content);
                }

            }
        }

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.e("onConnectionFailed", connectionResult.toString());
    }
}
