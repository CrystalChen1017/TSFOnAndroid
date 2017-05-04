package com.learn.tsfonandroid;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "cxq";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
    public void click01(View v){
        Log.i(TAG, "click01: ");
        MyTSF mytsf=new MyTSF(getAssets());
        float[] result=mytsf.getAddResult();
        for (int i=0;i<result.length;i++){
            Log.i(TAG, "click01: "+result[i] );
        }

    }
}
