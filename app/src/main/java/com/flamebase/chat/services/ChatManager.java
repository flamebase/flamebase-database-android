package com.flamebase.chat.services;

import android.support.v7.widget.RecyclerView;
import android.util.Log;
import com.flamebase.chat.model.GChat;
import com.flamebase.chat.model.Member;
import com.flamebase.database.FlamebaseDatabase;
import com.flamebase.database.interfaces.MapBlower;
import com.flamebase.database.interfaces.ObjectBlower;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by efraespada on 05/06/2017.
 */

public class ChatManager {

    private static final String TAG = ChatManager.class.getSimpleName();
    public static Map<String, GChat> map;
    public static Map<String, Member> contacts;
    public static RecyclerView.Adapter adapter;

    private ChatManager() {
        // nothing to do here ..
    }

    public static void init(RecyclerView.Adapter adapter) {
        ChatManager.adapter = adapter;
        if (map == null) {
            map = new HashMap<>();
        }
        if (contacts == null) {
            contacts = new HashMap<>();
        }
    }

    public static void syncGChat(final String path) {
        FlamebaseDatabase.createListener(path, new ObjectBlower<GChat>() {

            @Override
            public GChat updateObject() {
                if (map.containsKey(path)) {
                    return map.get(path);
                } else {
                    return null;
                }
            }

            @Override
            public void onObjectChanged(GChat ref) {
                if (map.containsKey(path)) {
                    map.get(path).setMember(ref.getMember());
                    map.get(path).setMessages(ref.getMessages());
                    map.get(path).setName(ref.getName());
                } else {
                    map.put(path, ref);
                }
                ChatManager.adapter.notifyDataSetChanged();
            }

            @Override
            public void progress(String id, int value) {
                Log.e(TAG, "loading percent for " + id + " : " + value + " %");
            }

            @Override
            public String getTag() {
                return path + "_sync";
            }

        }, GChat.class);

        LocalData.addPath(path);
    }

    public static void syncContacts(final String path) {
        FlamebaseDatabase.createListener(path, new MapBlower<Member>() {

            @Override
            public Map<String, Member> updateMap() {
                return contacts;
            }

            @Override
            public void onMapChanged(Map<String, Member> ref) {
                if (contacts != null) {
                    for (Map.Entry<String, Member> entry : ref.entrySet()) {
                        if (!contacts.containsKey(entry.getKey())) {
                            contacts.put(entry.getKey(), entry.getValue());
                        } else {
                            contacts.get(entry.getKey()).setName(entry.getValue().getName());
                            contacts.get(entry.getKey()).setOs(entry.getValue().getOs());
                            contacts.get(entry.getKey()).setToken(entry.getValue().getToken());
                            contacts.get(entry.getKey()).setEmail(entry.getValue().getEmail());
                        }
                    }
                } else {
                    contacts = ref;
                }
                ChatManager.adapter.notifyDataSetChanged();
            }

            @Override
            public void progress(String id, int value) {
                Log.e(TAG, "loading percent for " + id + " : " + value + " %");
            }

            @Override
            public String getTag() {
                return path + "_sync";
            }

        }, Member.class);
    }

    public static Map<String, Member> getContacts() {
        return contacts;
    }

    public static Map<String, GChat> getChats() {
        return map;
    }
}
