package com.rotor.database

import android.os.Handler
import android.util.Log
import com.efraespada.jsondiff.JSONDiff
import com.google.gson.Gson
import com.rotor.core.Builder
import com.rotor.core.NetworkUtil
import com.rotor.core.Rotor
import com.rotor.core.interfaces.BuilderFace
import com.rotor.core.interfaces.RScreen
import com.rotor.database.abstr.Reference
import com.rotor.database.interfaces.QueryCallback
import com.rotor.database.models.KReference
import com.rotor.database.models.PrimaryReferece
import com.rotor.database.models.PrimaryReferece.Companion.ACTION_NEW_OBJECT
import com.rotor.database.models.PrimaryReferece.Companion.ACTION_REFERENCE_REMOVED
import com.rotor.database.models.PrimaryReferece.Companion.EMPTY_OBJECT
import com.rotor.database.models.PrimaryReferece.Companion.NULL
import com.rotor.database.models.PrimaryReferece.Companion.OS
import com.rotor.database.models.PrimaryReferece.Companion.PATH
import com.rotor.database.request.*
import com.rotor.database.utils.BackgroundHandler
import com.rotor.database.utils.ReferenceUtils
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.apache.commons.lang3.StringEscapeUtils
import org.jetbrains.anko.doAsync
import org.json.JSONObject
import java.util.*
import kotlin.collections.ArrayList


/**
 * Created by efrainespada on 12/03/2018.
 */
class Database  {

    companion object {

        private val BACKGROUND_HANDLER = "background_handler"
        private val TAG: String = Database::class.java.simpleName!!

        private var lastActive: RScreen ? = null

        @JvmStatic fun initialize() {
            Rotor.prepare(Builder.DATABASE, object: BuilderFace {
                override fun onResume() {
                    val status = !NetworkUtil.getConnectivityStatus(Rotor.context!!).equals(NetworkUtil.NETWORK_STATUS_NOT_CONNECTED)
                    for (screen in Rotor.screens()) {
                        if (screen.isActive) {
                            for (ref in screen.holders()) {
                                val refe = ref.value!! as KReference<*>
                                refe.loadCachedReference()
                            }
                            if (!status) {
                                screen.disconnected()
                            } else {
                                screen.connected()
                            }
                        }
                    }
                }

                override fun onPause() {
                    for (entry in Rotor.screens()) {
                        if (entry.isActive) {
                            lastActive = entry
                        }
                    }
                }

                override fun onMessageReceived(jsonObject: JSONObject) {
                    try {
                        if (jsonObject.has("data")) {
                            val data = jsonObject.get("data") as JSONObject
                            if (data.has("info") && data.has(PATH)) {
                                val info = data.getString("info")
                                val path = data.getString(PATH)
                                if (ACTION_NEW_OBJECT.equals(info)) {
                                    if (contains(path)) {
                                        val handler = Handler()
                                        handler.postDelayed({ sync(path) }, 200)
                                    }
                                } else if (ACTION_REFERENCE_REMOVED.equals(info)) {
                                    if (contains(path)) {
                                        val handler = Handler()
                                        handler.postDelayed({ removePrim(path) }, 200)
                                    }
                                }
                            } else if (data.has(PATH)) {
                                val path = data.getString(PATH)
                                if (contains(path)) {
                                    getCurrentReference(path)!!.onMessageReceived(data)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                }
            })

            var backgroundFound = false
            for (screen in Rotor.screens()) {
                if (screen is BackgroundHandler) {
                    backgroundFound = true
                    break
                }
            }
            if (!backgroundFound) {
                Rotor.screens().add(BackgroundHandler())
            }
        }

        @JvmStatic fun <T> listen(database: String, path: String, reference: Reference<T>) {
            var objectReference: KReference<T> ? = null
            var rScreen: RScreen ? = null
            for (screen in Rotor.screens()) {
                if (screen.isActive) {
                    rScreen = screen
                }
                if (rScreen != null && rScreen.hasPath(path)) {

                    /**
                     * Notifications Exception:
                     * Rotor Notifications is always active and only can control notifications objects "/notifications/.."
                     */

                    if ((rScreen::class.java.simpleName.equals("Notifications") && path.contains("/notifications/") && !(rScreen is BackgroundHandler)) ||
                            (!rScreen::class.java.simpleName.equals("Notifications") && !(rScreen is BackgroundHandler))) {
                                objectReference = screen.holders().get(path) as KReference<T>?
                                break
                    }
                }
            }

            if (objectReference == null && rScreen != null) {
                Log.d(TAG, "Creating reference: $path")

                objectReference = KReference<T>(Rotor.context!!, database, path, reference, Date().time)

                rScreen.addPath(path, objectReference)
                objectReference.loadCachedReference()

                syncWithServer(path)
            } else if (objectReference != null) {
                objectReference.loadCachedReference()
            }

        }

        @JvmStatic fun sha1(value: String) : String {
            return JSONDiff.hash(StringEscapeUtils.unescapeJava(value))
        }

        @JvmStatic fun backgroundHandler() : BackgroundHandler ? {
            for (screen in Rotor.screens()) {
                if (screen is BackgroundHandler) return screen
            }
            return null
        }

        @JvmStatic private fun syncWithServer(path: String) {
            var content = ReferenceUtils.getElement(path)
            if (content == null) {
                content = PrimaryReferece.EMPTY_OBJECT
            }
            val ref = getCurrentReference(path)
            ReferenceUtils.service(Rotor.urlServer!!).createReference(CreateListener("listen_reference", ref!!.databaseName, path, Rotor.id!!, OS, sha1(content), content.length))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            { result ->
                                result.status?.let {
                                    Log.e(TAG, result.status)
                                }
                            },
                            { error -> error.printStackTrace() }
                    )
        }

        @JvmStatic fun unlisten(path: String) {
            val ref = getCurrentReference(path)
            ref?.let {
                ReferenceUtils.service(Rotor.urlServer!!).removeListener(RemoveListener("unlisten_reference", it.databaseName, path, Rotor.id!!))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                { result ->
                                    result.status?.let {
                                        Log.e(TAG, result.status)
                                    }
                                },
                                { error -> error.printStackTrace() }
                        )
            }
        }

        @JvmStatic fun remove(path: String) {
            val ref = getCurrentReference(path)
            ReferenceUtils.service(Rotor.urlServer!!).removeReference(RemoveReference("remove_reference", ref!!.databaseName, path, Rotor.id!!))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            { result ->
                                result.status?.let {
                                    Log.e(TAG, result.status)
                                }
                            },
                            { error -> error.printStackTrace() }
                    )
        }

        @JvmStatic internal fun refreshToServer(path: String, differences: String, len: Int, clean: Boolean) {
            if (differences == PrimaryReferece.EMPTY_OBJECT) {
                return
            }

            val ref = getCurrentReference(path)

            ReferenceUtils.service(Rotor.urlServer!!).refreshToServer(UpdateToServer("update_reference", ref!!.databaseName, path, Rotor.id!!, "android", differences, len, clean))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            { result ->
                                result.status?.let {
                                    Log.e(TAG, result.status)
                                }
                            },
                            { error -> error.printStackTrace() }
                    )
        }

        @JvmStatic fun refreshFromServer(path: String, content: String) {
            if (PrimaryReferece.EMPTY_OBJECT.equals(content)) {
                Log.e(TAG, "no content: $EMPTY_OBJECT")
                return
            }

            val ref = getCurrentReference(path)

            ReferenceUtils.service(Rotor.urlServer!!).refreshFromServer(UpdateFromServer("update_reference_from", ref!!.databaseName, path, Rotor.id!!, "android", content))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            { result ->
                                result.status?.let {
                                    Log.e(TAG, result.status)
                                }
                            },
                            { error -> error.printStackTrace() }
                    )
        }

