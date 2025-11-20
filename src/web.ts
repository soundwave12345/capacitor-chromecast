import { WebPlugin } from '@capacitor/core';

import type {
  ChromecastPlugin,
  ChromecastStatus,
  LoadMediaOptions,
  CastState,
  MediaStatus,
} from './definitions';

export class ChromecastWeb extends WebPlugin implements ChromecastPlugin {
  async initialize(_options: { appId: string }): Promise<void> {
    console.warn('Chromecast is not supported on web platform');
  }

  async showCastButton(): Promise<void> {
    console.warn('Chromecast is not supported on web platform');
  }

  async hideCastButton(): Promise<void> {
    console.warn('Chromecast is not supported on web platform');
  }

  async loadMedia(_options: LoadMediaOptions): Promise<void> {
    console.warn('Chromecast is not supported on web platform');
  }

  async play(): Promise<void> {
    console.warn('Chromecast is not supported on web platform');
  }

  async pause(): Promise<void> {
    console.warn('Chromecast is not supported on web platform');
  }

  async stop(): Promise<void> {
    console.warn('Chromecast is not supported on web platform');
  }

  async seek(_options: { position: number }): Promise<void> {
    console.warn('Chromecast is not supported on web platform');
  }

  async setVolume(_options: { volume: number }): Promise<void> {
    console.warn('Chromecast is not supported on web platform');
  }

  async getStatus(): Promise<ChromecastStatus> {
    return {
      isConnected: false,
      isPlaying: false,
      currentTime: 0,
      duration: 0,
      volume: 0,
    };
  }
}
