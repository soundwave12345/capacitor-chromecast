package com.caprockapps.plugins.chromecast;

import java.io.IOException;
import java.util.ArrayList;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaLoadRequestData;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.MediaSeekOptions;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.MediaQueue;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.cast.framework.media.RemoteMediaClient.MediaChannelResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import android.app.Activity;

import androidx.annotation.NonNull;

/*
 * All of the Chromecast session specific functions should start here.
 */
public class ChromecastSession {
    private static final String TAG = "Chromecast";
    /** The current context. */
    private Activity activity;
    /** A registered callback that we will un-register and re-register each time the session changes. */
    private Listener clientListener;
    /** The current session. */
    private CastSession session;
    /** The current session's client for controlling playback. */
    private RemoteMediaClient client;
    /** Indicates whether we are requesting media or not. **/
    private boolean requestingMedia = false;
    /** Handles and used to trigger queue updates. **/
    private MediaQueueController mediaQueueCallback;
    /** Stores a callback that should be called when the queue is loaded. **/
    private Runnable queueReloadCallback;
    /** Stores a callback that should be called when the queue status is updated. **/
    private Runnable queueStatusUpdatedCallback;

    /**
     * ChromecastSession constructor.
     * @param act the current activity
     * @param listener callback that will notify of certain events
     */
    public ChromecastSession(Activity act, @NonNull Listener listener) {
        this.activity = act;
        this.clientListener = listener;
    }

    /**
     * Sets the session object the will be used for other commands in this class.
     * @param castSession the session to use
     */
    public void setSession(final CastSession castSession) {
        activity.runOnUiThread(new Runnable() {
            public void run() {
                if (castSession == null) {
                    client = null;
                    return;
                }
                if (castSession.equals(session)) {
                    // Don't client and listeners if session did not change
                    return;
                }
                session = castSession;
                client = session.getRemoteMediaClient();
                if (client == null) {
                    return;
                }
                setupQueue();
                client.registerCallback(new RemoteMediaClient.Callback() {
                    private Integer prevItemId;
                    @Override
                    public void onStatusUpdated() {
                        final MediaStatus status = client.getMediaStatus();
                        if (requestingMedia
                                || queueStatusUpdatedCallback != null
                                || queueReloadCallback != null) {
                            return;
                        }

                        if (status != null) {
                            if (prevItemId == null) {
                                prevItemId = status.getCurrentItemId();
                            }
                            boolean shouldSkipUpdate = false;
                            if (status.getPlayerState() == MediaStatus.PLAYER_STATE_LOADING) {
                                // It appears the queue has advanced to the next item
                                // So send an update to indicate the previous has finished
                                clientListener.onMediaUpdate(createMediaObject(MediaStatus.IDLE_REASON_FINISHED));
                                shouldSkipUpdate = true;
                            }
                            if (prevItemId != null && prevItemId != status.getCurrentItemId() && mediaQueueCallback.getCurrentItemIndex() != -1) {
                                // The currentItem has changed, so update the current queue items
                                setQueueReloadCallback(new Runnable() {
                                    @Override
                                    public void run() {
                                        prevItemId = status.getCurrentItemId();
                                    }
                                });
                                mediaQueueCallback.refreshQueueItems();
                                shouldSkipUpdate = true;
                            }
                            if (shouldSkipUpdate) {
                                return;
                            }
                        }
                        // Send update
                        clientListener.onMediaUpdate(createMediaObject());
                    }
                    @Override
                    public void onQueueStatusUpdated() {
                        if (queueStatusUpdatedCallback != null) {
                            queueStatusUpdatedCallback.run();
                            setQueueStatusUpdatedCallback(null);
                        }
                    }
                });
                session.addCastListener(new Cast.Listener() {
                    @Override
                    public void onApplicationStatusChanged() {
                        clientListener.onSessionUpdate(createSessionObject());
                    }
                    @Override
                    public void onApplicationMetadataChanged(ApplicationMetadata appMetadata) {
                        clientListener.onSessionUpdate(createSessionObject());
                    }
                    @Override
                    public void onApplicationDisconnected(int i) {
                        clientListener.onSessionEnd(
                                ChromecastUtilities.createSessionObject(session, "stopped"));
                    }
                    @Override
                    public void onActiveInputStateChanged(int i) {
                        clientListener.onSessionUpdate(createSessionObject());
                    }
                    @Override
                    public void onStandbyStateChanged(int i) {
                        clientListener.onSessionUpdate(createSessionObject());
                    }
                    @Override
                    public void onVolumeChanged() {
                        clientListener.onSessionUpdate(createSessionObject());
                    }
                });
            }
        });
    }
    public void pause() {
        if (client == null || session == null) {
            //callback.error("session_error");
            return;
        }
        activity.runOnUiThread(new Runnable() {
            public void run() {
                try {
                    client.pause();
                } catch (Exception e) {
                    Log.e(TAG, "Seek error: " + e.getMessage(), e);
                }    

            }
        });
    }
    
