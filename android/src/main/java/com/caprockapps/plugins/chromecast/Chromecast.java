package com.caprockapps.plugins.chromecast;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.mediarouter.media.MediaRouter;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.framework.Session;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

@CapacitorPlugin()
public class Chromecast extends Plugin {
    /**
     * Tag for logging.
     */
    private static final String TAG = "Chromecast";
    /**
     * Object to control the connection to the chromecast.
     */
    private ChromecastConnection connection;
    /**
     * Object to control the media.
     */
    private ChromecastSession media;
    /**
     * Holds the reference to the current client initiated scan.
     */
    private ChromecastConnection.ScanCallback clientScan;
    /**
     * Holds the reference to the current client initiated scan callback.
     */
    private PluginCall scanPluginCall;
    /**
     * In the case that chromecast can't be used.
     **/
    private String noChromecastError;

    /**
     * Initialize all of the MediaRouter stuff with the AppId.
     * For now, ignore the autoJoinPolicy and defaultActionPolicy; those will come later
     *
     * @param pluginCall called with .success or .error depending on the result
     * @return true for cordova
     */
    @PluginMethod
    public boolean initialize(final PluginCall pluginCall) {
        String appId = pluginCall.getString("appId");
        Log.d(TAG, "Initialize called with App ID: " + appId);
        
        if (appId == null || appId.isEmpty()) {
            Log.e(TAG, "App ID is null or empty");
            pluginCall.reject("App ID is required");
            return false;
        }
        
        Log.d(TAG, "App ID validation passed: " + appId);
        // tab_and_origin_scoped | origin_scoped | page_scoped
        String autoJoinPolicy = pluginCall.getString("autoJoinPolicy");
        // create_session | cast_this_tab
        String defaultActionPolicy = pluginCall.getString("defaultActionPolicy");

        setup();

        try {
            this.connection = new ChromecastConnection(getActivity(), new ChromecastConnection.Listener() {
                @Override
                public void onSessionStarted(Session session, String sessionId) {
                  try {
                    JSONObject result = new JSONObject();
                    result.put("isConnected",session.isConnected());
                    result.put("sessionId",sessionId);
                    sendEvent("SESSION_STARTED", JSObject.fromJSONObject(result));
                  } catch (JSONException e) {
                  }
                }


              @Override
              public void onSessionEnded(Session session, int error) {
                try {
                  JSONObject result = new JSONObject();
                  result.put("isConnected",session.isConnected());
                  result.put("error",error);
                  sendEvent("SESSION_ENDED", JSObject.fromJSONObject(result));
                } catch (JSONException e) {
                }
              }
              @Override
              public void onSessionEnding(Session session) {
              }
              @Override
              public void onSessionResumeFailed(Session session, int error) {
              }
              @Override
              public void onSessionResumed(Session session, boolean wasSuspended) {
                try {
                  JSONObject result = new JSONObject();
                  result.put("isConnected",session.isConnected());
                  result.put("wasSuspended",wasSuspended);
                  sendEvent("SESSION_RESUMED", JSObject.fromJSONObject(result));
                } catch (JSONException e) {
                }
              }
              @Override
              public void onSessionResuming(Session session, String sessionId) {
              }
              @Override
              public void onSessionStartFailed(Session session, int error) {
                try {
                  JSONObject result = new JSONObject();
                  result.put("isConnected",session.isConnected());
                  result.put("error",error);
                  sendEvent("SESSION_START_FAILED", JSObject.fromJSONObject(result));
                } catch (JSONException e) {
                }
              }
              @Override
              public void onSessionStarting(Session session) {
              }
              @Override
              public void onSessionSuspended(Session session, int reason) {
              }

                @Override
                public void onSessionRejoin(JSONObject jsonSession) {
                    try {
                        sendEvent("SESSION_LISTENER", JSObject.fromJSONObject(jsonSession));
                    } catch (JSONException e) {
                    }
                }

                @Override
                public void onSessionUpdate(JSONObject jsonSession) {
                    try {
                        sendEvent("SESSION_UPDATE", JSObject.fromJSONObject(jsonSession));
                    } catch (JSONException e) {
                    }
                }

                @Override
                public void onSessionEnd(JSONObject jsonSession) {
                    onSessionUpdate(jsonSession);
                }

                @Override
                public void onReceiverAvailableUpdate(boolean available) {
                    sendEvent("RECEIVER_LISTENER", new JSObject().put("isAvailable", available));
                }

                @Override
                public void onMediaLoaded(JSONObject jsonMedia) {
                    try {
                        sendEvent("MEDIA_LOAD", JSObject.fromJSONObject(jsonMedia));
                    } catch (JSONException e) {
                    }
                }

                @Override
                public void onMediaUpdate(JSONObject jsonMedia) {
//                    JSONArray out = new JSONArray();


                    // TODO: Fix null pointer exception
                    try {
                        if (jsonMedia != null) {
                            sendEvent("MEDIA_UPDATE", JSObject.fromJSONObject(jsonMedia));
                        }
                    } catch (JSONException e) {
                    }
                }

                @Override
                public void onMessageReceived(CastDevice device, String namespace, String message) {
                    sendEvent("RECEIVER_MESSAGE", new JSObject().put(device.getDeviceId(), new JSObject().put("namespace", namespace).put("message", message)));
                }
            });
            this.media = connection.getChromecastSession();
        } catch (RuntimeException e) {
            Log.e("tag", "Error initializing Chromecast connection: " + e.getMessage());
            noChromecastError = "Could not initialize chromecast: " + e.getMessage();
            e.printStackTrace();
        }

        connection.initialize(appId, pluginCall);
        return true;
    }

