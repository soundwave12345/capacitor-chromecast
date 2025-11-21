package com.example.capacitorchromecast

import android.content.Context
import com.getcapacitor.*
import com.google.android.gms.cast.MediaLoadOptions
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient

@CapacitorPlugin(name = "Chromecast")
class ChromecastPlugin : Plugin(), SessionManagerListener<CastSession> {

    private var castSession: CastSession? = null

    override fun load() {
        val castContext = CastContext.getSharedInstance(context)
        castContext.sessionManager.addSessionManagerListener(this, CastSession::class.java)
        castSession = castContext.sessionManager.currentCastSession
    }

    // --- SessionManagerListener overrides ---
    override fun onSessionStarted(session: CastSession, sessionId: String) {
        castSession = session
    }

    override fun onSessionEnded(session: CastSession, error: Int) {
        castSession = null
    }

    override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
        castSession = session
    }

    override fun onSessionResumeFailed(session: CastSession, error: Int) {}
    override fun onSessionSuspended(session: CastSession, reason: Int) {}
    override fun onSessionStartFailed(session: CastSession, error: Int) {}
    override fun onSessionResuming(session: CastSession, sessionId: String) {}
    override fun onSessionEnding(session: CastSession) {}

    // --- Plugin methods ---
    @PluginMethod
    fun isAvailable(call: PluginCall) {
        call.resolve(JSObject().apply {
            put("available", castSession != null)
        })
    }

    @PluginMethod
    fun cast(call: PluginCall) {
        val url = call.getString("url") ?: return call.reject("url required")
        val title = call.getString("title")
        val subtitle = call.getString("subtitle")
        val imageUrl = call.getString("imageUrl")
        val contentType = call.getString("contentType") ?: "video/mp4"

        val mediaInfo = MediaInfo.Builder(url)
            .setContentType(contentType)
            .setMetadata(com.google.android.gms.cast.MediaMetadata(com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_MOVIE).apply {
                title?.let { putString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE, it) }
                subtitle?.let { putString(com.google.android.gms.cast.MediaMetadata.KEY_SUBTITLE, it) }
                imageUrl?.let { addImage(com.google.android.gms.common.images.WebImage(android.net.Uri.parse(it))) }
            })
            .build()

        val options = MediaLoadOptions.Builder().setAutoplay(true).build()

        castSession?.remoteMediaClient?.load(mediaInfo, options)
        call.resolve(JSObject().apply { put("success", true) })
    }

    @PluginMethod
    fun stop(call: PluginCall) {
        castSession?.remoteMediaClient?.stop()
        call.resolve()
    }

    @PluginMethod
    fun getStatus(call: PluginCall) {
        val connected = castSession?.isConnected ?: false
        val deviceName = castSession?.castDevice?.friendlyName
        call.resolve(JSObject().apply {
            put("connected", connected)
            deviceName?.let { put("deviceName", it) }
        })
    }
}
