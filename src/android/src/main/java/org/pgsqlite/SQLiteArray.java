package org.pgsqlite;

import org.json.JSONArray;

import java.lang.reflect.Field;
import java.util.ArrayList;

/**
 * Created by aporebsk on 11/28/15.
 *
 * Overwrite default JSONArray implementation to allow creation of object using initial size
 */
public class SQLiteArray extends JSONArray {
    public SQLiteArray(){
        super();
    }

    public SQLiteArray(int size){
        super();
        try {
            Field valuesField = JSONArray.class.getDeclaredField("values");
            valuesField.setAccessible(true);
            valuesField.set(this, new ArrayList<>(size));
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

}
