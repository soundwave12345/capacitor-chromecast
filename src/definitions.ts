export interface CastOptions {
url: string;
title?: string;
subtitle?: string;
imageUrl?: string;
}


export interface ChromecastPlugin {
isAvailable(): Promise<{ available: boolean }>;
showCastDialog(): Promise<void>;
cast(options: CastOptions): Promise<{ success: boolean }>;
stop(): Promise<void>;
getStatus(): Promise<{ connected: boolean; deviceName?: string }>;
}
