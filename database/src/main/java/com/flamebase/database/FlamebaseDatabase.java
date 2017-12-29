package com.flamebase.database;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;

import com.efraespada.stringcarelibrary.SC;
import com.flamebase.database.interfaces.Blower;
import com.flamebase.database.interfaces.ListBlower;
import com.flamebase.database.interfaces.MapBlower;
import com.flamebase.database.interfaces.ObjectBlower;
import com.flamebase.database.model.MapReference;
import com.flamebase.database.model.ObjectReference;
import com.flamebase.database.model.Reference;
import com.flamebase.database.model.TokenListener;
import com.flamebase.database.model.request.CreateListener;
import com.flamebase.database.model.request.RemoveListener;
import com.flamebase.database.model.request.UpdateFromServer;
import com.flamebase.database.model.request.UpdateToServer;
import com.flamebase.database.model.service.SyncResponse;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.messaging.RemoteMessage;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static com.flamebase.database.model.Reference.EMPTY_OBJECT;
import static com.flamebase.database.model.Reference.PATH;

/**
 * Created by efraespada on 10/06/2017.
 */

public class FlamebaseDatabase {

    private static final String TAG = FlamebaseDatabase.class.getSimpleName();

    private static TokenListener listener;
    private static Context context;
    private static String urlServer;
    private static String token;
    private static Gson gson;
    public static Boolean debug = false;
    private static final String OS = "android";

    private static HashMap<String, Reference> pathMap;

    public enum Type {
        OBJECT,
        LIST,
        MAP
    }

    private FlamebaseDatabase() {
        // nothing to do here
    }

    public static void onTokenRefresh(String token) {
        FlamebaseDatabase.token = token;
        Runnable task = new Runnable() {
            @Override
            public void run() {
                start();
            }
        };
        new Handler(Looper.getMainLooper()).post(task);
    }

    /**
     * Set initial config to createReference with flamebase server cluster
     *
     * @param context
     * @param urlServer
     */
    public static void initialize(Context context, String urlServer, final FirebaseInstanceId fii, final TokenListener listener) {
        FlamebaseDatabase.context = context;
        FlamebaseDatabase.urlServer = urlServer;
        FlamebaseDatabase.listener = listener;
        FlamebaseDatabase.gson = new Gson();
        if (FlamebaseDatabase.pathMap == null) {
            FlamebaseDatabase.pathMap = new HashMap<>();
        }
        if (FlamebaseDatabase.token == null && fii.getToken() != null) {
            FlamebaseDatabase.token = fii.getToken();
            start();
        }
    }

    private static void start() {
        SC.init(context);
        ReferenceUtils.initialize(context);
        FlamebaseDatabase.listener.databaseReady();
    }

    /**
     * debug logs
     * @param debug
     */
    public static void setDebug(boolean debug) {
        FlamebaseDatabase.debug = debug;
    }

