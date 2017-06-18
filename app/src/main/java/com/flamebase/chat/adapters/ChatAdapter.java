package com.flamebase.chat.adapters;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.flamebase.chat.R;
import com.flamebase.chat.model.GChat;
import com.flamebase.chat.services.ChatManager;

import java.util.Map;

/**
 * Created by efraespada on 17/06/2017.
 */

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

    @Override
    public ChatAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat, parent, false);
        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(ChatAdapter.ViewHolder holder, int position) {
        int i = 0;
        GChat gChat = null;
        for (Map.Entry<String, GChat> entry : ChatManager.getChats().entrySet()) {
            if (i == position) {
                gChat = entry.getValue();
                break;
            }
            i++;
        }

        holder.name.setText(gChat.getName());
    }

    @Override
    public int getItemCount() {
        return ChatManager.getChats().size();
    }


    static class ViewHolder extends RecyclerView.ViewHolder {

        TextView name;

        public ViewHolder(View itemView) {
            super(itemView);
            name = (TextView) itemView.findViewById(R.id.group_name);
        }
    }
}



