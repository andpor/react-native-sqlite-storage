package org.pgsqlite;

import com.facebook.react.bridge.ReactApplicationContext;

public abstract class SQLitePluginSpec extends NativeSQLiteSpec {
    public SQLitePluginSpec(ReactApplicationContext reactContext) {
        super(reactContext);
    }
}