    /**
     * creates a listener for given path
     *
     * @param path
     * @param blower
     * @param clazz
     * @param <T>
     */
    public static <T> void createListener(final String path, Blower<T> blower, Class<T> clazz) {
        if (FlamebaseDatabase.pathMap == null) {
            Log.e(TAG, "Use FlamebaseDatabase.initialize(Context context, String urlServer, String token) before create real time references");
            return;
        }

        if (FlamebaseDatabase.pathMap.containsKey(path)) {
            if (FlamebaseDatabase.debug) {
                Log.d(TAG, "Listener already added for: " + path);
            }
            return;
        }

        Type type;
        if (blower instanceof MapBlower) {
            type = Type.MAP;
        } else if (blower instanceof ListBlower) {
            type = Type.LIST;
        } else {
            type = Type.OBJECT;
        }

        switch (type) {

            case MAP:

                final MapBlower<T> mapBlower = (MapBlower<T>) blower;

                final MapReference mapReference = new MapReference<T>(context, path, mapBlower, clazz) {

                    @Override
                    public void progress(int value) {
                        mapBlower.progress(value);
                    }

                    @Override
                    public Map<String, T> updateMap() {
                        return mapBlower.updateMap();
                    }

                };

                pathMap.put(path, mapReference);

                syncWithServer(path, new Sender.FlamebaseResponse() {
                    @Override
                    public void onSuccess(JsonObject jsonObject) {
                        mapReference.serverLen = jsonObject.get("objectLen").getAsInt();
                        mapReference.queueLen = jsonObject.get("queueLen").getAsInt();
                        String respon = jsonObject.get("info").getAsString();

                        switch (respon) {

                            case "updates_sent":
                                if (FlamebaseDatabase.debug) {
                                    Log.d(TAG, respon);
                                }
                                break;

                            case "queue_ready":

                                if (FlamebaseDatabase.debug) {
                                    Log.d(TAG, respon);
                                }
                                break;

                            default:
                                if (FlamebaseDatabase.debug) {
                                    Log.d(TAG, respon);
                                }
                                break;
                        }
                    }

                    @Override
                    public void onFailure(String error) {
                        Log.e(TAG, "error: " + error);
                    }
                });

                break;

            case OBJECT:

                final ObjectBlower<T> objectBlower = (ObjectBlower<T>) blower;

                final ObjectReference objectReference = new ObjectReference<T>(context, path, objectBlower, clazz) {


                    @Override
                    public T updateObject() {
                        return objectBlower.updateObject();
                    }

                    @Override
                    public void progress(int value) {
                        objectBlower.progress(value);
                    }

                };

                pathMap.put(path, objectReference);

                syncWithServer(path, new Sender.FlamebaseResponse() {
                    @Override
                    public void onSuccess(JsonObject jsonObject) {
                        objectReference.serverLen = jsonObject.get("len").getAsInt();
                        String respon = jsonObject.get("info").getAsString();

                        switch (respon) {

                            case "listener_ready_for_refresh_client":
                                FlamebaseDatabase.refreshFromServer(path, new Sender.FlamebaseResponse() {
                                    @Override
                                    public void onSuccess(JsonObject jsonObject) {
                                        objectReference.serverLen = jsonObject.get("len").getAsInt();
                                        String respon = jsonObject.get("info").getAsString();

                                        switch (respon) {

                                            case "updates_sent":
                                                if (FlamebaseDatabase.debug) {
                                                    Log.d(TAG, respon);
                                                }
                                                break;

                                            case "queue_ready":
                                                if (FlamebaseDatabase.debug) {
                                                    Log.d(TAG, respon);
                                                }
                                                break;

                                            default:
                                                if (FlamebaseDatabase.debug) {
                                                    Log.d(TAG, respon);
                                                }
                                                break;
                                        }
                                    }

                                    @Override
                                    public void onFailure(String error) {
                                        Log.e(TAG, "Flamebase error: " + error);
                                    }
                                });
                                break;

                            case "listener_up_to_date":
                                objectReference.isSynchronized = true;
                                if (FlamebaseDatabase.debug) {
                                    Log.d(TAG, respon);
                                }
                                objectReference.loadCachedReference();
                                break;

                            case "queue_ready":
                                if (FlamebaseDatabase.debug) {
                                    Log.d(TAG, respon);
                                }
                                break;

                            default:

                                break;
                        }
                    }

                    @Override
                    public void onFailure(String error) {
                        Log.e(TAG, "error: " + error);
                    }
                });

                break;
        }
    }

    private static void syncWithServer(String path, final Sender.FlamebaseResponse callback) {
        String content = ReferenceUtils.getElement(path);
        if (content == null) {
            content = EMPTY_OBJECT;
        }
        String sha1 = ReferenceUtils.SHA1(content);

        CreateListener createListener = new CreateListener("create_listener", path, token, OS, sha1, content.length());

        Call<SyncResponse> call = ReferenceUtils.service(FlamebaseDatabase.urlServer).createReference(createListener);

        call.enqueue(new Callback<SyncResponse>() {

            @Override
            public void onResponse(Call<SyncResponse> call, Response<SyncResponse> response) {
                SyncResponse syncResponse = response.body();
                if (!syncResponse.getData().toString().equals(EMPTY_OBJECT)) {
                    callback.onSuccess(syncResponse.getData());
                } else {
                    callback.onFailure(syncResponse.getError());
                }
            }

            @Override
            public void onFailure(Call<SyncResponse> call, Throwable t) {
                if (t.getMessage() != null) {
                    callback.onFailure(t.getMessage().toString());
                } else {
                    callback.onFailure("refresh from server response error");
                }
            }
        });
    }

