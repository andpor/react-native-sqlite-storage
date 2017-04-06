/*
 * Copyright (c) 2015, Andrzej Porebski
 * Copyright (c) 2012-2015, Chris Brody
 * Copyright (c) 2005-2010, Nitobi Software Inc.
 * Copyright (c) 2010, IBM Corporation
 */

package io.liteglue;

import android.annotation.SuppressLint;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteStatement;

import android.util.Base64;

import com.facebook.common.logging.FLog;

import java.io.File;
import java.lang.IllegalArgumentException;
import java.lang.Number;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Android Database helper class
 */
class SQLiteAndroidDatabase {
    private static final Pattern FIRST_WORD = Pattern.compile("^\\s*(\\S+)",
            Pattern.CASE_INSENSITIVE);


    File dbFile;
    int openFlags;
    SQLiteDatabase mydb;

    /**
     * NOTE: Using default constructor, no explicit constructor.
     */

    /**
     * Open a database.
     *
     * @param dbfile   The database File specification
     */
    void open(File dbfile) throws Exception {
        this.open(dbfile,SQLiteOpenFlags.READWRITE | SQLiteOpenFlags.CREATE);
    }

    /**
     * Open a database.
     *
     * @param dbfile   The database File specification
     */
    void open(File dbfile, int openFlags) throws Exception {
        this.dbFile = dbfile; // for possible bug workaround
        this.openFlags = openFlags;
        this.mydb = SQLiteDatabase.openDatabase(dbfile.getAbsolutePath(), null, openFlags);
    }

    /**
     * Close a database (in the current thread).
     */
    void closeDatabaseNow() {
        if (mydb != null) {
            mydb.close();
            mydb = null;
        }
    }

    void bugWorkaround() throws Exception {
        this.closeDatabaseNow();
        this.open(this.dbFile,this.openFlags);
    }

    /**
     * Executes a batch request and sends the results via cbc.
     *
     * @param queryarr   Array of query strings
     * @param jsonparams Array of JSON query parameters
     * @param queryIDs   Array of query ids
     * @param cbc        Callback context from Cordova API
     */
    @SuppressLint("NewApi")
    void executeSqlBatch(String[] queryarr, JSONArray[] jsonparams,
                                 String[] queryIDs, CallbackContext cbc) {

        if (mydb == null) {
            // not allowed - can only happen if someone has closed (and possibly deleted) a database and then re-used the database
            cbc.error("database has been closed");
            return;
        }

        String query = "";
        String query_id = "";
        int len = queryarr.length;
        JSONArray batchResults = new JSONArray();

        for (int i = 0; i < len; i++) {
            int rowsAffectedCompat = 0;
            boolean needRowsAffectedCompat = false;
            query_id = queryIDs[i];

            JSONObject queryResult = null;
            String errorMessage = "unknown";

            try {
                boolean needRawQuery = true;

                query = queryarr[i];

                QueryType queryType = getQueryType(query);

                if (queryType == QueryType.update || queryType == QueryType.delete) {

                    SQLiteStatement myStatement = mydb.compileStatement(query);

                    if (jsonparams != null) {
                        bindArgsToStatement(myStatement, jsonparams[i]);
                    }

                    int rowsAffected = -1; // (assuming invalid)

                    try {
                        rowsAffected = myStatement.executeUpdateDelete();
                        // Indicate valid results:
                        needRawQuery = false;
                    } catch (SQLiteException ex) {
                        // Indicate problem & stop this query:
                        errorMessage = ex.getMessage();
                        FLog.e(SQLitePlugin.TAG, "SQLiteStatement.executeUpdateDelete() failed", ex);
                        needRawQuery = false;
                    }

                    if (rowsAffected != -1) {
                        queryResult = new JSONObject();
                        queryResult.put("rowsAffected", rowsAffected);
                    }
                }

                // INSERT:
                if (queryType == QueryType.insert && jsonparams != null) {
                    needRawQuery = false;

                    SQLiteStatement myStatement = mydb.compileStatement(query);

                    bindArgsToStatement(myStatement, jsonparams[i]);

                    long insertId = -1; // (invalid)

                    try {
                        insertId = myStatement.executeInsert();

                        // statement has finished with no constraint violation:
                        queryResult = new JSONObject();
                        if (insertId != -1) {
                            queryResult.put("insertId", insertId);
                            queryResult.put("rowsAffected", 1);
                        } else {
                            queryResult.put("rowsAffected", 0);
                        }
                    } catch (SQLiteException ex) {
                        // report error result with the error message
                        // could be constraint violation or some other error
                        errorMessage = ex.getMessage();
                        FLog.e(SQLitePlugin.TAG, "SQLiteDatabase.executeInsert() failed", ex);
                    }
                }

                if (queryType == QueryType.begin) {
                    needRawQuery = false;
                    try {
                        mydb.beginTransaction();

                        queryResult = new JSONObject();
                        queryResult.put("rowsAffected", 0);
                    } catch (SQLiteException ex) {
                        errorMessage = ex.getMessage();
                        FLog.e(SQLitePlugin.TAG, "SQLiteDatabase.beginTransaction() failed", ex);
                    }
                }

                if (queryType == QueryType.commit) {
                    needRawQuery = false;
                    try {
                        mydb.setTransactionSuccessful();
                        mydb.endTransaction();

                        queryResult = new JSONObject();
                        queryResult.put("rowsAffected", 0);
                    } catch (SQLiteException ex) {
                        errorMessage = ex.getMessage();
                        FLog.e(SQLitePlugin.TAG, "SQLiteDatabase.setTransactionSuccessful/endTransaction() failed", ex);
                    }
                }

                if (queryType == QueryType.rollback) {
                    needRawQuery = false;
                    try {
                        mydb.endTransaction();

                        queryResult = new JSONObject();
                        queryResult.put("rowsAffected", 0);
                    } catch (SQLiteException ex) {
                        errorMessage = ex.getMessage();
                        FLog.e(SQLitePlugin.TAG, "SQLiteDatabase.endTransaction() failed", ex);
                    }
                }

                // raw query for other statements:
                if (needRawQuery) {
                    queryResult = this.executeSqlStatementQuery(mydb, query, jsonparams[i], cbc);

                    if (needRowsAffectedCompat) {
                        queryResult.put("rowsAffected", rowsAffectedCompat);
                    }
                }
            } catch (Exception ex) {
                errorMessage = ex.getMessage();
                FLog.e(SQLitePlugin.TAG, "SQLiteAndroidDatabase.executeSql[Batch]() failed", ex);
            }

            try {
                if (queryResult != null) {
                    JSONObject r = new JSONObject();
                    r.put("qid", query_id);

                    r.put("type", "success");
                    r.put("result", queryResult);

                    batchResults.put(r);
                } else {
                    JSONObject r = new JSONObject();
                    r.put("qid", query_id);
                    r.put("type", "error");

                    JSONObject er = new JSONObject();
                    er.put("message", errorMessage);
                    r.put("result", er);

                    batchResults.put(r);
                }
            } catch (JSONException ex) {
                FLog.e(SQLitePlugin.TAG, "SQLiteAndroidDatabase.executeSql[Batch]() failed", ex);
            }
        }

        cbc.success(batchResults);
    }

