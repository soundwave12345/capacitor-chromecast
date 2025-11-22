package com.example.chromecast;

import android.content.Context;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.PluginMethod;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.MediaLoadRequestData;

@CapacitorPlugin(name = "Chromecast")
public class ChromecastPlugin extends Plugin {

    private CastContext castContext;
    private CastSession castSession;

    @Override
    public void load() {
        super.load();

        Context context = getContext();
        castContext = CastContext.getSharedInstance(context);

        SessionManager sessionManager = castContext.getSessionManager();

        sessionManager.addSessionManagerListener(new SessionManagerListener<CastSession>() {
            @Override
            public void onSessionStarted(CastSession session, String sessionId) {
                castSession = session;
            }

            @Override
            public void onSessionEnded(CastSession session, int error) {
                castSession = null;
            }

            // altri override obbligatori vuoti
            @Override public void onSessionResumed(CastSession session, boolean wasSuspended) {}
            @Override public void onSessionResumeFailed(CastSession session, int error) {}
            @Override public void onSessionStarting(CastSession session) {}
            @Override public void onSessionEnding(CastSession session) {}
            @Override public void onSessionSuspended(CastSession session, int reason) {}
            @Override public void onSessionStartFailed(CastSession session, int error) {}
        }, CastSession.class);
    }

    @PluginMethod
    public void startCasting(PluginCall call) {
        if (castSession == null) {
            call.reject("No active Chromecast session.");
            return;
        }

        String url = call.getString("url");

        if (url == null || url.isEmpty()) {
            call.reject("Missing audio URL");
            return;
        }

        MediaInfo mediaInfo = new MediaInfo.Builder(url)
                .setContentType("audio/mpeg")
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .build();

        MediaLoadRequestData requestData =
                new MediaLoadRequestData.Builder()
                        .setMediaInfo(mediaInfo)
                        .build();

        castSession.getRemoteMediaClient().load(requestData);

        call.resolve();
    }

    @PluginMethod
    public void stopCasting(PluginCall call) {
        if (castSession != null && castSession.getRemoteMediaClient() != null) {
            castSession.getRemoteMediaClient().stop();
        }

        call.resolve();
    }
}
