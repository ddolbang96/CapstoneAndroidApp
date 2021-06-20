package com.example.capstoneandroidapp;

import android.content.Intent;
import android.os.Bundle;

import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.capstoneandroidapp.databinding.ActivityPopupBinding;

public class PopupActivity extends AppCompatActivity {

    TextView txtText;
    private int phase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_popup);

        //UI 객체생성
        txtText = (TextView)findViewById(R.id.txtText);

        //데이터 가져오기
        Intent intent = getIntent();
        String data = intent.getStringExtra("data");
        phase = intent.getIntExtra("phase", 1);
        txtText.setText(data);

/*
        // 5초 뒤에 자동으로 팝업 닫힘
        Handler handler = new Handler();
        handler.postDelayed(new Runnable(){
            @Override
            public void run() {
                Intent intent2 = new Intent(PopupActivity.this, MaskDetectorActivity.class);
                intent2.putExtra("phase", phase);
                intent2.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent2);
                //finish();
                return;
            }
        },5000);
*/
    }

    //확인 버튼 클릭
    public void mOnClose(View v){
        // MaskDetectorActivity 로 전환 + Phase 값 넘기기 -> PopActivity 닫기
        Intent intent3 = new Intent(PopupActivity.this, MaskDetectorActivity.class);
        intent3.putExtra("phase", phase);
        intent3.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent3);
        //finish();
        return;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        //바깥레이어 클릭시 안닫히게
        if(event.getAction()==MotionEvent.ACTION_OUTSIDE){
            return false;
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        //안드로이드 백버튼 막기
        return;
    }
}