        @JvmStatic fun sync(path: String) {
            sync(path, false)
        }

        @JvmStatic fun sync(path: String, clean: Boolean) {
            doAsync {
                var found = false
                for (entry in Rotor.screens()) {
                    if (entry.isActive && entry.hasPath(path)) {
                        found = true
                        val refe = entry.holders().get(path) as KReference<*>
                        val result = refe.getDifferences(clean)
                        val diff = result[1] as String
                        val len = result[0] as Int
                        if (!EMPTY_OBJECT.equals(diff)) {
                            refreshToServer(path, diff, len, clean)
                        } else {
                            val blower = refe.getLastest()
                            val value = refe.getReferenceAsString()
                            if (value.equals(EMPTY_OBJECT) || value.equals(NULL)) {
                                blower.onCreate()
                            }
                        }
                    }
                }

                if (!found) {
                    for (entry in Rotor.screens()) {
                        if (entry.hasPath(path)) {
                            val refe = entry.holders().get(path) as KReference<*>
                            val result = refe.getDifferences(clean)
                            val diff = result[1] as String
                            val len = result[0] as Int
                            if (!EMPTY_OBJECT.equals(diff)) {
                                refreshToServer(path, diff, len, clean)
                            } else {
                                val blower = refe.getLastest()
                                val value = refe.getReferenceAsString()
                                if (value.equals(EMPTY_OBJECT) || value.equals(NULL)) {
                                    blower.onCreate()
                                }
                            }
                        }
                    }
                }
            }
        }

        @JvmStatic private fun removePrim(path: String) {
            val toRemove = ArrayList<KReference<*>>()
            for (entry in Rotor.screens()) {
                if (entry.hasPath(path)) {
                    val refe = entry.holders().get(path) as KReference<*>
                    toRemove.add(refe)
                    entry.holders().remove(path)
                }
            }
            for (reference in toRemove) {
                reference.remove()
            }
            ReferenceUtils.removeElement(path)
            Database.unlisten(path)
        }

        @JvmStatic fun <T> query(database: String, path: String, query: Any, mask: Any, callback: QueryCallback<T>, klass: Class<T>) {
            val gson = Gson();
            query(database, path, gson.toJson(query), gson.toJson(mask), callback, klass)
        }

        @JvmStatic fun <T> query(database: String, path: String, query: String, mask: String, callback: QueryCallback<T>, klass: Class<T>) {
            ReferenceUtils.service(Rotor.urlServer!!).query(Rotor.id!!, database, path, query, mask)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            { result ->
                                val gson = Gson()
                                val string = gson.toJson(result);
                                val obj: T = gson.fromJson(string, klass)
                                callback.response(obj)

                            },
                            { error ->
                                error.printStackTrace()
                            })

        }

        @JvmStatic fun getCurrentReference(path: String) : KReference<*> ? {
            // returns current active reference
            for (entry in Rotor.screens()) {
                if (entry.hasPath(path) && entry.isActive) {
                    return entry.holders().get(path) as KReference<*>
                }
            }
            // returns last active reference
            if (lastActive != null && lastActive!!.holders().containsKey(path)) {
                return lastActive!!.holders().get(path) as KReference<*>
            }

            // returns last reference (not active)
            for (entry in Rotor.screens()) {
                if (entry.hasPath(path)) {
                    return entry.holders().get(path) as KReference<*>
                }
            }
            return null
        }

        @JvmStatic fun contains(path: String) : Boolean {
            for (entry in Rotor.screens()) {
                if (entry.hasPath(path) && entry.isActive) {
                    return true
                }
            }
            if (lastActive != null) {
                return lastActive!!.hasPath(path)
            }
            return false
        }
    }

}