package com.yourcompany.chromecast;

import android.content.Context;
import android.net.Uri;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaLoadRequestData;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.CastState;
import com.google.android.gms.cast.framework.CastStateListener;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.images.WebImage;

@CapacitorPlugin(name = "Chromecast")
public class ChromecastPlugin extends Plugin {

    private CastContext castContext;
    private SessionManager sessionManager;
    private CastSession castSession;
    private RemoteMediaClient remoteMediaClient;
    private CastStateListener castStateListener;

    @Override
    public void load() {
        super.load();
        try {
            castContext = CastContext.getSharedInstance(getContext());
            sessionManager = castContext.getSessionManager();
            setupListeners();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupListeners() {
        castStateListener = new CastStateListener() {
            @Override
            public void onCastStateChanged(int state) {
                JSObject ret = new JSObject();
                switch (state) {
                    case CastState.NO_DEVICES_AVAILABLE:
                        ret.put("state", "NO_DEVICES_AVAILABLE");
                        break;
                    case CastState.NOT_CONNECTED:
                        ret.put("state", "NOT_CONNECTED");
                        break;
                    case CastState.CONNECTING:
                        ret.put("state", "CONNECTING");
                        break;
                    case CastState.CONNECTED:
                        ret.put("state", "CONNECTED");
                        if (castSession != null) {
                            ret.put("deviceName", castSession.getCastDevice().getFriendlyName());
                        }
                        break;
                }
                notifyListeners("castStateChanged", ret);
            }
        };
        castContext.addCastStateListener(castStateListener);

        sessionManager.addSessionManagerListener(new SessionManagerListener<CastSession>() {
            @Override
            public void onSessionStarted(CastSession session, String sessionId) {
                castSession = session;
                remoteMediaClient = castSession.getRemoteMediaClient();
                setupMediaStatusListener();
            }

            @Override
            public void onSessionEnded(CastSession session, int error) {
                castSession = null;
                remoteMediaClient = null;
            }

            @Override
            public void onSessionResumed(CastSession session, boolean wasSuspended) {
                castSession = session;
                remoteMediaClient = castSession.getRemoteMediaClient();
                setupMediaStatusListener();
            }

            @Override
            public void onSessionSuspended(CastSession session, int reason) {}

            @Override
            public void onSessionStarting(CastSession session) {}

            @Override
            public void onSessionStartFailed(CastSession session, int error) {}

            @Override
            public void onSessionEnding(CastSession session) {}

            @Override
            public void onSessionResuming(CastSession session, String sessionId) {}

            @Override
            public void onSessionResumeFailed(CastSession session, int error) {}
        }, CastSession.class);
    }

    private void setupMediaStatusListener() {
        if (remoteMediaClient != null) {
            remoteMediaClient.addListener(new RemoteMediaClient.Listener() {
                @Override
                public void onStatusUpdated() {
                    MediaStatus mediaStatus = remoteMediaClient.getMediaStatus();
                    if (mediaStatus != null) {
                        JSObject ret = new JSObject();
                        ret.put("playerState", getPlayerState(mediaStatus.getPlayerState()));
                        ret.put("currentTime", remoteMediaClient.getApproximateStreamPosition() / 1000.0);
                        ret.put("duration", remoteMediaClient.getStreamDuration() / 1000.0);
                        notifyListeners("mediaStatusChanged", ret);
                    }
                }
            });
        }
    }

    private String getPlayerState(int state) {
        switch (state) {
            case MediaStatus.PLAYER_STATE_IDLE:
                return "IDLE";
            case MediaStatus.PLAYER_STATE_PLAYING:
                return "PLAYING";
            case MediaStatus.PLAYER_STATE_PAUSED:
                return "PAUSED";
            case MediaStatus.PLAYER_STATE_BUFFERING:
                return "BUFFERING";
            case MediaStatus.PLAYER_STATE_LOADING:
                return "LOADING";
            default:
                return "IDLE";
        }
    }

    @PluginMethod
    public void initialize(PluginCall call) {
        String appId = call.getString("appId");
        if (appId == null) {
            call.reject("appId is required");
            return;
        }
        call.resolve();
    }

    @PluginMethod
    public void showCastButton(PluginCall call) {
        call.resolve();
    }

    @PluginMethod
    public void hideCastButton(PluginCall call) {
        call.resolve();
    }

    @PluginMethod
    public void loadMedia(PluginCall call) {
        String mediaUrl = call.getString("mediaUrl");
        if (mediaUrl == null) {
            call.reject("mediaUrl is required");
            return;
        }

        if (castSession == null || remoteMediaClient == null) {
            call.reject("Not connected to cast device");
            return;
        }

        MediaMetadata metadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
        
        String title = call.getString("title");
        if (title != null) {
            metadata.putString(MediaMetadata.KEY_TITLE, title);
        }

        String subtitle = call.getString("subtitle");
        if (subtitle != null) {
            metadata.putString(MediaMetadata.KEY_SUBTITLE, subtitle);
        }

        String imageUrl = call.getString("imageUrl");
        if (imageUrl != null) {
            metadata.addImage(new WebImage(Uri.parse(imageUrl)));
        }

        String contentType = call.getString("contentType", "video/mp4");
        
        MediaInfo mediaInfo = new MediaInfo.Builder(mediaUrl)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(contentType)
                .setMetadata(metadata)
                .build();

        MediaLoadRequestData loadRequest = new MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .setAutoplay(call.getBoolean("autoplay", true))
                .setCurrentTime(call.getLong("currentTime", 0L) * 1000)
                .build();

        remoteMediaClient.load(loadRequest);
        call.resolve();
    }

    @PluginMethod
    public void play(PluginCall call) {
        if (remoteMediaClient != null) {
            remoteMediaClient.play();
            call.resolve();
        } else {
            call.reject("No media loaded");
        }
    }

    @PluginMethod
    public void pause(PluginCall call) {
        if (remoteMediaClient != null) {
            remoteMediaClient.pause();
            call.resolve();
        } else {
            call.reject("No media loaded");
        }
    }

    @PluginMethod
    public void stop(PluginCall call) {
        if (castSession != null) {
            castSession.endSession();
            call.resolve();
        } else {
            call.reject("Not connected");
        }
    }

    @PluginMethod
    public void seek(PluginCall call) {
        Double position = call.getDouble("position");
        if (position == null) {
            call.reject("position is required");
            return;
        }

        if (remoteMediaClient != null) {
            remoteMediaClient.seek(position.longValue() * 1000);
            call.resolve();
        } else {
            call.reject("No media loaded");
        }
    }

    @PluginMethod
    public void setVolume(PluginCall call) {
        Double volume = call.getDouble("volume");
        if (volume == null) {
            call.reject("volume is required");
            return;
        }

        if (castSession != null) {
            try {
                castSession.setVolume(volume);
                call.resolve();
            } catch (Exception e) {
                call.reject("Failed to set volume", e);
            }
        } else {
            call.reject("Not connected");
        }
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("isConnected", castSession != null && castSession.isConnected());
        ret.put("isPlaying", false);
        ret.put("currentTime", 0);
        ret.put("duration", 0);
        ret.put("volume", 0);

        if (remoteMediaClient != null && remoteMediaClient.hasMediaSession()) {
            MediaStatus mediaStatus = remoteMediaClient.getMediaStatus();
            if (mediaStatus != null) {
                ret.put("isPlaying", mediaStatus.getPlayerState() == MediaStatus.PLAYER_STATE_PLAYING);
                ret.put("currentTime", remoteMediaClient.getApproximateStreamPosition() / 1000.0);
                ret.put("duration", remoteMediaClient.getStreamDuration() / 1000.0);
            }
        }

        if (castSession != null) {
            ret.put("volume", castSession.getVolume());
        }

        call.resolve(ret);
    }

    @Override
    protected void handleOnDestroy() {
        if (castContext != null && castStateListener != null) {
            castContext.removeCastStateListener(castStateListener);
        }
        super.handleOnDestroy();
    }
}