    /**
     * Request the session for the previously sent appId.
     * THIS IS WHAT LAUNCHES THE CHROMECAST PICKER
     * or, if we already have a session launch the connection options
     * dialog which will have the option to stop casting at minimum.
     *
     * @param pluginCall called with .success or .error depending on the result
     * @return true for capacitor
     */
    @PluginMethod
    public boolean requestSession(final PluginCall pluginCall) {
        connection.requestSession(new ChromecastConnection.RequestSessionCallback() {
            @Override
            public void onJoin(JSONObject jsonSession) {
                try {
                    pluginCall.resolve(JSObject.fromJSONObject(jsonSession));
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing session JSON", e);
                    pluginCall.reject("session_parse_error", e.getMessage());
                }
            }

            @Override
            public void onError(int errorCode) {
                Log.e(TAG, "Session request failed with error code: " + errorCode);
                
                // Gestion spécifique de l'erreur APPLICATION_NOT_FOUND
                if (errorCode == 2475) {
                    String errorMessage = "L'application Chromecast ne peut pas être chargée. ";
                    errorMessage += "Vérifiez que votre Chromecast a accès à Internet et est entièrement configuré.";
                    pluginCall.reject("application_not_found", errorMessage);
                } else {
                    // Autres codes d'erreur avec des messages plus descriptifs
                    String errorMessage = getErrorMessage(errorCode);
                    pluginCall.reject("session_error", errorMessage);
                }
            }

            @Override
            public void onCancel() {
                Log.d(TAG, "Session request was cancelled by user");
                pluginCall.reject("session_cancelled", "User cancelled the session request");
            }
        });
        return true;
    }
    
    /**
     * Convertit les codes d'erreur Cast en messages plus compréhensibles
     */
    private String getErrorMessage(int errorCode) {
        switch (errorCode) {
            case 2000:
                return "Échec de l'authentification";
            case 2001:
                return "Requête invalide";
            case 2002:
                return "Requête annulée";
            case 2003:
                return "Requête non autorisée";
            case 2004:
                return "Application non trouvée";
            case 2005:
                return "Application non en cours d'exécution";
            case 2006:
                return "Message trop volumineux";
            case 2007:
                return "Tampon d'envoi plein";
            case 2475:
                return "Application receiver introuvable - vérifiez votre connexion Internet et l'App ID";
            case 7:
                return "Erreur réseau";
            case 8:
                return "Erreur interne";
            case 15:
                return "Timeout de la requête";
            default:
                return "Erreur inconnue (code: " + errorCode + ")";
        }
    }

    /**
     * Selects a route by its id.
     *
     * @param pluginCall called with .success or .error depending on the result
     * @return true for cordova
     */
    @PluginMethod
    public boolean selectRoute(final PluginCall pluginCall) {
        // the id of the route to join
        String routeId = pluginCall.getString("routeId");
        connection.selectRoute(routeId, new ChromecastConnection.SelectRouteCallback() {
            @Override
            public void onJoin(JSONObject jsonSession) {
                try {
                    pluginCall.resolve(JSObject.fromJSONObject(jsonSession));
                } catch (JSONException e) {
                    pluginCall.reject("json_parse_error", e);
                }
            }

            @Override
            public void onError(JSONObject message) {
                try {
                    pluginCall.resolve(JSObject.fromJSONObject(message));
                } catch (JSONException e) {
                    pluginCall.reject("json_parse_error", e);
                }
            }
        });
        return true;
    }



    /**
     * Send a custom message to the receiver - we don't need this just yet... it was just simple to implement on the js side.
     *
     * @param namespace       namespace
     * @param message         the message to send

     * @return true for cordova
     */
    @PluginMethod
    public boolean sendMessage(final PluginCall pluginCall) {
      String namespace = pluginCall.getString("namespace");
      String message = pluginCall.getString("message");
      JSObject returnObj = new JSObject();
      returnObj.put("success",false);
      //If we don't have a session here we need to try and get it
      if(this.media == null) this.media = connection.getChromecastSession();
      //If we still don't have a session we can't call sendMessage return false;
      if(this.media == null){
        pluginCall.resolve(returnObj);
        return false;
      }
      this.media.sendMessage(namespace, message,new ResultCallback<Status>() {
        @Override
        public void onResult(Status result) {
          if (!result.isSuccess()) {
            returnObj.put("error",result.getStatus().toString());
          } else {
            returnObj.put("success",true);
          }
        }
      });
      pluginCall.resolve(returnObj);
      return true;
    }

    /**
     * Adds a listener to a specific namespace.
     *
     * @param pluginCall called with .success or .error depending on the result
     */
    @PluginMethod
    public void addMessageListener(PluginCall pluginCall) {
        String namespace = pluginCall.getString("namespace");
        if (namespace != null) {
            this.media.addMessageListener(namespace);
            pluginCall.resolve();
        } else {
            pluginCall.reject("namespace is required");
        }
    }