    public static void removeListener(final String path) {
        RemoveListener removeListener = new RemoveListener("remove_listener", path, token);

        Call<SyncResponse> call = ReferenceUtils.service(FlamebaseDatabase.urlServer).removeListener(removeListener);

        call.enqueue(new Callback<SyncResponse>() {

            @Override
            public void onResponse(Call<SyncResponse> call, Response<SyncResponse> response) {
                SyncResponse syncResponse = response.body();
                if (!syncResponse.getData().toString().equals(EMPTY_OBJECT)) {
                    ReferenceUtils.removeElement(path);
                    if (debug) {
                        Log.d(TAG, syncResponse.getData().get("info").getAsString());
                    }
                } else {
                    Log.e(TAG, syncResponse.getError());

                }
            }

            @Override
            public void onFailure(Call<SyncResponse> call, Throwable t) {
                if (t.getStackTrace() != null) {
                    Log.e(TAG, "error: " + t.getStackTrace().toString());
                } else {
                    Log.e(TAG, "error: refresh from server response error");
                }
            }
        });
    }

    private static void refreshToServer(final String path, @NonNull String differences, @NonNull Integer len, boolean clean) {
        if (!pathMap.get(path).isSynchronized) {
            /*
            if (jsonObject.has("error") && jsonObject.getString("error") != null) {
                String error = jsonObject.getString("error");
                switch (error) {

                    case "holder_not_found":
                        Log.e(TAG, error);
                        // TODO CREATE LISTENER
                        break;

                    case "data_updated_with_differences":
                        Log.e(TAG, error);
                        // TODO SEND FULL OBJECT
                        break;

                    default:
                        Log.e(TAG, error);
                        break;
                }
            }
            */


            String content = ReferenceUtils.getElement(path);
            if (content == null) {
                content = EMPTY_OBJECT;
            }
            String sha1 = ReferenceUtils.SHA1(content);

            UpdateToServer updateToServer = new UpdateToServer("update_data", path, FlamebaseDatabase.token, differences, len, clean);
            Call<SyncResponse> call = ReferenceUtils.service(FlamebaseDatabase.urlServer).refreshToServer(updateToServer);

            call.enqueue(new Callback<SyncResponse>() {

                @Override
                public void onResponse(Call<SyncResponse> call, Response<SyncResponse> response) {
                    SyncResponse syncResponse = response.body();
                    if (!syncResponse.getData().toString().equals(EMPTY_OBJECT)) {
                        if (FlamebaseDatabase.debug) {
                            Log.d(TAG, syncResponse.getData().toString());
                        }
                    } else {
                        if (FlamebaseDatabase.debug) {
                            Log.d(TAG, syncResponse.getError());
                        }
                    }
                }

                @Override
                public void onFailure(Call<SyncResponse> call, Throwable t) {
                    if (t.getMessage() != null) {
                        Log.e(TAG, t.getMessage());
                    } else {
                        Log.e(TAG, "update response error");
                    }
                }
            });
        }
    }

    public static void onMessageReceived(RemoteMessage remoteMessage) {
        try {
            String path = remoteMessage.getData().get(PATH);
            if (pathMap.containsKey(path)) {
                ((Reference) pathMap.get(path)).onMessageReceived(remoteMessage);
            } else {

            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void syncReference(String path) {
        syncReference(path, false);
    }

    /**
     * Updates {@code Map<String, Reference> pathMap} invoking {@code syncReference()} on Reference object.
     *
     * @param path
     * @param clean
     */
    public static void syncReference(String path, boolean clean) {
        if (pathMap.containsKey(path)) {
            Object[] result = pathMap.get(path).syncReference(clean);
            String diff = (String) result[1];
            Integer len = (Integer) result[0];
            refreshToServer(path, diff, len, clean);
        }
    }

    public static void refreshFromServer(String path, final Sender.FlamebaseResponse callback) {

        String content = ReferenceUtils.getElement(path);
        if (content == null) {
            content = EMPTY_OBJECT;
        }
        String sha1 = ReferenceUtils.SHA1(content);

        UpdateFromServer updateFromServer = new UpdateFromServer("get_updates", path, content, content.length(), token, "android");
        Call<SyncResponse> call = ReferenceUtils.service(FlamebaseDatabase.urlServer).refreshFromServer(updateFromServer);

        call.enqueue(new Callback<SyncResponse>() {

            @Override
            public void onResponse(Call<SyncResponse> call, Response<SyncResponse> response) {
                SyncResponse syncResponse = response.body();
                if (!syncResponse.getData().toString().equals(EMPTY_OBJECT)) {
                    callback.onSuccess(syncResponse.getData());
                } else {
                    callback.onFailure(syncResponse.getError());
                }
            }

            @Override
            public void onFailure(Call<SyncResponse> call, Throwable t) {
                if (t.getStackTrace() != null) {
                    callback.onFailure(t.getStackTrace().toString());
                } else {
                    callback.onFailure("refresh from server response error");
                }
            }
        });
    }
}
