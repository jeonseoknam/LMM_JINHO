package com.example.studybuddy;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.studybuddy.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MyTimerFragment extends Fragment {

    private FirebaseFirestore firestore;
    private FirebaseAuth firebaseAuth;

    private TextView timerText;         // 시간 표시 텍스트
    private ProgressBar circularProgress; // 원형 ProgressBar
    private Button deleteButton, pauseButton, registerButton, rankingButton, historyButton;

    private Handler handler = new Handler(Looper.getMainLooper());
    private int elapsedTime = 0; // 경과 시간 (단위: 초)
    private boolean isRunning = false;

    private TimerService timerService;
    private boolean isServiceBound = false;


    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TimerService.TimerBinder binder = (TimerService.TimerBinder) service;
            timerService = binder.getService();
            isServiceBound = true;



            // 서비스와 상태 동기화
            elapsedTime = timerService.getElapsedTime();
            isRunning = timerService.isRunning();
            updateTimer();
            updatePauseButton();

            // 기존 Runnable 제거 후 실행
            handler.removeCallbacks(timerRunnable);

            if (isRunning) {
                handler.post(timerRunnable);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
        }
    };

    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRunning) {
                elapsedTime++; // 1초마다 증가
                updateTimer();
                handler.postDelayed(this, 1000); // 1초마다 반복 실행
            }
        }
    };

    public static MyTimerFragment newInstance(String param1, String param2) {
        MyTimerFragment fragment = new MyTimerFragment();
        Bundle args = new Bundle();
        args.putString("param1", param1);
        args.putString("param2", param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Firestore와 FirebaseAuth 초기화
        firestore = FirebaseFirestore.getInstance();
        firebaseAuth = FirebaseAuth.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_my_timer, container, false);

        // View 초기화
        timerText = view.findViewById(R.id.timer_text);
        circularProgress = view.findViewById(R.id.circular_progress);
        deleteButton = view.findViewById(R.id.delete_button);
        pauseButton = view.findViewById(R.id.pause_button);
        registerButton = view.findViewById(R.id.register_button);
        rankingButton = view.findViewById(R.id.ranking_button);
        historyButton = view.findViewById(R.id.history_button);


        // 초기 타이머 설정
        updateTimer();

        // 버튼 동작 설정
        pauseButton.setOnClickListener(v -> toggleTimer());
        deleteButton.setOnClickListener(v -> resetTimer());

        if (isServiceBound) {
            timerService.resetTimer();
            updateUI();
        }

        //등록 버튼
        registerButton.setOnClickListener(v -> {
            String time = timerText.getText().toString();
            showInputDialog();

        });

       historyButton.setOnClickListener(v -> {
            Toast.makeText(getContext(), "나의 공부 기록 페이지로 이동합니다",Toast.LENGTH_SHORT).show();

            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new TimeListFragment())
                    .addToBackStack(null)
                    .commit();
        });

        rankingButton.setOnClickListener(v -> {
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new RankingFragment())
                    .addToBackStack(null)
                    .commit();
        });



        return view;
    }
    private void showInputDialog() {
        // 다이얼로그 레이아웃 설정
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_register_time, null);
        EditText subjectEditText = dialogView.findViewById(R.id.subject_edit_text);
        EditText memoEditText = dialogView.findViewById(R.id.memo_edit_text);

        new AlertDialog.Builder(requireContext())
                .setTitle("공부 기록 저장")
                .setView(dialogView)
                .setPositiveButton("저장", (dialog, which) -> {
                    String subjectName = subjectEditText.getText().toString().trim();
                    String memo = memoEditText.getText().toString().trim();

                    // Firestore에 데이터 저장
                    saveTimeToFirestore(timerText.getText().toString(), subjectName, memo);
                })
                .setNegativeButton("취소", null)
                .show();
    }


    private void saveTimeToFirestore(String elapsedTime, String subjectName, String memo) {
        // 현재 사용자 ID 가져오기 (익명 사용자 처리)
        String userId = firebaseAuth.getCurrentUser() != null ? firebaseAuth.getCurrentUser().getUid() : "anonymous";

        // Firestore에 저장할 데이터 준비
        Map<String, Object> studySession = new HashMap<>();
        studySession.put("user_id", userId);
        studySession.put("elapsed_time", elapsedTime);
        studySession.put("subject_name", subjectName);
        studySession.put("memo", memo);
        studySession.put("timestamp", System.currentTimeMillis());

        // Firestore에 데이터 추가
        firestore.collection("study_sessions")
                .add(studySession)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(getContext(), "공부 기록이 저장되었습니다!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "저장 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }


    private void updateTimer() {
        // 경과 시간을 분, 초로 변환
        int minutes = elapsedTime / 60;
        int seconds = elapsedTime % 60;
        String time = String.format("%02d:%02d", minutes, seconds);
        timerText.setText(time);

        // ProgressBar 업데이트 (60분이 기준)
        int progress = (int) (((double) elapsedTime % 3600) / 3600 * 100); // 3600초 = 60분
        circularProgress.setProgress(progress);
    }

    private void startTimerRunnable() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isRunning) {
                    elapsedTime++;
                    updateTimer();
                    handler.postDelayed(this, 1000); // 1초마다 실행
                }
            }
        }, 1000);
    }


    private void resetTimer() {
        if (isServiceBound) {
            timerService.resetTimer();
            elapsedTime = 0;
            isRunning = false;
            handler.removeCallbacks(timerRunnable);
            updateTimer();
            pauseButton.setText("시작");
        }
    }


    @Override
    public void onStart() {
        super.onStart();
         //TimerService 바인딩
        Intent intent = new Intent(getActivity(), TimerService.class);
        requireActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (isServiceBound) {

            requireActivity().unbindService(serviceConnection);
            isServiceBound = false;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (isServiceBound && timerService != null) { // TimerService와 연결된 상태인지 확인
            // TimerService에서 상태를 가져와 동기화
            elapsedTime = timerService.getElapsedTime();
            isRunning = timerService.isRunning();

            // UI 업데이트
            updateTimer();
            updatePauseButton(); // 버튼 텍스트를 업데이트

            // 기존 Runnable 제거 후 실행
            handler.removeCallbacks(timerRunnable);

            // 타이머가 실행 중이라면 Runnable로 지속 업데이트
            if (isRunning) {
                handler.post(timerRunnable);
            }
        }
    }
    private void updatePauseButton() {
        if (isRunning) {
            pauseButton.setText("일시정지");
        } else {
            pauseButton.setText("시작");
        }
    }

    private void toggleTimer() {
        if (isServiceBound) {
            handler.removeCallbacks(timerRunnable); // 기존 Runnable 제거

            if (isRunning) {
                timerService.stopTimer();
                isRunning = false;
            } else {
                timerService.startTimer();
                isRunning = true;
                handler.post(timerRunnable); // 새로운 Runnable 추가
            }

            updateTimer();
            updatePauseButton(); // 버튼 텍스트 업데이트
        }
    }


    private void updateUI() {
        if (isServiceBound) {
            int elapsedTime = timerService.getElapsedTime();
            int minutes = elapsedTime / 60;
            int seconds = elapsedTime % 60;
            String time = String.format("%02d:%02d", minutes, seconds);
            timerText.setText(time);

            int progress = (elapsedTime % 3600) * 100 / 3600;
            circularProgress.setProgress(progress);
        }
    }

}
