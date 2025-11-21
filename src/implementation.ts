import { WebPlugin } from '@capacitor/core';
import type { ChromecastPlugin } from './definitions';


export class ChromecastWeb extends WebPlugin implements ChromecastPlugin {
constructor() {
super({ name: 'Chromecast' });
}
async isAvailable(): Promise<{ available: boolean }> {
return { available: false };
}
async showCastDialog(): Promise<void> {
throw this.unavailable('Chromecast plugin is available on Android only.');
}
async cast(): Promise<{ success: boolean }> {
throw this.unavailable('Chromecast plugin is available on Android only.');
}
async stop(): Promise<void> {
throw this.unavailable('Chromecast plugin is available on Android only.');
}
async getStatus(): Promise<{ connected: boolean }> {
return { connected: false };
}
}


const Chromecast = new ChromecastWeb();
export { Chromecast };