    public void play() {
        //RemoteMediaClient client = getRemoteMediaClient();
        if (client != null) client.play();
    }
    
    public void next() {
        //RemoteMediaClient client = getRemoteMediaClient();
        if (client != null) client.queueNext(null);
    }
/*    
    public void previous() {
        //RemoteMediaClient client = getRemoteMediaClient();
        if (client != null) client.queuePrevious(null);
    }*/
    public void seek(int position) {
        if (client == null || session == null) {
            //callback.error("session_error");
            return;
        }
        activity.runOnUiThread(new Runnable() {
            public void run() {
                try {
                    client.seek(position);
                } catch (Exception e) {
                    Log.e(TAG, "Seek error: " + e.getMessage(), e);
                }    

            }
        });
    }

    /**
     * Adds a message listener if one does not already exist.
     * @param namespace namespace
     */
    public void addMessageListener(final String namespace) {
        if (client == null || session == null) {
            return;
        }
        activity.runOnUiThread(new Runnable() {
            public void run() {
                try {
                    session.setMessageReceivedCallbacks(namespace, clientListener);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Sends a message to a specified namespace.
     * @param namespace namespace
     * @param message the message to send
     * @param callback called with success or error
     */
    public void sendMessage(final String namespace, final String message, final ResultCallback<Status> callback) {
        if (client == null || session == null) {
            //callback.error("session_error");
            return;
        }
        activity.runOnUiThread(new Runnable() {
            public void run() {
                session.sendMessage(namespace, message).setResultCallback(callback);

            }
        });
    }

/* ------------------------------------   MEDIA FNs   ------------------------------------------- */

    /**
     * Loads media over the media API.
     * @param contentId      - The URL of the content
     * @param customData     - CustomData
     * @param contentType    - The MIME type of the content
     * @param duration       - The length of the video (if known)
     * @param streamType     - The stream type
     * @param autoPlay       - Whether or not to start the video playing or not
     * @param currentTime    - Where in the video to begin playing from
     * @param metadata       - Metadata
     * @param textTrackStyle - The text track style
     * @param callback called with success or error
     */
    public void loadMedia(final String contentId, final JSONObject customData, final String contentType, final long duration, final String streamType, final boolean autoPlay, final double currentTime, final JSONObject metadata, final JSONObject textTrackStyle, final PluginCall callback) {
        if (client == null || session == null) {
            callback.reject("session_error");
            return;
        }
        activity.runOnUiThread(new Runnable() {
            public void run() {
                MediaInfo mediaInfo = ChromecastUtilities.createMediaInfo(contentId, customData, contentType, duration, streamType, metadata, textTrackStyle);
                MediaLoadRequestData loadRequest = new MediaLoadRequestData.Builder()
                        .setMediaInfo(mediaInfo)
                        .setAutoplay(autoPlay)
                        .setCurrentTime((long) currentTime * 1000)
                        .build();

                requestingMedia = true;
                setQueueReloadCallback(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            callback.resolve(JSObject.fromJSONObject(createMediaObject()));
                        } catch (JSONException e) {
                            callback.reject(e.getMessage(), e);
                        }
                    }
                });
                client.load(loadRequest).setResultCallback(new ResultCallback<MediaChannelResult>() {
                    @Override
                    public void onResult(@NonNull MediaChannelResult result) {
                        requestingMedia = false;
                        if (!result.getStatus().isSuccess()) {
                            callback.reject("session_error");
                            setQueueReloadCallback(null);
                        }
                    }
                });
            }
        });
    }









/* ------------------------------------   QUEUE FNs   ------------------------------------------- */

    private void setQueueReloadCallback(Runnable callback) {
        this.queueReloadCallback = callback;
    }

    private void setQueueStatusUpdatedCallback(Runnable callback) {
        this.queueStatusUpdatedCallback = callback;
    }

    /**
     * Sets up the objects and listeners required for queue functionality.
     */
    private void setupQueue() {
        MediaQueue queue = client.getMediaQueue();
        setQueueReloadCallback(null);
        mediaQueueCallback = new MediaQueueController(queue);
        queue.registerCallback(mediaQueueCallback);
    }

    private class MediaQueueController extends MediaQueue.Callback {
        /** The MediaQueue object. **/
        private MediaQueue queue;
        /** Contains the item indexes that we need before sending out an update. **/
        private ArrayList<Integer> lookingForIndexes = new ArrayList<Integer>();
        /** Keeps track of the queueItems. **/
        private JSONArray queueItems;

        MediaQueueController(MediaQueue q) {
            this.queue = q;
        }

        /**
         * Given i == currentItemId, get items [i-1, i, i+1].
         * Note: Exclude items out of range, eg. < 0 and > queue.length.
         * Therefore, it is always 2-3 items (matches chrome desktop implementation).
         */
        void refreshQueueItems() {
            int len = queue.getItemIds().length;
            int index = getCurrentItemIndex();

            // Reset lookingForIndexes
            lookingForIndexes = new ArrayList<>();

            // Only add indexes to look for it the currentItemIndex is valid
            if (index != -1) {
                // init i-1, i, i+1 (exclude items out of range), so always 2-3 items
                for (int i = index - 1; i <= index + 1; i++) {
                    if (i >= 0 && i < len) {
                        lookingForIndexes.add(i);
                    }
                }
            }
            checkLookingForIndexes();
        }
        private int getCurrentItemIndex() {
            return queue.indexOfItemWithId(client.getMediaStatus().getCurrentItemId());
        }
        /**
         * Works to get all items listed in lookingForIndexes.
         * After all have been found, send out an update.
         */
        private void checkLookingForIndexes() {
            // reset queueItems
            queueItems = new JSONArray();

            // Can we get all items in lookingForIndex?
            MediaQueueItem item;
            boolean foundAllIndexes = true;
            for (int index : lookingForIndexes) {
                item = queue.getItemAtIndex(index, true);
                // If this returns null that means the item is not in the cache, which will
                // trigger itemsUpdatedAtIndexes, which will trigger checkLookingForIndexes again
                if (item != null) {
                    queueItems.put(ChromecastUtilities.createQueueItem(item, index));
                } else {
                    foundAllIndexes = false;
                }
            }
            if (foundAllIndexes) {
                lookingForIndexes.clear();
                updateFinished();
            }
        }
        private void updateFinished() {
            // Update the queueItems
            ChromecastUtilities.setQueueItems(queueItems);
            if (queueReloadCallback != null && queue.getItemCount() > 0) {
                queueReloadCallback.run();
                setQueueReloadCallback(null);
            }
            clientListener.onMediaUpdate(createMediaObject());
        }

        @Override
        public void itemsReloaded() {
            synchronized (queue) {
                int itemCount = queue.getItemCount();
                if (itemCount == 0) {
                    return;
                }
                if (queueReloadCallback == null) {
                    setQueueReloadCallback(new Runnable() {
                        @Override
                        public void run() {
                            // This was externally loaded
                            clientListener.onMediaLoaded(createMediaObject());
                        }
                    });
                }
                refreshQueueItems();
            }
        }
        @Override
        public void itemsUpdatedAtIndexes(int[] ints) {
            synchronized (queue) {
                // Check if we were looking for all the ints
                for (int i = 0; i < ints.length; i++) {
                    // If we weren't looking for an ints, that means it was changed
                    // (rather than just retrieved from the cache)
                    if (lookingForIndexes.indexOf(ints[i]) == -1) {
                        // So refresh the queue (the changed item might not be part
                        // of the items we want to output anyways, so let refresh
                        // handle it.
                        refreshQueueItems();
                        return;
                    }
                }
                // Else, we got new items from the cache
                checkLookingForIndexes();
            }
        }
        @Override
        public void itemsInsertedInRange(int startIndex, int insertCount) {
            synchronized (queue) {
                refreshQueueItems();
            }
        }
        @Override
        public void itemsRemovedAtIndexes(int[] ints) {
            synchronized (queue) {
                refreshQueueItems();
            }
        }
    };

/* ------------------------------------   HELPERS  ---------------------------------------------- */

    private JSONObject createSessionObject() {
        return ChromecastUtilities.createSessionObject(session);
    }

    /** Last sent media object. **/
    private JSONObject lastMediaObject;
    private JSONObject createMediaObject() {
        return createMediaObject(null);
    }

    private JSONObject createMediaObject(Integer idleReason) {
        if (idleReason != null && lastMediaObject != null) {
            try {
                lastMediaObject.put("playerState", ChromecastUtilities.getMediaPlayerState(MediaStatus.PLAYER_STATE_IDLE));
                lastMediaObject.put("idleReason", ChromecastUtilities.getMediaIdleReason(idleReason));
                return lastMediaObject;
            } catch (JSONException e) {
            }
        }
        JSONObject out = ChromecastUtilities.createMediaObject(session);
        lastMediaObject = out;
        return out;
    }

    interface Listener extends Cast.MessageReceivedCallback {
        void onMediaLoaded(JSONObject jsonMedia);
        void onMediaUpdate(JSONObject jsonMedia);
        void onSessionUpdate(JSONObject jsonSession);
        void onSessionEnd(JSONObject jsonSession);
    }
}
