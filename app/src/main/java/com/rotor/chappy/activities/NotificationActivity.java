package com.rotor.chappy.activities;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.widget.Toast;

import com.rotor.chappy.BuildConfig;
import com.rotor.chappy.ContactsListener;
import com.rotor.chappy.services.ChatManager;
import com.rotor.chappy.services.LocalData;
import com.rotor.core.Rotor;
import com.rotor.core.interfaces.StatusListener;
import com.rotor.database.Database;
import com.rotor.notifications.NotificationRouterActivity;
import com.rotor.notifications.Notifications;
import com.rotor.notifications.interfaces.Listener;
import com.rotor.notifications.model.Notification;

import org.json.JSONArray;
import org.json.JSONException;

/**
 * Created by efraespada on 23/03/2018.
 */

public class NotificationActivity extends NotificationRouterActivity {

    public static String TAG = NotificationActivity.class.getSimpleName();

    @Override
    public void onCreate() {
        LocalData.init(getApplicationContext());

        Rotor.initialize(getApplicationContext(), BuildConfig.database_url, BuildConfig.redis_url, new StatusListener() {
            @Override
            public void connected() {
                Database.initialize();
                ChatManager.splashSyncContacts(new ContactsListener() {
                    @Override
                    public void contactsReady() {
                        JSONArray array = LocalData.getLocalPaths();
                        for (int i = 0; i < array.length(); i++) {
                            try {
                                final String path = array.getString(i);
                                ChatManager.addGChat(path, new ChatManager.CreateChatListener() {
                                    @Override
                                    public void newChat() {
                                        Database.unlisten(path);
                                        LocalData.removePath(path);
                                    }
                                });
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }

                    }
                });
                Notifications.initialize(NotificationActivity.class, new Listener() {
                    @Override
                    public void opened(@NonNull String deviceId, @NonNull Notification notification) {
                        Toast.makeText(getApplicationContext(), deviceId + " opened \"" + notification.getContent().getBody() + "\"", Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void removed(@NonNull Notification notification) {

                    }
                });
            }

            @Override
            public void reconnecting() {

            }
        });
        Rotor.debug(true);
    }

    @Override
    public void notificationTouched(int action, @NonNull String id, @NonNull String room) {
        if (action == SplashActivity.ACTION_CHAT) {
            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra("path", room);
            intent.putExtra("notification", id);
            startActivity(intent);
        }
        finish();
    }

}
