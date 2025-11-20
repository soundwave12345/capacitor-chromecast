import Foundation
import Capacitor
import GoogleCast

@objc(ChromecastPlugin)
public class ChromecastPlugin: CAPPlugin {
    private var sessionManager: GCKSessionManager?
    private var remoteMediaClient: GCKRemoteMediaClient?
    
    public override func load() {
        sessionManager = GCKCastContext.sharedInstance().sessionManager
        setupListeners()
    }
    
    private func setupListeners() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(castStateDidChange),
            name: NSNotification.Name.gckCastStateDidChange,
            object: GCKCastContext.sharedInstance()
        )
        
        sessionManager?.add(self)
    }
    
    @objc private func castStateDidChange(notification: Notification) {
        let state = GCKCastContext.sharedInstance().castState
        var stateString = ""
        
        switch state {
        case .noDevicesAvailable:
            stateString = "NO_DEVICES_AVAILABLE"
        case .notConnected:
            stateString = "NOT_CONNECTED"
        case .connecting:
            stateString = "CONNECTING"
        case .connected:
            stateString = "CONNECTED"
        @unknown default:
            stateString = "NOT_CONNECTED"
        }
        
        var data: [String: Any] = ["state": stateString]
        if let session = sessionManager?.currentCastSession,
           let device = session.device {
            data["deviceName"] = device.friendlyName
        }
        
        notifyListeners("castStateChanged", data: data)
    }
    
    @objc func initialize(_ call: CAPPluginCall) {
        guard let appId = call.getString("appId") else {
            call.reject("appId is required")
            return
        }
        
        let criteria = GCKDiscoveryCriteria(applicationID: appId)
        let options = GCKCastOptions(discoveryCriteria: criteria)
        GCKCastContext.setSharedInstanceWith(options)
        
        call.resolve()
    }
    
    @objc func showCastButton(_ call: CAPPluginCall) {
        call.resolve()
    }
    
    @objc func hideCastButton(_ call: CAPPluginCall) {
        call.resolve()
    }
    
    @objc func loadMedia(_ call: CAPPluginCall) {
        guard let mediaUrl = call.getString("mediaUrl") else {
            call.reject("mediaUrl is required")
            return
        }
        
        guard let session = sessionManager?.currentCastSession,
              let remoteMediaClient = session.remoteMediaClient else {
            call.reject("Not connected to cast device")
            return
        }
        
        let metadata = GCKMediaMetadata(metadataType: .movie)
        
        if let title = call.getString("title") {
            metadata.setString(title, forKey: kGCKMetadataKeyTitle)
        }
        
        if let subtitle = call.getString("subtitle") {
            metadata.setString(subtitle, forKey: kGCKMetadataKeySubtitle)
        }
        
        if let imageUrl = call.getString("imageUrl"),
           let url = URL(string: imageUrl) {
            metadata.addImage(GCKImage(url: url, width: 480, height: 360))
        }
        
        let contentType = call.getString("contentType") ?? "video/mp4"
        
        guard let url = URL(string: mediaUrl) else {
            call.reject("Invalid media URL")
            return
        }
        
        let mediaInfoBuilder = GCKMediaInformationBuilder(contentURL: url)
        mediaInfoBuilder.streamType = .buffered
        mediaInfoBuilder.contentType = contentType
        mediaInfoBuilder.metadata = metadata
        
        let mediaInfo = mediaInfoBuilder.build()
        
        let autoplay = call.getBool("autoplay") ?? true
        let currentTime = call.getDouble("currentTime") ?? 0.0
        
        let loadOptions = GCKMediaLoadOptions()
        loadOptions.autoplay = autoplay
        loadOptions.playPosition = currentTime
        
        let request = remoteMediaClient.loadMedia(mediaInfo, with: loadOptions)
        request.delegate = self
        
        self.remoteMediaClient = remoteMediaClient
        remoteMediaClient.add(self)
        
        call.resolve()
    }
    
    @objc func play(_ call: CAPPluginCall) {
        guard let remoteMediaClient = remoteMediaClient else {
            call.reject("No media loaded")
            return
        }
        
        remoteMediaClient.play()
        call.resolve()
    }
    
    @objc func pause(_ call: CAPPluginCall) {
        guard let remoteMediaClient = remoteMediaClient else {
            call.reject("No media loaded")
            return
        }
        
        remoteMediaClient.pause()
        call.resolve()
    }
    
    @objc func stop(_ call: CAPPluginCall) {
        guard let session = sessionManager?.currentCastSession else {
            call.reject("Not connected")
            return
        }
        
        session.endSession()
        call.resolve()
    }
    
    @objc func seek(_ call: CAPPluginCall) {
        guard let position = call.getDouble("position") else {
            call.reject("position is required")
            return
        }
        
        guard let remoteMediaClient = remoteMediaClient else {
            call.reject("No media loaded")
            return
        }
        
        remoteMediaClient.seek(toTimeInterval: position)
        call.resolve()
    }
    
    @objc func setVolume(_ call: CAPPluginCall) {
        guard let volume = call.getDouble("volume") else {
            call.reject("volume is required")
            return
        }
        
        guard let session = sessionManager?.currentCastSession else {
            call.reject("Not connected")
            return
        }
        
        session.setDeviceVolume(Float(volume))
        call.resolve()
    }
    
    @objc func getStatus(_ call: CAPPluginCall) {
        var result: [String: Any] = [
            "isConnected": false,
            "isPlaying": false,
            "currentTime": 0,
            "duration": 0,
            "volume": 0
        ]
        
        if let session = sessionManager?.currentCastSession {
            result["isConnected"] = session.connectionState == .connected
            result["volume"] = session.currentDeviceVolume
            
            if let remoteMediaClient = session.remoteMediaClient,
               let mediaStatus = remoteMediaClient.mediaStatus {
                result["isPlaying"] = mediaStatus.playerState == .playing
                result["currentTime"] = remoteMediaClient.approximateStreamPosition()
                result["duration"] = mediaStatus.mediaInformation?.streamDuration ?? 0
                
                if let metadata = mediaStatus.mediaInformation?.metadata {
                    var mediaInfo: [String: String] = [:]
                    if let title = metadata.string(forKey: kGCKMetadataKeyTitle) {
                        mediaInfo["title"] = title
                    }
                    if let subtitle = metadata.string(forKey: kGCKMetadataKeySubtitle) {
                        mediaInfo["subtitle"] = subtitle
                    }
                    if let images = metadata.images(), images.count > 0,
                       let imageUrl = images[0].url.absoluteString as String? {
                        mediaInfo["imageUrl"] = imageUrl
                    }
                    result["mediaInfo"] = mediaInfo
                }
            }
        }
        
        call.resolve(result)
    }
    
    deinit {
        NotificationCenter.default.removeObserver(self)
        sessionManager?.remove(self)
        remoteMediaClient?.remove(self)
    }
}

