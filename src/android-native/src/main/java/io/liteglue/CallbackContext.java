/**
 * Written by Andrzej Porebski Nov 14/2015
 *
 * Copyright (c) 2015, Andrzej Porebski
 */
package io.liteglue;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class CallbackContext {

    private static final String LOG_TAG = "SQLitePlugin";

    private Callback successCallback;
    private Callback errorCallback;

    public CallbackContext(Callback success, Callback error) {
        this.successCallback = success;
        this.errorCallback = error;
    }

    /**
     * Helper for success callbacks that just returns the Status.OK by default
     *
     * @param message The message to add to the success result.
     */
    public void success(JSONObject message) {
        try {
            WritableMap writableMap = SQLitePluginConverter.jsonToReact(message);
            successCallback.invoke(writableMap);
        } catch (JSONException ex){
            errorCallback.invoke("Internal error converting results:"+ex.getMessage());
        }
    }

    /**
     * Helper for success callbacks that just returns the Status.OK by default
     *
     * @param message The message to add to the success result.
     */
    public void success(String message) {
        successCallback.invoke(message);
    }

    /**
     * Helper for success callbacks that just returns the Status.OK by default
     *
     * @param message The message to add to the success result.
     */
    public void success(JSONArray message) {
        try {
            WritableArray writableArray = SQLitePluginConverter.jsonToReact(message);
            successCallback.invoke(writableArray);
        } catch (JSONException ex){
            errorCallback.invoke("Internal error converting results:"+ex.getMessage());
        }

    }

    /**
     * Helper for success callbacks that just returns the Status.OK by default
     */
    public void success() {
        successCallback.invoke("Success");
    }

    /**
     * Helper for error callbacks that just returns the Status.ERROR by default
     *
     * @param message The message to add to the error result.
     */
    public void error(JSONObject message) {
        try {
            WritableMap writableMap = SQLitePluginConverter.jsonToReact(message);
            errorCallback.invoke(writableMap);
        } catch (JSONException ex){
            errorCallback.invoke("Internal error converting results:"+ex.getMessage());
        }
    }

    /**
     * Helper for error callbacks that just returns the Status.ERROR by default
     *
     * @param message The message to add to the error result.
     */
    public void error(String message) {
        errorCallback.invoke(message);
    }
}