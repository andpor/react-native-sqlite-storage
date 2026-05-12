/**
 * Written by Andrzej Porebski Nov 14/2015
 *
 * Copyright (c) 2015, Andrzej Porebski
 */
package org.pgsqlite;

import android.app.Activity;

import com.facebook.react.TurboReactPackage;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.module.model.ReactModuleInfo;
import com.facebook.react.module.model.ReactModuleInfoProvider;

import java.util.HashMap;
import java.util.Map;

public class SQLitePluginPackage extends TurboReactPackage {

    /**
     * @deprecated activity parameter is ignored. Kept for binary compatibility
     * with older consumers that still pass an Activity reference.
     */
    @Deprecated
    public SQLitePluginPackage(Activity activity) {
        this();
    }

    public SQLitePluginPackage() {}

    @Override
    public NativeModule getModule(String name, ReactApplicationContext reactContext) {
        if ("SQLite".equals(name)) {
            return new SQLitePlugin(reactContext);
        }
        return null;
    }

    @Override
    public ReactModuleInfoProvider getReactModuleInfoProvider() {
        return () -> {
            Map<String, ReactModuleInfo> map = new HashMap<>();
            boolean isTurboModule = BuildConfig.IS_NEW_ARCHITECTURE_ENABLED;
            map.put(
                "SQLite",
                new ReactModuleInfo(
                    "SQLite",
                    "org.pgsqlite.SQLitePlugin",
                    false,  // canOverrideExistingModule
                    false,  // needsEagerInit
                    false,  // hasConstants
                    false,  // isCxxModule
                    isTurboModule
                )
            );
            return map;
        };
    }
}
