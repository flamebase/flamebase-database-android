package com.rotor.chappy.activities.splash;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.widget.Toast;

import com.rotor.chappy.BuildConfig;
import com.rotor.chappy.R;
import com.rotor.chappy.activities.home.HomeActivity;
import com.rotor.chappy.activities.login.LoginGoogleActivity;
import com.rotor.chappy.activities.notifications.NotificationActivity;
import com.rotor.chappy.model.User;
import com.rotor.chappy.services.ChatRepository;
import com.rotor.core.RAppCompatActivity;
import com.rotor.core.Rotor;
import com.rotor.core.interfaces.RStatus;
import com.rotor.database.Database;
import com.rotor.notifications.Notifications;
import com.rotor.notifications.interfaces.Listener;
import com.rotor.notifications.model.Notification;
import com.tapadoo.alerter.Alerter;

/**
 * Created by efraespada on 27/02/2018.
 */

public class SplashActivity extends RAppCompatActivity implements SplashInterface.View {

    public static String TAG = SplashActivity.class.getSimpleName();
    public static int ACTION_CHAT = 4532;
    private SplashPresenter presenter;
    private User user;
    private int LOCATION_REQUEST_CODE = 2345;

    private boolean omitMoreChanges = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        presenter = new SplashPresenter(this);

        Rotor.initialize(getApplicationContext(), BuildConfig.database_url, BuildConfig.redis_url, new RStatus() {
            @Override
            public void ready() {
                Database.initialize();
                Notifications.initialize(NotificationActivity.class, new Listener() {
                    @Override
                    public void opened(@NonNull String deviceId, @NonNull Notification notification) {
                        Toast.makeText(getApplicationContext(), deviceId + " opened \"" + notification.getContent().getBody() + "\"", Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void removed(@NonNull Notification notification) {

                    }
                });

                presenter.start();
            }
        });
        Rotor.debug(true);
    }

    @Override
    public void goLogin() {
        Intent intent = new Intent(SplashActivity.this, LoginGoogleActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    public void goMain() {
        Intent intent = new Intent(SplashActivity.this, HomeActivity.class);
        startActivity(intent);
        finish();
    }

    private void startService() {
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                presenter.goMain();
            }
        }, 3000);
    }


    @Override
    protected void onResume() {
        super.onResume();
        presenter.onResumeView();
    }

    @Override
    protected void onPause() {
        presenter.onPauseView();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)  == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)  == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startService();
        } else {
            finish();
        }
    }

    @Override
    public void onCreateUser() {
        presenter.goLogin();
    }

    @Override
    public void onUserChanged(User user) {
        this.user = user;
        if (!this.user.getToken().equals(Rotor.getId())) {
            this.user.setToken(Rotor.getId());
            presenter.syncProfile("/users/" + this.user.getUid());
        } else if (!omitMoreChanges) {
            omitMoreChanges = true;
            ChatRepository.defineUser(user);
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    startService();
                } else {
                    String[] perm = new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.CAMERA
                    };
                    ActivityCompat.requestPermissions(this, perm, LOCATION_REQUEST_CODE);
                }
            } else {
                startService();
            }
        }
    }

    @Override
    public User onUpdateUser() {
        return user;
    }

    @Override
    public void onDestroyUser() {
        user = null;
        presenter.goLogin();
    }

    @Override
    public void userProgress(int value) {
        // nothing to do here
    }

    @Override
    public void connected() {
        Alerter.clearCurrent(SplashActivity.this);
    }

    @Override
    public void disconnected() {
        Alerter.create(SplashActivity.this).setTitle("Device not connected")
                .setText("Trying to reconnect")
                .enableProgress(true)
                .disableOutsideTouch()
                .enableInfiniteDuration(true)
                .setProgressColorRes(R.color.primary)
                .show();
    }
}