    /**
     * Loads some media on the Chromecast using the media APIs.
     *
     * @param contentId      The URL of the media item
     * @param customData     CustomData
     * @param contentType    MIME type of the content
     * @param duration       Duration of the content
     * @param streamType     buffered | live | other
     * @param autoPlay       Whether or not to automatically start playing the media
     * @param currentTime    Where to begin playing from
     * @param metadata       Metadata
     * @param textTrackStyle The text track style
     * @param pluginCall     called with .success or .error depending on the result
     */
    @PluginMethod
    public void loadMedia(final PluginCall pluginCall) {
        String contentId = pluginCall.getString("contentId");
        JSObject customData = pluginCall.getObject("customData", new JSObject());
        String contentType = pluginCall.getString("contentType", "");
        Integer duration = pluginCall.getInt("duration", 0);
        String streamType = pluginCall.getString("streamType", "");
        Boolean autoPlay = pluginCall.getBoolean("autoPlay", false);
        Integer currentTime = pluginCall.getInt("currentTime", 0);
        JSObject metadata = pluginCall.getObject("metadata", new JSObject());
        JSObject textTrackStyle = pluginCall.getObject("textTrackStyle", new JSObject());

        // Détection automatique du contentType si non spécifié ou incorrect
        String detectedContentType = detectContentType(contentId, contentType);
        if (!detectedContentType.equals(contentType)) {
            Log.d(TAG, "ContentType corrigé de '" + contentType + "' vers '" + detectedContentType + "'");
            contentType = detectedContentType;
        }
        
        // Ajustement du streamType pour HLS
        if (detectedContentType.equals("application/x-mpegURL") && (streamType == null || streamType.isEmpty())) {
            streamType = "LIVE";
            Log.d(TAG, "StreamType défini sur LIVE pour le stream HLS");
        }
        
        // Logging pour diagnostic
        Log.d(TAG, "=== LOAD MEDIA DEBUG ===");
        Log.d(TAG, "contentId: " + contentId);
        Log.d(TAG, "contentType: " + contentType);
        Log.d(TAG, "streamType: " + streamType);
        Log.d(TAG, "autoPlay: " + autoPlay);
        Log.d(TAG, "duration: " + duration);
        Log.d(TAG, "currentTime: " + currentTime);
        Log.d(TAG, "========================");

        this.connection.getChromecastSession().loadMedia(contentId, customData, contentType, duration, streamType, autoPlay, currentTime, metadata, textTrackStyle, pluginCall);
    }
    
    /**
     * Méthode pour charger un média avec des en-têtes d'authentification personnalisés.
     * Cette méthode permet de passer des tokens d'authentification et des headers personnalisés.
     * 
     * @param pluginCall contient les paramètres suivants :
     *                   - contentId : l'URL du média
     *                   - customData : données personnalisées (optionnel)
     *                   - contentType : type MIME du contenu (optionnel, détecté automatiquement)
     *                   - duration : durée du média en secondes (optionnel)
     *                   - streamType : type de stream (optionnel, détecté automatiquement)
     *                   - autoPlay : lecture automatique (optionnel, défaut : false)
     *                   - currentTime : position de départ en secondes (optionnel, défaut : 0)
     *                   - metadata : métadonnées du média (optionnel)
     *                   - textTrackStyle : style des sous-titres (optionnel)
     *                   - authHeaders : en-têtes d'authentification (optionnel)
     *                   - authToken : token d'authentification à ajouter aux customData (optionnel)
     */
    @PluginMethod
    public void loadMediaWithHeaders(final PluginCall pluginCall) {
        String contentId = pluginCall.getString("contentId");
        JSObject customData = pluginCall.getObject("customData", new JSObject());
        String contentType = pluginCall.getString("contentType", "");
        Integer duration = pluginCall.getInt("duration", 0);
        String streamType = pluginCall.getString("streamType", "");
        Boolean autoPlay = pluginCall.getBoolean("autoPlay", false);
        Integer currentTime = pluginCall.getInt("currentTime", 0);
        JSObject metadata = pluginCall.getObject("metadata", new JSObject());
        JSObject textTrackStyle = pluginCall.getObject("textTrackStyle", new JSObject());
        JSObject authHeaders = pluginCall.getObject("authHeaders", new JSObject());
        String authToken = pluginCall.getString("authToken", "");

        // Ajouter les en-têtes d'authentification aux customData
        if (authHeaders != null && authHeaders.length() > 0) {
            try {
                customData.put("authHeaders", authHeaders);
                Log.d(TAG, "Added auth headers to customData: " + authHeaders.toString());
            } catch (Exception e) {
                Log.w(TAG, "Failed to add auth headers to customData", e);
            }
        }
        
        // Ajouter le token d'authentification aux customData
        if (authToken != null && !authToken.isEmpty()) {
            try {
                customData.put("authToken", authToken);
                Log.d(TAG, "Added auth token to customData");
            } catch (Exception e) {
                Log.w(TAG, "Failed to add auth token to customData", e);
            }
        }

        // Détection automatique du contentType si non spécifié ou incorrect
        String detectedContentType = detectContentType(contentId, contentType);
        if (!detectedContentType.equals(contentType)) {
            Log.d(TAG, "ContentType corrigé de '" + contentType + "' vers '" + detectedContentType + "'");
            contentType = detectedContentType;
        }
        
        // Ajustement du streamType pour HLS
        if (detectedContentType.equals("application/x-mpegURL") && (streamType == null || streamType.isEmpty())) {
            streamType = "LIVE";
            Log.d(TAG, "StreamType défini sur LIVE pour le stream HLS");
        }
        
        // Logging pour diagnostic
        Log.d(TAG, "=== LOAD MEDIA WITH HEADERS DEBUG ===");
        Log.d(TAG, "contentId: " + contentId);
        Log.d(TAG, "contentType: " + contentType);
        Log.d(TAG, "streamType: " + streamType);
        Log.d(TAG, "autoPlay: " + autoPlay);
        Log.d(TAG, "duration: " + duration);
        Log.d(TAG, "currentTime: " + currentTime);
        Log.d(TAG, "customData: " + customData.toString());
        Log.d(TAG, "=====================================");

        this.connection.getChromecastSession().loadMedia(contentId, customData, contentType, duration, streamType, autoPlay, currentTime, metadata, textTrackStyle, pluginCall);
    }

