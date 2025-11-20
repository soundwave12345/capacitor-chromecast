{
  "name": "capacitor-chromecast",
  "version": "1.0.0",
  "description": "Capacitor plugin for native Chromecast support",
  "main": "dist/plugin.cjs.js",
  "module": "dist/esm/index.js",
  "types": "dist/esm/index.d.ts",
  "unpkg": "dist/plugin.js",
  "files": [
    "android/",
    "dist/",
    "ios/",
    "CapacitorChromecast.podspec"
  ],
  "author": "Your Name",
  "license": "MIT",
  "repository": {
    "type": "git",
    "url": "https://github.com/yourusername/capacitor-chromecast.git"
  },
  "bugs": {
    "url": "https://github.com/yourusername/capacitor-chromecast/issues"
  },
  "keywords": [
    "capacitor",
    "plugin",
    "chromecast",
    "cast",
    "native"
  ],
  "scripts": {
    "verify": "npm run verify:ios && npm run verify:android && npm run verify:web",
    "verify:ios": "cd ios && pod install && xcodebuild -workspace Plugin.xcworkspace -scheme Plugin -destination generic/platform=iOS && cd ..",
    "verify:android": "cd android && ./gradlew clean build test && cd ..",
    "verify:web": "npm run build",
    "build": "npm run clean && npm run docgen && tsc && rollup -c rollup.config.js",
    "clean": "rimraf ./dist",
    "watch": "tsc --watch",
    "prepublishOnly": "npm run build",
    "docgen": "docgen --api ChromecastPlugin --output-readme README.md --output-json dist/docs.json"
  },
  "devDependencies": {
    "@capacitor/android": "^6.0.0",
    "@capacitor/core": "^6.0.0",
    "@capacitor/docgen": "^0.2.2",
    "@capacitor/ios": "^6.0.0",
    "@ionic/eslint-config": "^0.3.0",
    "@ionic/prettier-config": "^4.0.0",
    "@rollup/plugin-node-resolve": "^15.0.1",
    "prettier": "^3.0.0",
    "prettier-plugin-java": "^2.0.0",
    "rimraf": "^5.0.0",
    "rollup": "^4.0.0",
    "typescript": "^5.0.0"
  },
  "peerDependencies": {
    "@capacitor/core": "^6.0.0"
  },
  "capacitor": {
    "ios": {
      "src": "ios"
    },
    "android": {
      "src": "android"
    }
  }
}
