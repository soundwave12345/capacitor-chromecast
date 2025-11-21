import { registerPlugin } from '@capacitor/core';
import type { ChromecastPlugin } from './definitions';


const Chromecast = registerPlugin<ChromecastPlugin>('Chromecast', {
android: () => import('./implementation'),
});


export * from './definitions';
export { Chromecast };