    /**
     * Méthode simplifiée pour lancer un média avec des paramètres par défaut.
     * Wrapper autour de loadMedia pour une utilisation plus simple.
     *
     * @param pluginCall contient l'URL du média dans le paramètre "mediaUrl"
     */
    @PluginMethod
    public void launchMedia(final PluginCall pluginCall) {
        // Debug complet pour voir toutes les données
        Log.d(TAG, "=== DEBUG LAUNCH MEDIA ===");
        Log.d(TAG, "MethodName: " + pluginCall.getMethodName());
        Log.d(TAG, "CallbackId: " + pluginCall.getCallbackId());
        
        JSObject data = pluginCall.getData();
        Log.d(TAG, "Data: " + (data != null ? data.toString() : "null"));
        
        if (data != null) {
            // Lister toutes les clés disponibles
            try {
                // JSObject hérite de JSONObject, utilisons les méthodes disponibles
                java.util.Iterator<String> keys = data.keys();
                Log.d(TAG, "Clés disponibles:");
                while (keys.hasNext()) {
                    String key = keys.next();
                    Object value = data.opt(key);
                    Log.d(TAG, "  " + key + ": " + value + " (" + (value != null ? value.getClass().getSimpleName() : "null") + ")");
                }
            } catch (Exception e) {
                Log.e(TAG, "Erreur lors de l'analyse des clés: " + e.getMessage());
            }
        }
        
        // Pour une méthode avec un seul paramètre, Capacitor utilise une approche différente
        // L'URL devrait être le premier et seul paramètre passé à la méthode
        String mediaUrl = null;
        
        // Essayer différentes façons de récupérer le paramètre unique
        // 1. Récupérer comme paramètre nommé "mediaUrl"
        mediaUrl = pluginCall.getString("mediaUrl");
        if (mediaUrl != null && !mediaUrl.isEmpty()) {
            Log.d(TAG, "URL trouvée dans mediaUrl: " + mediaUrl);
        } else {
            // 2. Essayer "url"  
            mediaUrl = pluginCall.getString("url");
            if (mediaUrl != null && !mediaUrl.isEmpty()) {
                Log.d(TAG, "URL trouvée dans url: " + mediaUrl);
            } else {
                // 3. Essayer "options" (d'après vos logs précédents)
                mediaUrl = pluginCall.getString("options");
                if (mediaUrl != null && !mediaUrl.isEmpty()) {
                    Log.d(TAG, "URL trouvée dans options: " + mediaUrl);
                } else {
                    // 4. Si les données ne sont pas vides, essayer de les traiter comme une chaîne directe
                    if (data != null && data.has("options")) {
                        Object options = data.opt("options");
                        Log.d(TAG, "Options trouvé: " + options + " (" + (options != null ? options.getClass().getSimpleName() : "null") + ")");
                        if (options instanceof String) {
                            mediaUrl = (String) options;
                            Log.d(TAG, "URL trouvée dans data.options: " + mediaUrl);
                        }
                    }
                }
            }
        }
        
        Log.d(TAG, "=== FIN DEBUG ===");
        
        if (mediaUrl == null || mediaUrl.isEmpty()) {
            Log.e(TAG, "ERREUR: Aucune URL trouvée après toutes les tentatives");
            pluginCall.reject("mediaUrl est requis");
            return;
        }
        
        Log.d(TAG, "URL finale utilisée: " + mediaUrl);

        // Vérifier si une session est active
        if (this.connection == null) {
            Log.e(TAG, "ERREUR: Aucune connexion Chromecast initialisée");
            pluginCall.resolve(new JSObject().put("value", false));
            return;
        }
        
        if (this.connection.getChromecastSession() == null) {
            Log.e(TAG, "ERREUR: Aucune session Chromecast active");
            pluginCall.resolve(new JSObject().put("value", false));
            return;
        }
        
        Log.d(TAG, "Session Chromecast trouvée, tentative de lancement du média...");

        try {
            // Utiliser des valeurs par défaut pour les paramètres optionnels
            JSObject customData = new JSObject();
            
            // Détection automatique du contentType
            String contentType = detectContentType(mediaUrl, null);
            
            Integer duration = 0; // Durée inconnue
            String streamType = "BUFFERED"; // Type de stream par défaut
            
            // Ajustement du streamType pour HLS
            if (contentType.equals("application/x-mpegURL")) {
                streamType = "LIVE";
                Log.d(TAG, "StreamType ajusté pour HLS: LIVE");
            }
            
            Boolean autoPlay = true; // Lecture automatique
            Integer currentTime = 0; // Commencer au début
            JSObject metadata = new JSObject();
            JSObject textTrackStyle = new JSObject();

            Log.d(TAG, "Paramètres de loadMedia:");
            Log.d(TAG, "  URL: " + mediaUrl);
            Log.d(TAG, "  ContentType (détecté): " + contentType);
            Log.d(TAG, "  StreamType: " + streamType);
            Log.d(TAG, "  AutoPlay: " + autoPlay);

            // Appeler directement la méthode loadMedia de ChromecastSession
            Log.d(TAG, "Appel de la méthode loadMedia de ChromecastSession...");
            
            // Convertir les paramètres en format JSON
            try {
                JSONObject customDataJSON = new JSONObject(customData.toString());
                JSONObject metadataJSON = new JSONObject(metadata.toString());
                JSONObject textTrackStyleJSON = new JSONObject(textTrackStyle.toString());
                
                // Appeler directement la méthode loadMedia de ChromecastSession
                this.connection.getChromecastSession().loadMedia(
                    mediaUrl, 
                    customDataJSON, 
                    contentType, 
                    duration.longValue(), 
                    streamType, 
                    autoPlay, 
                    currentTime.doubleValue(), 
                    metadataJSON, 
                    textTrackStyleJSON, 
                    pluginCall
                );
            } catch (JSONException e) {
                Log.e(TAG, "Erreur de conversion JSON: " + e.getMessage());
                pluginCall.reject("json_error", e);
            }
            
            Log.d(TAG, "LaunchMedia terminé, méthode loadMedia appelée");
        } catch (Exception e) {
            Log.e(TAG, "Exception lors du lancement du média: " + e.getMessage());
            e.printStackTrace();
            pluginCall.resolve(new JSObject().put("value", false));
        }
    }



