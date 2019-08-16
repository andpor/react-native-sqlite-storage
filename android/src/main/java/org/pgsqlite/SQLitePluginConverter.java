/**
 * Written by Andrzej Porebski Nov 14/2015
 *
 * Copyright (c) 2015, Andrzej Porebski
 */

package org.pgsqlite;

import com.facebook.react.bridge.NoSuchKeyException;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableType;

public abstract class SQLitePluginConverter {

    /**
     * Returns the value at {@code key} if it exists, coercing it if
     * necessary.
     */
    static String getString(ReadableMap map, String key, String defaultValue) {
        if (map == null){
            return defaultValue;
        }
        try {
            ReadableType type = map.getType(key);
            switch (type) {
                case Number:
                    double value = map.getDouble(key);
                    if (value == (long) value) {
                        return String.valueOf((long) value);
                    } else {
                        return String.valueOf(value);
                    }
                case Boolean:
                    return String.valueOf(map.getBoolean(key));
                case String:
                    return map.getString(key);
                case Null:
                    return null;
                default:
                    return defaultValue;
            }
        } catch(NoSuchKeyException ex){
            return defaultValue;
        }
    }

    /**
     * Returns the value at {@code key} if it exists, coercing it if
     * necessary.
     */
    static boolean getBoolean(ReadableMap map, String key, boolean defaultValue) {
        if (map == null){
            return defaultValue;
        }
        try {
            ReadableType type = map.getType(key);
            switch (type) {
                case Boolean:
                    return map.getBoolean(key);
                case String: {
                    String value = map.getString(key);
                    if ("true".equalsIgnoreCase(value)) {
                        return true;
                    } else if ("false".equalsIgnoreCase(value)) {
                        return false;
                    }
                    return false;
                }
                case Number: {
                    double value = map.getDouble(key);
                    if (value == (long) 0) {
                        return Boolean.FALSE;
                    } else {
                        return Boolean.TRUE;
                    }
                }
                case Null:
                    return false;
                default:
                    return defaultValue;
            }
        } catch(NoSuchKeyException ex){
            return defaultValue;
        }
    }

    /**
     * Returns the value at {@code index} if it exists, coercing it if
     * necessary.
     */
    static String getString(ReadableArray array, int index, String defaultValue) {
        if (array == null){
            return defaultValue;
        }
        try {
            ReadableType type = array.getType(index);
            switch (type) {
                case Number:
                    double value = array.getDouble(index);
                    if (value == (long) value) {
                        return String.valueOf((long) value);
                    } else {
                        return String.valueOf(value);
                    }
                case Boolean:
                    return String.valueOf(array.getBoolean(index));
                case String:
                    return array.getString(index);
                case Null:
                    return null;
                default:
                    return defaultValue;
            }
        } catch(NoSuchKeyException ex){
            return defaultValue;
        }
    }

    /**
     * Returns the value at {@code index} if it exists, coercing it if
     * necessary.
     */
    static boolean getBoolean(ReadableArray array, int index, boolean defaultValue) {
        if (array == null){
            return defaultValue;
        }
        try {
            ReadableType type = array.getType(index);
            switch (type) {
                case Boolean:
                    return array.getBoolean(index);
                case String: {
                    String value = array.getString(index);
                    if ("true".equalsIgnoreCase(value)) {
                        return true;
                    } else if ("false".equalsIgnoreCase(value)) {
                        return false;
                    }
                    return false;
                }
                case Number: {
                    double value = array.getDouble(index);
                    if (value == 0) {
                        return Boolean.FALSE;
                    } else {
                        return Boolean.TRUE;
                    }
                }
                case Null:
                    return false;
                default:
                    return defaultValue;
            }
        } catch(NoSuchKeyException ex){
            return defaultValue;
        }
    }


    static Object get(ReadableMap map,String key,Object defaultValue){
        if (map == null){
            return defaultValue;
        }

        try {
            Object value = null;
            ReadableType type = map.getType(key);
            switch(type){
                case Boolean:
                    value = map.getBoolean(key);
                    break;
                case Number:
                    value = map.getDouble(key);
                    break;
                case String:
                    value = map.getString(key);
                    break;
                case Map:
                    value = map.getMap(key);
                    break;
                case Array:
                    value = map.getArray(key);
                    break;
                case Null:
                    value = null;
                    break;
            }
            return value;
        } catch (NoSuchKeyException ex){
            return defaultValue;
        }
    }

    static Object get(ReadableArray array,int index,Object defaultValue){
        if (array == null){
            return defaultValue;
        }

        try {
            Object value = null;
            ReadableType type = array.getType(index);
            switch(type){
                case Boolean:
                    value = array.getBoolean(index);
                    break;
                case Number:
                    value = array.getDouble(index);
                    break;
                case String:
                    value = array.getString(index);
                    break;
                case Map:
                    value = array.getMap(index);
                    break;
                case Array:
                    value = array.getArray(index);
                    break;
                case Null:
                    break;
            }
            return value;
        } catch (NoSuchKeyException ex){
            return defaultValue;
        }
    }
}
