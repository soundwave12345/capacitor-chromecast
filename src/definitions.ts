export interface ChromecastPlugin {
  /**
   * Initialize the Chromecast SDK
   */
  initialize(options: { appId: string }): Promise<void>;

  /**
   * Show the cast button
   */
  showCastButton(): Promise<void>;

  /**
   * Hide the cast button
   */
  hideCastButton(): Promise<void>;

  /**
   * Load media to cast
   */
  loadMedia(options: LoadMediaOptions): Promise<void>;

  /**
   * Play current media
   */
  play(): Promise<void>;

  /**
   * Pause current media
   */
  pause(): Promise<void>;

  /**
   * Stop casting and disconnect
   */
  stop(): Promise<void>;

  /**
   * Seek to position in seconds
   */
  seek(options: { position: number }): Promise<void>;

  /**
   * Set volume (0.0 - 1.0)
   */
  setVolume(options: { volume: number }): Promise<void>;

  /**
   * Get current playback status
   */
  getStatus(): Promise<ChromecastStatus>;

  /**
   * Add listener for cast state changes
   */
  addListener(
    eventName: 'castStateChanged',
    listenerFunc: (state: CastState) => void,
  ): Promise<PluginListenerHandle>;

  /**
   * Add listener for media status changes
   */
  addListener(
    eventName: 'mediaStatusChanged',
    listenerFunc: (status: MediaStatus) => void,
  ): Promise<PluginListenerHandle>;

  /**
   * Remove all listeners
   */
  removeAllListeners(): Promise<void>;
}

export interface LoadMediaOptions {
  mediaUrl: string;
  title?: string;
  subtitle?: string;
  imageUrl?: string;
  contentType?: string;
  streamType?: 'buffered' | 'live';
  autoplay?: boolean;
  currentTime?: number;
}

export interface ChromecastStatus {
  isConnected: boolean;
  isPlaying: boolean;
  currentTime: number;
  duration: number;
  volume: number;
  mediaInfo?: {
    title?: string;
    subtitle?: string;
    imageUrl?: string;
  };
}

export interface CastState {
  state: 'NO_DEVICES_AVAILABLE' | 'NOT_CONNECTED' | 'CONNECTING' | 'CONNECTED';
  deviceName?: string;
}

export interface MediaStatus {
  playerState: 'IDLE' | 'PLAYING' | 'PAUSED' | 'BUFFERING' | 'LOADING';
  currentTime: number;
  duration: number;
}

export interface PluginListenerHandle {
  remove: () => Promise<void>;
}