    /**
     * Stops the session.
     *
     * @param pluginCall called with .success or .error depending on the result
     * @return true for cordova
     */
    @PluginMethod
    public boolean sessionStop(PluginCall pluginCall) {
        connection.endSession(true, pluginCall);
        return true;
    }

    /**
     * Stops the session.
     *
     * @param pluginCall called with .success or .error depending on the result
     * @return true for cordova
     */
    @PluginMethod
    public boolean sessionLeave(PluginCall pluginCall) {
        connection.endSession(false, pluginCall);
        return true;
    }

    /**
     * Will actively scan for routes and send a json array to the client.
     * It is super important that client calls "stopRouteScan", otherwise the
     * battery could drain quickly.
     *
     * @param pluginCall called with .success or .error depending on the result
     * @return true for cordova
     */
    @PluginMethod
    public boolean startRouteScan(PluginCall pluginCall) {
        if (scanPluginCall != null) {

            scanPluginCall.reject("Started a new route scan before stopping previous one.");
//            scanPluginCall.reject(ChromecastUtilities.createError("cancel", "Started a new route scan before stopping previous one."));
        }
        scanPluginCall = pluginCall;
        Runnable startScan = new Runnable() {
            @Override
            public void run() {
                clientScan = new ChromecastConnection.ScanCallback() {
                    @Override
                    void onRouteUpdate(List<MediaRouter.RouteInfo> routes) {
                        if (scanPluginCall != null) {
                            JSObject ret = new JSObject();
                            JSArray retArr = new JSArray();

                            for (int i = 0; i < routes.size(); i++) {
                                retArr.put(routes.get(i));
                            }
                            ret.put("routes", retArr);

                            scanPluginCall.resolve(ret);
                        } else {
                            // Try to get the scan to stop because we already ended the scanCallback
                            connection.stopRouteScan(clientScan, null);
                        }
                    }
                };
                connection.startRouteScan(null, clientScan, null);
            }
        };
        if (clientScan != null) {
            // Stop any other existing clientScan
            connection.stopRouteScan(clientScan, startScan);
        } else {
            startScan.run();
        }
        return true;
    }

    /**
     * Stops the scan started by startRouteScan.
     *
     * @param pluginCall called with .success or .error depending on the result
     * @return true for cordova
     */
    @PluginMethod
    public boolean stopRouteScan(final PluginCall pluginCall) {
        // Stop any other existing clientScan
        connection.stopRouteScan(clientScan, new Runnable() {
            @Override
            public void run() {
                if (scanPluginCall != null) {
                    scanPluginCall.reject("Scan stopped.");
                    scanPluginCall = null;
                }
                pluginCall.resolve();
            }
        });
        return true;
    }

    /**
     * Do everything you need to for "setup" - calling back sets the isAvailable and lets every function on the
     * javascript side actually do stuff.
     *
     * @return true for cordova
     */
    private boolean setup() {
        if (this.connection != null) {
            connection.stopRouteScan(clientScan, new Runnable() {
                @Override
                public void run() {
                    if (scanPluginCall != null) {
                        scanPluginCall.reject("Scan stopped because setup triggered.");
                        scanPluginCall = null;
                    }
                    sendEvent("SETUP", new JSObject());
                }
            });
        }

        return true;
    }

    /**
     * This triggers an event on the JS-side.
     *
     * @param eventName - The name of the JS event to trigger
     * @param args      - The arguments to pass the JS event
     */
    private void sendEvent(String eventName, JSObject args) {
        notifyListeners(eventName, args);
    }

    /**
     * Test method to try different App IDs and diagnose the issue
     */
    @PluginMethod
    public void testAppIds(PluginCall pluginCall) {
        String[] testAppIds = {
            "CC1AD845", // Default Media Receiver
            "4F8B3483", // Styled Media Receiver
            "07AEE832", // TicTacToe sample app
            "233637DE"  // Another test app
        };
        
        JSObject result = new JSObject();
        JSArray results = new JSArray();
        
        for (String appId : testAppIds) {
            Log.d(TAG, "Testing App ID: " + appId);
            JSObject testResult = new JSObject();
            testResult.put("appId", appId);
            testResult.put("name", getAppIdName(appId));
            
            try {
                // Just log the test, don't actually initialize
                Log.d(TAG, "Would test: " + appId + " (" + getAppIdName(appId) + ")");
                testResult.put("status", "ready_for_test");
                
            } catch (Exception e) {
                testResult.put("status", "error: " + e.getMessage());
            }
            
            results.put(testResult);
        }
        
        result.put("testResults", results);
        result.put("message", "Use these App IDs to test: initialize with each one individually");
        pluginCall.resolve(result);
    }
    
    private String getAppIdName(String appId) {
        switch (appId) {
            case "CC1AD845": return "Default Media Receiver";
            case "4F8B3483": return "Styled Media Receiver";
            case "07AEE832": return "TicTacToe Sample";
            case "233637DE": return "Test App";
            default: return "Unknown";
        }
    }

