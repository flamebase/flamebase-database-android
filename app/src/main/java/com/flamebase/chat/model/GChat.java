package com.flamebase.chat.model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

/**
 * Created by efraespada on 04/06/2017.
 */

public class GChat {

    @SerializedName("name")
    @Expose
    String name;

    @SerializedName("members")
    @Expose
    List<String> members;

    @SerializedName("messages")
    @Expose
    Map<String, Message> messages;

    public GChat(String name, List<String> members, Map<String, Message> messages) {
        this.name = name;
        this.members = members;
        this.messages = messages;
    }

    public List<String> getMembers() {
        return members;
    }

    public void setMembers(List<String> members) {
        this.members = members;
    }

    public Map<String, Message> getMessages() {
        return messages;
    }

    public void setMessages(Map<String, Message> messages) {
        this.messages = messages;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