    private void bindArgsToStatement(SQLiteStatement myStatement, JSONArray sqlArgs) throws JSONException {
        for (int i = 0; i < sqlArgs.length(); i++) {
            if (sqlArgs.get(i) instanceof Float || sqlArgs.get(i) instanceof Double) {
                myStatement.bindDouble(i + 1, sqlArgs.getDouble(i));
            } else if (sqlArgs.get(i) instanceof Number) {
                myStatement.bindLong(i + 1, sqlArgs.getLong(i));
            } else if (sqlArgs.isNull(i)) {
                myStatement.bindNull(i + 1);
            } else {
                myStatement.bindString(i + 1, sqlArgs.getString(i));
            }
        }
    }

    /**
     * Get rows results from query cursor.
     *
     * @return results in string form
     */
    private JSONObject executeSqlStatementQuery(SQLiteDatabase mydb,
                                                String query, JSONArray paramsAsJson,
                                                CallbackContext cbc) throws Exception {
        JSONObject rowsResult = new JSONObject();

        Cursor cur = null;
        try {
            String[] params = null;

            params = new String[paramsAsJson.length()];

            for (int j = 0; j < paramsAsJson.length(); j++) {
                if (paramsAsJson.isNull(j))
                    params[j] = "";
                else
                    params[j] = paramsAsJson.getString(j);
            }

            cur = mydb.rawQuery(query, params);
        } catch (Exception ex) {
            FLog.e(SQLitePlugin.TAG, "SQLiteAndroidDatabase.executeSql[Batch]() failed", ex);
            throw ex;
        }

        // If query result has rows
        if (cur != null && cur.moveToFirst()) {
            JSONArray rowsArrayResult = new JSONArray();
            String key = "";
            int colCount = cur.getColumnCount();

            // Build up JSON result object for each row
            do {
                JSONObject row = new JSONObject();
                try {
                    for (int i = 0; i < colCount; ++i) {
                        key = cur.getColumnName(i);
                        bindRow(row, key, cur, i);
                    }

                    rowsArrayResult.put(row);

                } catch (JSONException e) {
                    FLog.w(SQLitePlugin.TAG, e.getMessage(), e);
                }
            } while (cur.moveToNext());

            try {
                rowsResult.put("rows", rowsArrayResult);
            } catch (JSONException e) {
                FLog.e(SQLitePlugin.TAG, e.getMessage(), e);
            }
        }

        if (cur != null) {
            cur.close();
        }

        return rowsResult;
    }

    @SuppressLint("NewApi")
    private void bindRow(JSONObject row, String key, Cursor cur, int i) throws JSONException {
        int curType = cur.getType(i);

        switch (curType) {
            case Cursor.FIELD_TYPE_NULL:
                row.put(key, JSONObject.NULL);
                break;
            case Cursor.FIELD_TYPE_INTEGER:
                row.put(key, cur.getLong(i));
                break;
            case Cursor.FIELD_TYPE_FLOAT:
                row.put(key, cur.getDouble(i));
                break;
            case Cursor.FIELD_TYPE_BLOB:
                row.put(key, new String(Base64.encode(cur.getBlob(i), Base64.DEFAULT)));
                break;
            case Cursor.FIELD_TYPE_STRING:
            default: /* (not expected) */
                row.put(key, cur.getString(i));
                break;
        }
    }

    static QueryType getQueryType(String query) {
        Matcher matcher = FIRST_WORD.matcher(query);
        if (matcher.find()) {
            try {
                return QueryType.valueOf(matcher.group(1).toLowerCase(Locale.US));
            } catch (IllegalArgumentException ignore) {
                // unknown verb
            }
        }
        return QueryType.other;
    }

    static enum QueryType {
        update,
        insert,
        delete,
        select,
        begin,
        commit,
        rollback,
        other
    }
} /* vim: set expandtab : */
