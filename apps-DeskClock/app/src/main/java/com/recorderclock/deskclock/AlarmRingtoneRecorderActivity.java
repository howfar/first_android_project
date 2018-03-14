/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.recorderclock.deskclock;

import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.ColorDrawable;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ListView;
import android.widget.TextView;

import com.recorderclock.deskclock.audio.AudioRecoderDialog;
import com.recorderclock.deskclock.audio.AudioRecoderUtils;

import java.io.File;
import java.util.ArrayList;

import static android.os.Environment.MEDIA_MOUNTED;

/**
 * Base activity class that changes with window's background color dynamically based on the
 * current hour.
 */
public class AlarmRingtoneRecorderActivity extends AppCompatActivity implements View.OnTouchListener, AudioRecoderUtils.OnAudioStatusUpdateListener{

    /**
     * Key used to save/restore the current background color from the saved instance state.
     */
    private static final String KEY_BACKGROUND_COLOR = "background_color";

    /**
     * Duration in millis to animate changes to the background color.
     */
    private static final long BACKGROUND_COLOR_ANIMATION_DURATION = 3000L;

    /**
     * {@link BroadcastReceiver} to update the background color whenever the system time changes.
     */
    private BroadcastReceiver mOnTimeChangedReceiver;

    /**
     * {@link ColorDrawable} used to draw the window's background.
     */
    private ColorDrawable mBackground;

    private AudioRecoderDialog recoderDialog;
    private AudioRecoderUtils recoderUtils;
    private TextView button;
    private long downT;

    private boolean sdCardExit;
    private File myRecAudioDir;// 得到Sd卡path
    private ArrayList<String> recordFiles;
    private ListView myListView1;
    private String tmpStr;
    private Uri uri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final int currentColor = Utils.getCurrentHourColor();
        final int backgroundColor = savedInstanceState == null ? currentColor
                : savedInstanceState.getInt(KEY_BACKGROUND_COLOR, currentColor);
        setBackgroundColor(backgroundColor, false /* animate */);

        setContentView(R.layout.alarm_record_activity);

        //判断SDK版本是否大于等于19，大于就让他显示，小于就要隐藏，不然低版本会多出来一个
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            setTranslucentStatus(true);
            //还有设置View的高度，因为每个型号的手机状态栏高度都不相同
        }

        button = (TextView) findViewById(android.R.id.button1);
        button.setOnTouchListener(this);

        recoderDialog = new AudioRecoderDialog(this);
        recoderDialog.setShowAlpha(0.98f);

        //recoderUtils = new AudioRecoderUtils(new File(Environment.getExternalStorageDirectory() + "/recoder.amr"));
        File recorderFile = new File(getFilePath(this, "/recoder") + "/recoder.amr");
        recoderUtils = new AudioRecoderUtils(recorderFile);

        uri = Uri.fromFile(recorderFile);

        recoderUtils.setOnAudioStatusUpdateListener(this);
    }

    @TargetApi(19)
    private void setTranslucentStatus(boolean on) {
        Window win = getWindow();
        WindowManager.LayoutParams winParams = win.getAttributes();
        final int bits = WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS;
        if (on) {
            winParams.flags |= bits;
        } else {
            winParams.flags &= ~bits;
        }
        win.setAttributes(winParams);
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        switch(event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                //Toast.makeText(getApplicationContext(),"start！！",Toast.LENGTH_SHORT).show();

                //权限请求
                recoderUtils.verifyStoragePermissions(this);

                recoderUtils.startRecord();
                downT = System.currentTimeMillis();
                recoderDialog.showAtLocation(view, Gravity.CENTER, 0, 0);
                button.setBackgroundResource(R.drawable.shape_recoder_btn_recoding);
                return true;
            case MotionEvent.ACTION_UP:
                recoderUtils.stopRecord();
                recoderDialog.dismiss();
                button.setBackgroundResource(R.drawable.shape_recoder_btn_normal);
                return true;
        }
        return false;
    }

    @Override
    public void onUpdate(double db) {
        if(null != recoderDialog) {
            int level = (int) db;
            recoderDialog.setLevel((int)db);
            recoderDialog.setTime(System.currentTimeMillis() - downT);
        }
    }


    /**
     *
     * @param context 上下文对象
     * @param dir  存储目录
     * @return
     */
    public static String getFilePath(Context context,String dir) {
        String directoryPath="";
        //判断SD卡是否可用
        if (MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) ) {
            directoryPath =context.getExternalFilesDir(dir).getAbsolutePath() ;
            // directoryPath =context.getExternalCacheDir().getAbsolutePath() ;
        }else{
            //没内存卡就存机身内存
            directoryPath=context.getFilesDir()+File.separator+dir;
            // directoryPath=context.getCacheDir()+File.separator+dir;
        }
        File file = new File(directoryPath);
        if(!file.exists()){//判断文件目录是否存在
            file.mkdirs();
        }
        Log.i("SORTAGE","filePath====>"+directoryPath);
        return directoryPath;
    }

    // 存储一个音频文件数组到list当中
    private void getRecordFiles()
    {
        recordFiles = new ArrayList<String>();
        if (sdCardExit)
        {
            File files[] = myRecAudioDir.listFiles();
            if (files != null)
            {
                for (int i = 0; i < files.length; i++)
                {
                    if (files[i].getName().indexOf(".") >= 0)
                    {
            /* 只取.amr文件 */
                        String fileS = files[i].getName().substring(
                                files[i].getName().indexOf("."));
                        if (fileS.toLowerCase().equals(".amr"))
                            recordFiles.add(files[i].getName());
                    }
                }
            }
        }
    }


    @Override
    protected void onResume() {
        super.onResume();

        // Register mOnTimeChangedReceiver to update current background color periodically.
        if (mOnTimeChangedReceiver == null) {
            final IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_TIME_TICK);
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            registerReceiver(mOnTimeChangedReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    setBackgroundColor(Utils.getCurrentHourColor(), true /* animate */);
                }
            }, filter);
        }

        // Ensure the background color is up-to-date.
        setBackgroundColor(Utils.getCurrentHourColor(), true /* animate */);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Stop updating the background color when not active.
        if (mOnTimeChangedReceiver != null) {
            unregisterReceiver(mOnTimeChangedReceiver);
            mOnTimeChangedReceiver = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // Save the background color so we can animate the change when the activity is restored.
        if (mBackground != null) {
            outState.putInt(KEY_BACKGROUND_COLOR, mBackground.getColor());
        }
    }

    /**
     * Sets the current background color to the provided value and animates the change if desired.
     *
     * @param color the ARGB value to set as the current background color
     * @param animate {@code true} if the change should be animated
     */
    protected void setBackgroundColor(int color, boolean animate) {
        if (mBackground == null) {
            mBackground = new ColorDrawable(color);
            getWindow().setBackgroundDrawable(mBackground);
        }

        if (mBackground.getColor() != color) {
            if (animate) {
                ObjectAnimator.ofObject(mBackground, "color", AnimatorUtils.ARGB_EVALUATOR, color)
                        .setDuration(BACKGROUND_COLOR_ANIMATION_DURATION)
                        .start();
            } else {
                mBackground.setColor(color);
            }
        }
    }

    @Override
    public void onBackPressed() {
        Log.i("ringtone_back_pressed", "onBackPressed");
        Log.i("ringtone_back_pressed", "uir----:"+uri);
        Intent returnIntent = new Intent();
        returnIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI,uri);
        setResult(Activity.RESULT_OK,returnIntent);
        super.onBackPressed();
    }

}
