package com.rotor.chappy.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;
import com.google.gson.Gson;
import com.rotor.chappy.model.Chat;
import com.rotor.chappy.model.Message;
import com.rotor.chappy.model.PendingMessages;
import com.rotor.core.Rotor;

import java.util.HashMap;

public class Docker {

    private static SharedPreferences pref() {
        return Rotor.getContext().getSharedPreferences(Rotor.getContext().getPackageName(), Context.MODE_PRIVATE);
    }

    public static SharedPreferences.Editor editor() {
        return pref().edit();
    }

    public static void addPendingMessage(Chat chat, String messageId, Message message) {
        if (FirebaseAuth.getInstance().getUid() != null) {
            String key = FirebaseAuth.getInstance().getUid() + chat.getId();
            String stored = pref().getString(key, null);
            if (stored != null) {
                Gson gson = new Gson();
                PendingMessages pendingMessages = gson.fromJson(stored, PendingMessages.class);
                pendingMessages.getMessages().put(messageId, message);
                String toStore = gson.toJson(pendingMessages, PendingMessages.class);
                editor().putString(key, toStore).apply();
            } else {
                Gson gson = new Gson();
                PendingMessages pendingMessages = new PendingMessages();
                pendingMessages.getMessages().put(messageId, message);
                String toStore = gson.toJson(pendingMessages, PendingMessages.class);
                editor().putString(key, toStore).apply();
            }
        }
    }

    public static void removePendingMessage(Chat chat, String messageId) {
        if (FirebaseAuth.getInstance().getUid() != null) {
            String key = FirebaseAuth.getInstance().getUid() + chat.getId();
            String stored = pref().getString(key, null);
            if (stored != null) {
                Gson gson = new Gson();
                PendingMessages pendingMessages = gson.fromJson(stored, PendingMessages.class);
                ((HashMap<String,Message>)pendingMessages.getMessages()).remove(messageId);
                String toStore = gson.toJson(pendingMessages, PendingMessages.class);
                editor().putString(key, toStore).apply();
            }
        }
    }

    public static PendingMessages getPendingMessage(Chat chat) {
        if (FirebaseAuth.getInstance().getUid() != null && chat != null) {
            String key = FirebaseAuth.getInstance().getUid() + chat.getId();
            String stored = pref().getString(key, null);
            if (stored != null) {
                Gson gson = new Gson();
                return gson.fromJson(stored, PendingMessages.class);
            }
        }
        return new PendingMessages();
    }
}