    /**
     * Diagnostic method to check Chromecast and network status
     */
    @PluginMethod
    public void diagnosticInfo(PluginCall pluginCall) {
        JSObject result = new JSObject();
        
        try {
            // Basic diagnostic information
            result.put("sdkVersion", "Google Cast SDK");
            result.put("diagnosticTime", System.currentTimeMillis());
            
            // Add troubleshooting suggestions
            JSArray suggestions = new JSArray();
            suggestions.put("1. Redémarrez votre Chromecast (débranchez 30 secondes)");
            suggestions.put("2. Vérifiez que votre Chromecast peut accéder à Internet");
            suggestions.put("3. Assurez-vous que les deux appareils sont sur le même réseau WiFi");
            suggestions.put("4. Testez avec l'app Google Home pour vérifier la connectivité");
            suggestions.put("5. Essayez un autre App ID: '4F8B3483' (Styled Media Receiver)");
            suggestions.put("6. Vérifiez les paramètres de votre pare-feu/antivirus");
            result.put("troubleshootingSuggestions", suggestions);
            
            // Common App IDs to test
            JSArray testAppIds = new JSArray();
            testAppIds.put("CC1AD845 - Default Media Receiver");
            testAppIds.put("4F8B3483 - Styled Media Receiver");
            testAppIds.put("07AEE832 - TicTacToe Sample");
            testAppIds.put("233637DE - Test App");
            result.put("testAppIds", testAppIds);
            
        } catch (Exception e) {
            result.put("error", e.getMessage());
            Log.e(TAG, "Error in diagnosticInfo", e);
        }
        
        pluginCall.resolve(result);
    }
    
    private String getCastStateDescription(int castState) {
        switch (castState) {
            case 1: return "NO_DEVICES_AVAILABLE - Aucun appareil Cast trouvé";
            case 2: return "NOT_CONNECTED - Pas connecté";
            case 3: return "CONNECTING - Connexion en cours";
            case 4: return "CONNECTED - Connecté";
            default: return "Unknown state: " + castState;
        }
    }

    /**
     * Detecte automatiquement le contentType en fonction de l'URL
     */
    private String detectContentType(String url, String providedContentType) {
        if (url == null) {
            return providedContentType != null ? providedContentType : "video/mp4";
        }
        
        // Nettoie l'URL pour enlever les paramètres de requête SEULEMENT pour la détection
        // L'URL originale avec les tokens sera préservée lors du chargement du média
        String baseUrl = url.split("\\?")[0].toLowerCase();
        
        if (baseUrl.endsWith(".m3u8")) {
            Log.d(TAG, "Detected HLS stream (.m3u8) - using application/x-mpegURL");
            if (url.contains("token=") || url.contains("?") || url.contains("&")) {
                Log.d(TAG, "HLS stream with authentication tokens detected - tokens will be preserved");
            }
            return "application/x-mpegURL";
        } else if (baseUrl.endsWith(".mpd")) {
            Log.d(TAG, "Detected DASH stream (.mpd) - using application/dash+xml");
            if (url.contains("token=") || url.contains("?") || url.contains("&")) {
                Log.d(TAG, "DASH stream with authentication tokens detected - tokens will be preserved");
            }
            return "application/dash+xml";
        } else if (baseUrl.endsWith(".mp4")) {
            Log.d(TAG, "Detected MP4 video - using video/mp4");
            return "video/mp4";
        } else if (baseUrl.endsWith(".webm")) {
            Log.d(TAG, "Detected WebM video - using video/webm");
            return "video/webm";
        } else if (baseUrl.endsWith(".mkv")) {
            Log.d(TAG, "Detected MKV video - using video/x-matroska");
            return "video/x-matroska";
        }
        
        // Si on ne peut pas détecter, utilise le contentType fourni ou MP4 par défaut
        return providedContentType != null ? providedContentType : "video/mp4";
    }

