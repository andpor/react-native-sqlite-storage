package org.pgsqlite;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;

public abstract class SQLitePluginSpec extends ReactContextBaseJavaModule {
    public SQLitePluginSpec(ReactApplicationContext reactContext) {
        super(reactContext);
    }
}
