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
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import java.io.File;
import java.lang.IllegalArgumentException;
import java.lang.Number;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * @param queryArr   Array of query strings
     * @param queryParams Array of JSON query parameters
     * @param queryIDs   Array of query ids
     * @param cbc        Callback context from Cordova API
     */
    @SuppressLint("NewApi")
    void executeSqlBatch(String[] queryArr, ReadableArray[] queryParams,
                                 String[] queryIDs, CallbackContext cbc) {

        if (mydb == null) {
            // not allowed - can only happen if someone has closed (and possibly deleted) a database and then re-used the database
            cbc.error("database has been closed");
            return;
        }

        String query = "";
        String query_id = "";
        int len = queryArr.length;
        WritableArray batchResults = Arguments.createArray();

        for (int i = 0; i < len; i++) {
            int rowsAffectedCompat = 0;
            boolean needRowsAffectedCompat = false;
            query_id = queryIDs[i];

            WritableMap queryResult = null;
            String errorMessage = "unknown";

            try {
                boolean needRawQuery = true;

                query = queryArr[i];

                QueryType queryType = getQueryType(query);

                if (queryType == QueryType.update || queryType == QueryType.delete) {

                    SQLiteStatement myStatement = mydb.compileStatement(query);

                    if (queryParams != null) {
                        bindArgsToStatement(myStatement, queryParams[i]);
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
                        queryResult = Arguments.createMap();
                        queryResult.putInt("rowsAffected", rowsAffected);
                    }
                }

                // INSERT:
                else if (queryType == QueryType.insert && queryParams != null) {
                    needRawQuery = false;
                    SQLiteStatement myStatement = mydb.compileStatement(query);
                    bindArgsToStatement(myStatement, queryParams[i]);
                    long insertId = -1; // (invalid)

                    try {
                        insertId = myStatement.executeInsert();

                        // statement has finished with no constraint violation:
                        queryResult = Arguments.createMap();
                        if (insertId != -1) {
                            queryResult.putDouble("insertId", insertId);
                            queryResult.putInt("rowsAffected", 1);
                        } else {
                            queryResult.putInt("rowsAffected", 0);
                        }
                    } catch (SQLiteException ex) {
                        // report error result with the error message
                        // could be constraint violation or some other error
                        errorMessage = ex.getMessage();
                        FLog.e(SQLitePlugin.TAG, "SQLiteDatabase.executeInsert() failed", ex);
                    }
                }

                else if (queryType == QueryType.begin) {
                    needRawQuery = false;
                    try {
                        mydb.beginTransaction();

                        queryResult = Arguments.createMap();
                        queryResult.putInt("rowsAffected", 0);
                    } catch (SQLiteException ex) {
                        errorMessage = ex.getMessage();
                        FLog.e(SQLitePlugin.TAG, "SQLiteDatabase.beginTransaction() failed", ex);
                    }
                }

                else if (queryType == QueryType.commit) {
                    needRawQuery = false;
                    try {
                        mydb.setTransactionSuccessful();
                        mydb.endTransaction();

                        queryResult = Arguments.createMap();
                        queryResult.putInt("rowsAffected", 0);
                    } catch (SQLiteException ex) {
                        errorMessage = ex.getMessage();
                        FLog.e(SQLitePlugin.TAG, "SQLiteDatabase.setTransactionSuccessful/endTransaction() failed", ex);
                    }
                }

                else if (queryType == QueryType.rollback) {
                    needRawQuery = false;
                    try {
                        mydb.endTransaction();

                        queryResult = Arguments.createMap();
                        queryResult.putInt("rowsAffected", 0);
                    } catch (SQLiteException ex) {
                        errorMessage = ex.getMessage();
                        FLog.e(SQLitePlugin.TAG, "SQLiteDatabase.endTransaction() failed", ex);
                    }
                }

                // raw query for other statements:
                if (needRawQuery) {
                    queryResult = this.executeSqlStatementQuery(mydb, query, queryParams != null ? queryParams[i] : null, cbc);

                    if (needRowsAffectedCompat) {
                        queryResult.putInt("rowsAffected", rowsAffectedCompat);
                    }
                }
            } catch (Exception ex) {
                errorMessage = ex.getMessage();
                FLog.e(SQLitePlugin.TAG, "SQLiteAndroidDatabase.executeSql[Batch]() failed", ex);
            }

            if (queryResult != null) {
                WritableMap r = Arguments.createMap();
                r.putString("qid", query_id);

                r.putString("type", "success");
                r.putMap("result", queryResult);

                batchResults.pushMap(r);
            } else {
                WritableMap r = Arguments.createMap();
                r.putString("qid", query_id);
                r.putString("type", "error");

                WritableMap er = Arguments.createMap();
                er.putString("message", errorMessage);
                r.putMap("result", er);

                batchResults.pushMap(r);
            }
        }

        cbc.success(batchResults);
    }

    private void bindArgsToStatement(SQLiteStatement myStatement, ReadableArray sqlArgs) {
        if (sqlArgs == null)
            return;

        for (int i = 0; i < sqlArgs.size(); i++) {
            ReadableType type = sqlArgs.getType(i);
            if (type == ReadableType.Number) {
                double tmp = sqlArgs.getDouble(i);
                if (tmp == (long) tmp) {
                    myStatement.bindLong(i + 1, (long) tmp);
                } else {
                    myStatement.bindDouble(i + 1, tmp);
                }
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
    private WritableMap executeSqlStatementQuery(SQLiteDatabase mydb,
                                                String query, ReadableArray queryParams,
                                                CallbackContext cbc) throws Exception {
        WritableMap rowsResult = Arguments.createMap();

        Cursor cur;
        try {
            String[] params = new String[0];
            if (queryParams != null) {
                int size = queryParams.size();
                params = new String[size];
                for (int j = 0; j < size; j++) {
                    if (queryParams.isNull(j))
                        params[j] = "";
                    else
                        params[j] = queryParams.getString(j);
                }
            }
            cur = mydb.rawQuery(query, params);
        } catch (Exception ex) {
            FLog.e(SQLitePlugin.TAG, "SQLiteAndroidDatabase.executeSql[Batch]() failed", ex);
            throw ex;
        }

        // If query result has rows
        if (cur != null && cur.moveToFirst()) {
            WritableArray rowsArrayResult = Arguments.createArray();
            String key = "";
            int colCount = cur.getColumnCount();

            // Build up JSON result object for each row
            do {
                WritableMap row = Arguments.createMap();
                for (int i = 0; i < colCount; ++i) {
                    key = cur.getColumnName(i);
                    bindRow(row, key, cur, i);
                }

                rowsArrayResult.pushMap(row);
            } while (cur.moveToNext());

            rowsResult.putArray("rows", rowsArrayResult);
        }

        if (cur != null) {
            cur.close();
        }

        return rowsResult;
    }

    @SuppressLint("NewApi")
    private void bindRow(WritableMap row, String key, Cursor cur, int i) {
        int curType = cur.getType(i);

        switch (curType) {
            case Cursor.FIELD_TYPE_NULL:
                row.putNull(key);
                break;
            case Cursor.FIELD_TYPE_INTEGER:
                row.putDouble(key, cur.getLong(i));
                break;
            case Cursor.FIELD_TYPE_FLOAT:
                row.putDouble(key, cur.getDouble(i));
                break;
            case Cursor.FIELD_TYPE_BLOB:
                row.putString(key, new String(Base64.encode(cur.getBlob(i), Base64.DEFAULT)));
                break;
            case Cursor.FIELD_TYPE_STRING:
            default:
                row.putString(key, cur.getString(i));
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
