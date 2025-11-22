import { registerPlugin } from '@capacitor/core';

export interface ChromecastPlugin {
  init(): Promise<void>;
  load(options: { url: string }): Promise<{ success: boolean }>;
}

const Chromecast = registerPlugin<ChromecastPlugin>('Chromecast', {
  android: () => import('./dist/android'),
});

export * from './dist/definitions';
export { Chromecast };