    /**
     * Teste l'accessibilité d'une URL pour diagnostiquer les problèmes de chargement
     */
    @PluginMethod
    public void testUrl(PluginCall pluginCall) {
        String url = pluginCall.getString("url");
        if (url == null || url.isEmpty()) {
            pluginCall.reject("URL est requise");
            return;
        }
        
        JSObject result = new JSObject();
        result.put("url", url);
        
        try {
            // Analyser l'URL
            java.net.URL urlObj = new java.net.URL(url);
            result.put("protocol", urlObj.getProtocol());
            result.put("host", urlObj.getHost());
            result.put("port", urlObj.getPort());
            result.put("path", urlObj.getPath());
            result.put("query", urlObj.getQuery());
            
            // Détecter le content type
            String detectedContentType = detectContentType(url, null);
            result.put("detectedContentType", detectedContentType);
            
            // Analyser les paramètres pour HLS/DASH
            if (url.toLowerCase().contains(".m3u8")) {
                result.put("streamType", "HLS");
                result.put("suggestedContentType", "application/x-mpegURL");
                result.put("suggestedStreamType", "LIVE");
            } else if (url.toLowerCase().contains(".mpd")) {
                result.put("streamType", "DASH");
                result.put("suggestedContentType", "application/dash+xml");
                result.put("suggestedStreamType", "LIVE");
            } else {
                result.put("streamType", "PROGRESSIVE");
                result.put("suggestedStreamType", "BUFFERED");
            }
            
            // Analyser les tokens/authentification
            if (url.contains("token=")) {
                result.put("hasToken", true);
                result.put("warning", "L'URL contient un token d'authentification. Vérifiez que le Chromecast peut y accéder.");
            } else {
                result.put("hasToken", false);
            }
            
            // Suggérer des alternatives
            JSArray suggestions = new JSArray();
            suggestions.put("Testez l'URL dans un navigateur depuis le même réseau");
            suggestions.put("Vérifiez que l'URL est accessible publiquement");
            suggestions.put("Pour HLS: utilisez contentType 'application/x-mpegURL' et streamType 'LIVE'");
            suggestions.put("Testez avec une URL simple comme 'http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4'");
            result.put("suggestions", suggestions);
            
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        
        pluginCall.resolve(result);
    }

    /**
     * Teste la connectivité réseau et diagnostique les problèmes Cast
     */
    @PluginMethod
    public void networkDiagnostic(PluginCall pluginCall) {
        JSObject result = new JSObject();
        
        try {
            // Vérifier la connectivité réseau générale
            ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            
            if (activeNetwork != null) {
                result.put("networkConnected", true);
                result.put("networkType", activeNetwork.getTypeName());
                result.put("networkState", activeNetwork.getState().toString());
                
                // Vérifier si on est sur WiFi
                boolean isWiFi = activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;
                result.put("isWiFi", isWiFi);
                
                if (!isWiFi) {
                    result.put("warning", "Chromecast nécessite une connexion WiFi. Vous êtes sur " + activeNetwork.getTypeName());
                }
            } else {
                result.put("networkConnected", false);
                result.put("error", "Aucune connexion réseau détectée");
            }
            
            // Vérifier les services Google Play
            try {
                int gmsAvailable = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(getContext());
                result.put("googlePlayServices", gmsAvailable == ConnectionResult.SUCCESS);
                if (gmsAvailable != ConnectionResult.SUCCESS) {
                    result.put("gmsError", "Google Play Services non disponible: " + gmsAvailable);
                }
            } catch (Exception e) {
                result.put("gmsError", "Erreur lors de la vérification Google Play Services: " + e.getMessage());
            }
            
            // Test de base du Cast Context
            try {
                // Vérifier si on peut obtenir le CastContext
                if (connection != null) {
                    result.put("castConnectionAvailable", true);
                    result.put("castContextStatus", "Context available");
                } else {
                    result.put("castConnectionAvailable", false);
                    result.put("castContextStatus", "No connection object");
                }
            } catch (Exception e) {
                result.put("castContextError", e.getMessage());
            }
            
            // Suggestions de dépannage
            JSArray suggestions = new JSArray();
            suggestions.put("1. Redémarrez votre Chromecast (débranchement 30s)");
            suggestions.put("2. Vérifiez que votre Chromecast affiche l'écran d'accueil normal");
            suggestions.put("3. Testez YouTube depuis Google Home pour vérifier la connectivité");
            suggestions.put("4. Assurez-vous que les deux appareils sont sur le même réseau WiFi");
            suggestions.put("5. Désactivez temporairement pare-feu/antivirus");
            suggestions.put("6. Redémarrez votre routeur WiFi");
            suggestions.put("7. Essayez sur un autre réseau (hotspot mobile)");
            result.put("troubleshooting", suggestions);
            
        } catch (Exception e) {
            result.put("error", "Erreur pendant le diagnostic: " + e.getMessage());
            Log.e(TAG, "Erreur networkDiagnostic", e);
        }
        
        pluginCall.resolve(result);
    }

    /**
     * Diagnostic complet du Cast SDK pour identifier les problèmes
     */
    @PluginMethod
    public void fullDiagnostic(PluginCall pluginCall) {
        JSObject result = new JSObject();
        
        try {
            // 1. Vérifier l'App ID dans les SharedPreferences
            android.content.SharedPreferences prefs = getContext().getSharedPreferences("CHROMECAST_SETTINGS", android.content.Context.MODE_PRIVATE);
            String savedAppId = prefs.getString("appId", "NOT_FOUND");
            result.put("savedAppId", savedAppId);
            
            // 2. Vérifier l'App ID par défaut
            String defaultAppId = getContext().getString(R.string.app_id);
            result.put("defaultAppId", defaultAppId);
            
            // 3. Tester la création d'un CastOptionsProvider
            try {
                CastOptionsProvider provider = new CastOptionsProvider();
                com.google.android.gms.cast.framework.CastOptions options = provider.getCastOptions(getContext());
                result.put("castOptionsCreated", true);
                result.put("receiverApplicationId", options.getReceiverApplicationId());
            } catch (Exception e) {
                result.put("castOptionsError", e.getMessage());
            }
            
            // 4. Vérifier l'état du CastContext
            try {
                if (connection != null) {
                    result.put("connectionExists", true);
                    // Pas d'accès direct au CastContext depuis ici, mais on peut vérifier la connection
                } else {
                    result.put("connectionExists", false);
                }
            } catch (Exception e) {
                result.put("connectionError", e.getMessage());
            }
            
            // 5. Vérifier la connectivité réseau
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getContext().getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            
            if (activeNetwork != null) {
                result.put("networkConnected", true);
                result.put("networkType", activeNetwork.getTypeName());
                boolean isWiFi = activeNetwork.getType() == android.net.ConnectivityManager.TYPE_WIFI;
                result.put("isWiFi", isWiFi);
                
                if (!isWiFi) {
                    result.put("networkWarning", "Chromecast nécessite WiFi, vous êtes sur " + activeNetwork.getTypeName());
                }
            } else {
                result.put("networkConnected", false);
            }
            
            // 6. Suggestions de diagnostic
            JSArray nextSteps = new JSArray();
            nextSteps.put("1. Redémarrez COMPLÈTEMENT l'application (pas juste refresh)");
            nextSteps.put("2. Vérifiez que votre Chromecast affiche l'écran d'accueil normal");
            nextSteps.put("3. Testez YouTube depuis Google Home sur le même Chromecast");
            nextSteps.put("4. Redémarrez votre Chromecast (débranchement 30s)");
            nextSteps.put("5. Vérifiez que téléphone et Chromecast sont sur même réseau WiFi");
            nextSteps.put("6. Essayez depuis un autre réseau (hotspot mobile)");
            result.put("nextSteps", nextSteps);
            
        } catch (Exception e) {
            result.put("diagnosticError", e.getMessage());
            Log.e(TAG, "Erreur fullDiagnostic", e);
        }
        
        pluginCall.resolve(result);
    }

    /**
     * Test différentes URLs pour diagnostiquer les problèmes de streaming
     */
    @PluginMethod
    public void testStreamingUrls(PluginCall pluginCall) {
        JSObject result = new JSObject();
        JSArray testUrls = new JSArray();
        
        // URLs de test connues
        String[] urls = {
            "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", // MP4 simple
            "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8", // HLS simple  
            "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8", // HLS test
            "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8" // HLS Bitdash
        };
        
        String[] descriptions = {
            "MP4 simple (Google)",
            "HLS simple (Akamai)", 
            "HLS test (Unified Streaming)",
            "HLS test (Bitdash)"
        };
        
        for (int i = 0; i < urls.length; i++) {
            JSObject testUrl = new JSObject();
            testUrl.put("url", urls[i]);
            testUrl.put("description", descriptions[i]);
            testUrl.put("detectedContentType", detectContentType(urls[i], null));
            
            // Suggérer les paramètres de streaming
            if (urls[i].contains(".m3u8")) {
                testUrl.put("suggestedContentType", "application/x-mpegURL");
                testUrl.put("suggestedStreamType", "LIVE");
            } else {
                testUrl.put("suggestedContentType", "video/mp4");
                testUrl.put("suggestedStreamType", "BUFFERED");
            }
            
            testUrls.put(testUrl);
        }
        
        result.put("testUrls", testUrls);
        result.put("instructions", "Testez ces URLs une par une avec launchMedia pour identifier le problème");
        
        // Instructions de test
        JSArray steps = new JSArray();
        steps.put("1. Testez d'abord le MP4 simple");
        steps.put("2. Si ça marche, testez les HLS");
        steps.put("3. Si tout marche, le problème est votre URL Mux");
        steps.put("4. Vérifiez le token JWT (expiration, IP restrictions)");
        steps.put("5. Testez votre URL Mux depuis un navigateur sur le même réseau");
        result.put("testSteps", steps);
        
        pluginCall.resolve(result);
    }

    /**
     * Méthode pour charger un média HLS sécurisé en utilisant un récepteur personnalisé
     * qui peut gérer l'authentification des segments HLS
     * 
     * @param pluginCall contient les paramètres du média et l'App ID du récepteur personnalisé
     */
    @PluginMethod
    public void loadSecureHLS(final PluginCall pluginCall) {
        String contentId = pluginCall.getString("contentId");
        String customAppId = pluginCall.getString("customAppId");
        JSObject customData = pluginCall.getObject("customData", new JSObject());
        String contentType = pluginCall.getString("contentType", "application/x-mpegURL");
        String streamType = pluginCall.getString("streamType", "LIVE");
        Boolean autoPlay = pluginCall.getBoolean("autoPlay", true);
        JSObject metadata = pluginCall.getObject("metadata", new JSObject());
        String authToken = pluginCall.getString("authToken", "");

        if (contentId == null || contentId.isEmpty()) {
            pluginCall.reject("contentId est requis");
            return;
        }

        // Extraire le token de l'URL si présent
        if (authToken.isEmpty() && contentId.contains("token=")) {
            try {
                String[] urlParts = contentId.split("\\?");
                if (urlParts.length > 1) {
                    String[] params = urlParts[1].split("&");
                    for (String param : params) {
                        if (param.startsWith("token=")) {
                            authToken = param.substring(6);
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to extract token from URL", e);
            }
        }

        // Ajouter les informations d'authentification aux customData
        try {
            customData.put("authToken", authToken);
            customData.put("secureHLS", true);
            customData.put("originalUrl", contentId);
            
            // Ajouter les informations pour le récepteur personnalisé
            customData.put("authType", "url_token");
            customData.put("contentType", contentType);
            
            Log.d(TAG, "=== SECURE HLS DEBUG ===");
            Log.d(TAG, "contentId: " + contentId);
            Log.d(TAG, "authToken: " + (authToken.isEmpty() ? "EMPTY" : "PRESENT"));
            Log.d(TAG, "customAppId: " + customAppId);
            Log.d(TAG, "customData: " + customData.toString());
            Log.d(TAG, "========================");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to prepare secure HLS data", e);
            pluginCall.reject("Failed to prepare secure HLS data: " + e.getMessage());
            return;
        }

        // Note: Pour l'instant, nous n'utilisons pas l'App ID personnalisé
        // car cela nécessite une réinitialisation complète de la connexion
        // TODO: Implémenter le support pour les App IDs personnalisés
        if (customAppId != null && !customAppId.isEmpty()) {
            Log.d(TAG, "Custom App ID provided but not implemented yet: " + customAppId);
            Log.d(TAG, "Using default receiver with custom data for secure HLS");
        }
        
        // Utiliser le récepteur par défaut avec les données personnalisées
        loadMediaWithCustomData(contentId, customData, contentType, streamType, autoPlay, metadata, pluginCall);
    }
    
    /**
     * Méthode helper pour charger un média avec des données personnalisées
     */
    private void loadMediaWithCustomData(String contentId, JSObject customData, String contentType, 
                                       String streamType, boolean autoPlay, JSObject metadata, PluginCall pluginCall) {
        try {
            this.connection.getChromecastSession().loadMedia(
                contentId, 
                customData, 
                contentType, 
                0L, // duration
                streamType, 
                autoPlay, 
                0.0, // currentTime
                metadata, 
                new JSObject(), // textTrackStyle
                pluginCall
            );
        } catch (Exception e) {
            Log.e(TAG, "Failed to load media with custom data", e);
            pluginCall.reject("Failed to load media: " + e.getMessage());
        }
    }
    @PluginMethod
    public void mediaPause(PluginCall call) {
        pause();
        call.resolve();
    }
    
    @PluginMethod
    public void mediaPlay(PluginCall call) {
        play();
        call.resolve();
    }
    @PluginMethod
    public void mediaSeek(PluginCall call) {
        int position = call.getInt("position", 0);
        seek(position);
        //if(client != null) client.seek(position);
        call.resolve();
    }
}
