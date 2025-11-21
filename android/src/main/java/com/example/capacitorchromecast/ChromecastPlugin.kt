package com.example.capacitorchromecast

import android.app.Activity
import android.content.Context
import com.getcapacitor.*
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.media.MediaLoadOptions

@CapacitorPlugin(name = "Chromecast")
class ChromecastPlugin : Plugin() {
    private var castContext: CastContext? = null
    private var session: CastSession? = null
    private var sessionListener: SessionManagerListener<CastSession>? = null

    override fun load() {
        context?.let {
            try {
                castContext = CastContext.getSharedInstance(it)
            } catch (e: Exception) {
                // CastContext may throw if no Google Play Services; handle gracefully
            }
        }

        sessionListener = object : SessionManagerListener<CastSession> {
            override fun onSessionStarted(p0: CastSession?, p1: String?) { session = p0 }
            override fun onSessionEnded(p0: CastSession?, p1: Int) { session = null }
            override fun onSessionResumed(p0: CastSession?, p1: Boolean) { session = p0 }
            override fun onSessionSuspended(p0: CastSession?, p1: Int) {}
            override fun onSessionStarting(p0: CastSession?) {}
            override fun onSessionEnding(p0: CastSession?) {}
            override fun onSessionResumeFailed(p0: CastSession?, p1: Int) { session = null }
            override fun onSessionStartFailed(p0: CastSession?, p1: Int) { session = null }
            override fun onSessionResuming(p0: CastSession?, p1: String?) {}
        }
        castContext?.sessionManager?.addSessionManagerListener(sessionListener as SessionManagerListener<CastSession>, CastSession::class.java)
    }

    @PluginMethod
    fun isAvailable(call: PluginCall) {
        val available = castContext != null
        call.resolve(JSObject().put("available", available))
    }

    @PluginMethod
    fun getStatus(call: PluginCall) {
        val connected = session?.isConnected == true
        val result = JSObject()
        result.put("connected", connected)
        if (connected) {
            result.put("deviceName", session?.castDevice?.friendlyName)
        }
        call.resolve(result)
    }

    @PluginMethod
    fun showCastDialog(call: PluginCall) {
        // In many apps you show the Cast button in toolbar; here we rely on host app to provide UI.
        // We'll just report availability.
        val available = castContext != null
        if (!available) {
            call.reject("Cast not available")
            return
        }
        call.resolve()
    }

    @PluginMethod
    fun cast(call: PluginCall) {
        val url = call.getString("url")
        val title = call.getString("title") ?: ""
        val subtitle = call.getString("subtitle") ?: ""
        val imageUrl = call.getString("imageUrl")

        if (url == null) {
            call.reject("url required")
            return
        }

        val mediaMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE)
        mediaMetadata.putString(MediaMetadata.KEY_TITLE, title)
        mediaMetadata.putString(MediaMetadata.KEY_SUBTITLE, subtitle)
        if (imageUrl != null) {
            mediaMetadata.addImage(com.google.android.gms.common.images.WebImage(android.net.Uri.parse(imageUrl)))
        }

        val mediaInfo = MediaInfo.Builder(url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType("video/mp4")
            .setMetadata(mediaMetadata)
            .build()

        val remoteClient: RemoteMediaClient? = session?.remoteMediaClient
        if (remoteClient == null) {
            call.reject("No active cast session")
            return
        }

        val options = MediaLoadOptions.Builder().setAutoplay(true).build()
        remoteClient.load(mediaInfo, options)
        val out = JSObject()
        out.put("success", true)
        call.resolve(out)
    }

    @PluginMethod
    fun stop(call: PluginCall) {
        val remoteClient: RemoteMediaClient? = session?.remoteMediaClient
        if (remoteClient == null) {
            call.reject("No active cast session")
            return
        }
        remoteClient.stop()
        call.resolve()
    }

    override fun handleOnDestroy() {
        castContext?.sessionManager?.removeSessionManagerListener(sessionListener as SessionManagerListener<CastSession>, CastSession::class.java)
        super.handleOnDestroy()
    }
}
