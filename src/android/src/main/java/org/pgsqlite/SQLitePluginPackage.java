/**
 * Written by Andrzej Porebski Nov 14/2015
 *
 * Copyright (c) 2015, Andrzej Porebski
 */
package org.pgsqlite;



import android.app.Activity;

import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.JavaScriptModule;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.uimanager.ViewManager;

import java.util.ArrayList;
import java.util.Collections;

import java.util.List;

public class SQLitePluginPackage implements ReactPackage {
    private Activity activity = null;

    public SQLitePluginPackage(Activity activity){
        this.activity = activity;
    }

    @Override
    public List<NativeModule> createNativeModules(
                                ReactApplicationContext reactContext) {
      List<NativeModule> modules = new ArrayList<>();

      modules.add(new SQLitePlugin(reactContext, activity));

      return modules;
    }

    @Override
    public List<Class<? extends JavaScriptModule>> createJSModules() {
        return Collections.emptyList();
    }

    @Override
    public List<ViewManager> createViewManagers(ReactApplicationContext reactContext) {
      return Collections.emptyList();
    }
}