extension ChromecastPlugin: GCKSessionManagerListener {
    public func sessionManager(_ sessionManager: GCKSessionManager, didStart session: GCKCastSession) {
        remoteMediaClient = session.remoteMediaClient
        remoteMediaClient?.add(self)
    }
    
    public func sessionManager(_ sessionManager: GCKSessionManager, didEnd session: GCKCastSession, withError error: Error?) {
        remoteMediaClient?.remove(self)
        remoteMediaClient = nil
    }
}

extension ChromecastPlugin: GCKRemoteMediaClientListener {
    public func remoteMediaClient(_ client: GCKRemoteMediaClient, didUpdate mediaStatus: GCKMediaStatus?) {
        guard let mediaStatus = mediaStatus else { return }
        
        var playerState = "IDLE"
        switch mediaStatus.playerState {
        case .idle:
            playerState = "IDLE"
        case .playing:
            playerState = "PLAYING"
        case .paused:
            playerState = "PAUSED"
        case .buffering:
            playerState = "BUFFERING"
        case .loading:
            playerState = "LOADING"
        @unknown default:
            playerState = "IDLE"
        }
        
        let data: [String: Any] = [
            "playerState": playerState,
            "currentTime": client.approximateStreamPosition(),
            "duration": mediaStatus.mediaInformation?.streamDuration ?? 0
        ]
        
        notifyListeners("mediaStatusChanged", data: data)
    }
}

extension ChromecastPlugin: GCKRequestDelegate {
    public func requestDidComplete(_ request: GCKRequest) {
        print("Cast request completed")
    }
    
    public func request(_ request: GCKRequest, didFailWithError error: GCKError) {
        print("Cast request failed: \(error.localizedDescription)")
    }
}
