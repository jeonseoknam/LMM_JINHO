package com.example.studybuddy;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class ChatTimerFragment extends Fragment {

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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat_timer, container, false);

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

        registerButton.setOnClickListener(v -> {
            Toast.makeText(getContext(), "공부 시간을 저장합니다.", Toast.LENGTH_SHORT).show();
            // 여기에 Firestore 저장 로직을 추가하거나 새로운 기능을 추가할 수 있습니다.
        });

        rankingButton.setOnClickListener(v -> {
            Toast.makeText(getContext(), "랭킹 페이지로 이동합니다.", Toast.LENGTH_SHORT).show();
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new RankingFragment())
                    .addToBackStack(null)
                    .commit();
        });

        historyButton.setOnClickListener(v -> {
            Toast.makeText(getContext(), "나의 공부 기록 페이지로 이동합니다",Toast.LENGTH_SHORT).show();

            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new TimeListFragment())
                    .addToBackStack(null)
                    .commit();
        });

        return view;
    }

    private void updateTimer() {
        // 경과 시간을 분, 초로 변환
        int minutes = elapsedTime / 60;
        int seconds = elapsedTime % 60;
        String time = String.format("%02d:%02d", minutes, seconds);
        timerText.setText(time);

        // ProgressBar 업데이트 (60분 기준)
        int progress = (int) (((double) elapsedTime % 3600) / 3600 * 100);
        circularProgress.setProgress(progress);
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

    private void toggleTimer() {
        if (isServiceBound) {
            handler.removeCallbacks(timerRunnable);

            if (isRunning) {
                timerService.stopTimer();
                isRunning = false;
            } else {
                timerService.startTimer();
                isRunning = true;
                handler.post(timerRunnable);
            }

            updateTimer();
            updatePauseButton();
        }
    }

    private void updatePauseButton() {
        if (isRunning) {
            pauseButton.setText("일시정지");
        } else {
            pauseButton.setText("시작");
        }
    }

    @Override
    public void onStart() {
        super.onStart();
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

        if (isServiceBound && timerService != null) {
            elapsedTime = timerService.getElapsedTime();
            isRunning = timerService.isRunning();
            updateTimer();
            updatePauseButton();
            handler.removeCallbacks(timerRunnable);

            if (isRunning) {
                handler.post(timerRunnable);
            }
        }
    }
}