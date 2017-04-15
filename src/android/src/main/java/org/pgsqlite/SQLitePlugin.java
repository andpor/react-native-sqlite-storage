/*
 * Copyright (c) 2015, Andrzej Porebski
 * Copyright (c) 2012-2015, Chris Brody
 * Copyright (c) 2005-2010, Nitobi Software Inc.
 * Copyright (c) 2010, IBM Corporation
 */

package org.pgsqlite;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteStatement;
import android.content.Context;
import android.util.Base64;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.lang.IllegalArgumentException;
import java.lang.Number;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.facebook.common.logging.FLog;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.Callback;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;


public class SQLitePlugin extends ReactContextBaseJavaModule {

    public static final String TAG = SQLitePlugin.class.getSimpleName();

    private static final String PLUGIN_NAME = "SQLite";

    private static final Pattern FIRST_WORD = Pattern.compile("^\\s*(\\S+)",
            Pattern.CASE_INSENSITIVE);

    /**
     * Multiple database runner map (static).
     * NOTE: no public static accessor to db (runner) map since it would not work with db threading.
     * FUTURE put DBRunner into a public class that can provide external accessor.
     */
    static ConcurrentHashMap<String, DBRunner> dbrmap = new ConcurrentHashMap<String, DBRunner>();

    /**
     * Linked activity
     */
    protected Context context = null;

    /**
     * Thread pool for database operations
     */
    protected ExecutorService threadPool;

    public SQLitePlugin(ReactApplicationContext reactContext) {
        super(reactContext);
        this.context = reactContext.getApplicationContext();
        this.threadPool = Executors.newCachedThreadPool();
    }

    /**
     * Required React Native method - returns the name of this Plugin - SQLitePlugin
     */
    @Override
    public String getName() {
        return PLUGIN_NAME;
    }

    @ReactMethod
    public void open(ReadableMap args, Callback success, Callback error) {
        String actionAsString = "open";
        try {
            JSONArray params = new JSONArray();
            params.put(SQLitePluginConverter.reactToJSON(args));
            this.execute(actionAsString, params, new CallbackContext(success, error));
        } catch (Exception ex){
            error.invoke("Unexpected error:"+ex.getMessage());
        }
    }

    @ReactMethod
    public void close(ReadableMap args, Callback success, Callback error) {
        String actionAsString = "close";
        try {
            JSONArray params = new JSONArray();
            params.put(SQLitePluginConverter.reactToJSON(args));
            this.execute(actionAsString, params, new CallbackContext(success, error));
        } catch (Exception ex){
            error.invoke("Unexpected error"+ex.getMessage());
        }
    }

    @ReactMethod
    public void attach(ReadableMap args, Callback success, Callback error) {
        String actionAsString = "attach";
        try {
            JSONArray params = new JSONArray();
            params.put(SQLitePluginConverter.reactToJSON(args));
            this.execute(actionAsString, params, new CallbackContext(success, error));
        } catch (Exception ex){
            error.invoke("Unexpected error"+ex.getMessage());
        }
    }

    @ReactMethod
    public void delete(ReadableMap args, Callback success, Callback error) {
        String actionAsString = "delete";
        try {
            JSONArray params = new JSONArray();
            params.put(SQLitePluginConverter.reactToJSON(args));
            this.execute(actionAsString, params, new CallbackContext(success, error));
        } catch (Exception ex){
            error.invoke("Unexpected error"+ex.getMessage());
        }
    }

    @ReactMethod
    public void backgroundExecuteSqlBatch(ReadableMap args, Callback success, Callback error) {
        String actionAsString = "backgroundExecuteSqlBatch";
        try {
            JSONArray params = new JSONArray();
            params.put(SQLitePluginConverter.reactToJSON(args));
            this.execute(actionAsString, params, new CallbackContext(success, error));
        } catch (Exception ex){
            error.invoke("Unexpected error"+ex.getMessage());
        }
    }

    @ReactMethod
    public void executeSqlBatch(ReadableMap args, Callback success, Callback error) {
        String actionAsString = "executeSqlBatch";
        try {
            JSONArray params = new JSONArray();
            params.put(SQLitePluginConverter.reactToJSON(args));
            this.execute(actionAsString, params, new CallbackContext(success, error));
        } catch (Exception ex){
            error.invoke("Unexpected error");
        }
    }

    @ReactMethod
    public void echoStringValue(ReadableMap args, Callback success, Callback error) {
        String actionAsString = "echoStringValue";
        try {
            JSONArray params = new JSONArray();
            params.put(SQLitePluginConverter.reactToJSON(args));
            this.execute(actionAsString, params, new CallbackContext(success, error));
        } catch (Exception ex){
            error.invoke("Unexpected error");
        }
    }


    /**
     *
     * @return the thread pool available for scheduling background execution
     */
    protected ExecutorService getThreadPool(){
        return this.threadPool;
    }

    /**
     *
     * @return linked activity
     */
    protected Context getContext(){
        return this.context;
    }

    /**
     * Executes the request and returns PluginResult.
     *
     * @param actionAsString The action to execute.
     * @param args   JSONArray of arguments for the plugin.
     * @param cbc    Callback context from Cordova API
     * @return       Whether the action was valid.
     */
    protected boolean execute(String actionAsString, JSONArray args, CallbackContext cbc) throws Exception{

        Action action;
        try {
            action = Action.valueOf(actionAsString);
        } catch (IllegalArgumentException e) {
            // shouldn't ever happen
            FLog.e(TAG, "unexpected error", e);
            throw(e);
        }

        try {
            return executeAndPossiblyThrow(action, args, cbc);
        } catch (JSONException e) {
            // TODO: signal JSON problem to JS
            FLog.e(TAG, "unexpected error", e);
            throw(e);
        }
    }

    private boolean executeAndPossiblyThrow(Action action, JSONArray args, CallbackContext cbc)
            throws JSONException {

        JSONObject o;
        String dbname;

        switch (action) {
            case echoStringValue:
                o = args.getJSONObject(0);
                String echo_value = o.getString("value");
                cbc.success(echo_value);
                break;

            case open:
                o = args.getJSONObject(0);
                dbname = o.getString("name");
                // open database and start reading its queue
                this.startDatabase(dbname, o, cbc);
                break;

            case close:
                o = args.getJSONObject(0);
                dbname = o.getString("path");
                // put request in the q to close the db
                this.closeDatabase(dbname, cbc);
                break;

            case attach:
                o = args.getJSONObject(0);
                dbname = o.getString("path");

                // attach database
                this.attachDatabase(dbname, o.getString("dbName"), o.getString("dbAlias"), cbc);
                break;

            case delete:
                o = args.getJSONObject(0);
                dbname = o.getString("path");

                deleteDatabase(dbname, cbc);

                break;

            case executeSqlBatch:
            case backgroundExecuteSqlBatch:
                String[] queries;
                String[] queryIDs = null;

                JSONArray jsonArr;
                JSONArray[] jsonparams = null;

                JSONObject allargs = args.getJSONObject(0);
                JSONObject dbargs = allargs.getJSONObject("dbargs");
                dbname = dbargs.getString("dbname");
                JSONArray txargs = allargs.getJSONArray("executes");

                if (txargs.isNull(0)) {
                    queries = new String[0];
                } else {
                    int len = txargs.length();
                    queries = new String[len];
                    queryIDs = new String[len];
                    jsonparams = new JSONArray[len];

                    for (int i = 0; i < len; i++) {
                        JSONObject a = txargs.getJSONObject(i);
                        queries[i] = a.getString("sql");
                        queryIDs[i] = a.getString("qid");
                        jsonArr = a.getJSONArray("params");
                        jsonparams[i] = jsonArr;
                    }
                }

                // put db query in the queue to be executed in the db thread:
                DBQuery q = new DBQuery(queries, queryIDs, jsonparams, cbc);
                DBRunner r = dbrmap.get(dbname);
                if (r != null) {
                    try {
                        r.q.put(q);
                    } catch(Exception e) {
                        FLog.e(TAG, "couldn't add to queue", e);
                        cbc.error("couldn't add to queue");
                    }
                } else {
                    cbc.error("database not open");
                }
                break;
        }

        return true;
    }

    /**
     * Clean up and close all open databases.
     */
    public void closeAllOpenDatabases() {
        while (!dbrmap.isEmpty()) {
            String dbname = dbrmap.keySet().iterator().next();

            this.closeDatabaseNow(dbname);

            DBRunner r = dbrmap.get(dbname);
            try {
                // stop the db runner thread:
                r.q.put(new DBQuery());
            } catch(Exception ex) {
                FLog.e(TAG, "couldn't stop db thread for db: " + dbname,ex);
            }
            dbrmap.remove(dbname);
        }
    }

    // --------------------------------------------------------------------------
    // LOCAL METHODS
    // --------------------------------------------------------------------------

    /**
     *
     * @param dbname - The name of the database file
     * @param options - options passed in from JS
     * @param cbc - JS callback context
     */
    private void startDatabase(String dbname, JSONObject options, CallbackContext cbc) {
        // TODO: is it an issue that we can orphan an existing thread?  What should we do here?
        // If we re-use the existing DBRunner it might be in the process of closing...
        DBRunner r = dbrmap.get(dbname);

        // Brody TODO: It may be better to terminate the existing db thread here & start a new one, instead.
        if (r != null) {
            // don't orphan the existing thread; just re-open the existing database.
            // In the worst case it might be in the process of closing, but even that's less serious
            // than orphaning the old DBRunner.
            cbc.success("database started");
        } else {
            r = new DBRunner(dbname, options, cbc);
            dbrmap.put(dbname, r);
            this.getThreadPool().execute(r);
        }
    }

    /**
     * Open a database.
     *
     * @param dbname - The name of the database file
     * @param assetFilePath - path to the pre-populated database file
     * @param openFlags - the db open options
     * @param cbc - JS callback
     * @return instance of SQLite database
     * @throws Exception
     */
    private SQLiteDatabase openDatabase(String dbname, String assetFilePath, int openFlags, CallbackContext cbc) throws Exception {
        InputStream in = null;
        File dbfile = null;
        try {
            SQLiteDatabase database = this.getDatabase(dbname);
            if (database != null && database.isOpen()) {
                //this only happens when DBRunner is cycling the db for the locking work around.
                // otherwise, this should not happen - should be blocked at the execute("open") level
                if (cbc != null) cbc.error("database already open");
                throw new Exception("database already open");
            }

            if (assetFilePath != null && assetFilePath.length() > 0) {
                if (assetFilePath.compareTo("1") == 0) {
                    assetFilePath = "www/" + dbname;
                    in = this.getContext().getAssets().open(assetFilePath);
                    FLog.v(TAG, "Located pre-populated DB asset in app bundle www subdirectory: " + assetFilePath);
                } else if (assetFilePath.charAt(0) == '~') {
                    assetFilePath = assetFilePath.startsWith("~/") ? assetFilePath.substring(2) : assetFilePath.substring(1);
                    in = this.getContext().getAssets().open(assetFilePath);
                    FLog.v(TAG, "Located pre-populated DB asset in app bundle subdirectory: " + assetFilePath);
                } else {
                    File filesDir = this.getContext().getFilesDir();
                    assetFilePath = assetFilePath.startsWith("/") ? assetFilePath.substring(1) : assetFilePath;
                    File assetFile = new File(filesDir, assetFilePath);
                    in = new FileInputStream(assetFile);
                    FLog.v(TAG, "Located pre-populated DB asset in Files subdirectory: " + assetFile.getCanonicalPath());
                    if (openFlags == SQLiteDatabase.OPEN_READONLY) {
                        dbfile = assetFile;
                        FLog.v(TAG, "Detected read-only mode request for external asset.");
                    }
                }
            }

            if (dbfile == null) {
                openFlags = SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.CREATE_IF_NECESSARY;
                dbfile = this.getContext().getDatabasePath(dbname);

                if (!dbfile.exists() && in != null) {
                    FLog.v(TAG, "Copying pre-populated db asset to destination");
                    this.createFromAssets(dbname, dbfile, in);
                }

                if (!dbfile.exists()) {
                    dbfile.getParentFile().mkdirs();
                }
            }

            FLog.v(TAG, "Opening sqlite db: " + dbfile.getAbsolutePath());

            SQLiteDatabase mydb = SQLiteDatabase.openDatabase(dbfile.getAbsolutePath(), null, openFlags);

            if (cbc != null) // needed for Android locking/closing workaround
                cbc.success("database open");

            return mydb;
        } catch (SQLiteException ex) {
            if (cbc != null) // needed for Android locking/closing workaround
                cbc.error("can't open database " + ex);
            throw ex;
        } finally {
           closeQuietly(in);
        }
    }

    /**
     * If a prepopulated DB file exists in the assets folder it is copied to the dbPath.
     * Only runs the first time the app runs.
     *
     * @param dbName The name of the database file - could be used as filename for imported asset
     * @param dbfile The File of the destination db
     * @param assetFileInputStream input file stream for pre-populated db asset
     */
    private void createFromAssets(String dbName, File dbfile, InputStream assetFileInputStream) {
        OutputStream out = null;

        try {
            FLog.v(TAG, "Copying pre-populated DB content");
            String dbPath = dbfile.getAbsolutePath();
            dbPath = dbPath.substring(0, dbPath.lastIndexOf("/") + 1);

            File dbPathFile = new File(dbPath);
            if (!dbPathFile.exists())
                dbPathFile.mkdirs();

            File newDbFile = new File(dbPath + dbName);
            out = new FileOutputStream(newDbFile);

            // XXX TODO: this is very primitive, other alternatives at:
            // http://www.journaldev.com/861/4-ways-to-copy-file-in-java
            byte[] buf = new byte[1024];
            int len;
            while ((len = assetFileInputStream.read(buf)) > 0)
                out.write(buf, 0, len);

            FLog.v(TAG, "Copied pre-populated DB content to: " + newDbFile.getAbsolutePath());
        } catch (IOException e) {
            FLog.e(TAG, "No pre-populated DB found.", e);
        } finally {
            closeQuietly(out);
        }
    }

    /**
     * Close a database (in another thread).
     *
     * @param dbName - The name of the database file
     * @param cbc - JS callback
     */
    private void closeDatabase(String dbName, CallbackContext cbc) {
        DBRunner r = dbrmap.get(dbName);
        if (r != null) {
            try {
                r.q.put(new DBQuery(false, cbc));
            } catch(Exception e) {
                if (cbc != null) {
                    cbc.error("couldn't close database" + e);
                }
                FLog.e(TAG, "couldn't close database", e);
            }
        } else {
            if (cbc != null) {
                cbc.success("database closed");
            }
        }
    }

    /**
     * Close a database (in the current thread).
     *
     * @param dbName   The name of the database file
     */
    private void closeDatabaseNow(String dbName) {
        SQLiteDatabase mydb = this.getDatabase(dbName);

        if (mydb != null) {
            mydb.close();
        }
    }

    /**
     * Attach a database
     *
     * @param dbName - The name of the database file
     * @param dbNameToAttach - The name of the database file to attach
     * @param dbAlias - The alias of the attached database
     * @param cbc - JS callback
     */
    private void attachDatabase(String dbName, String dbNameToAttach, String dbAlias, CallbackContext cbc) {
        DBRunner runner = dbrmap.get(dbName);
        if (runner != null) {
            File databasePath = this.getContext().getDatabasePath(dbNameToAttach);
            String filePathToAttached = databasePath.getAbsolutePath();
            String statement = "ATTACH DATABASE '" + filePathToAttached + "' AS " + dbAlias;
            // TODO: get rid of qid as it's just hardcoded to 1111 in js layer
            DBQuery query = new DBQuery(new String [] {statement},
                    new String[] {"1111"}, new JSONArray[] {new JSONArray()}, cbc);
            try {
                runner.q.put(query);
            } catch (InterruptedException e) {
                cbc.error("Can't put query in the queue. Interrupted.");
            }
        } else {
            cbc.error("Database " + dbName + "i s not created yet");
        }
    }

    /**
     *
     * @param dbname - The name of the database file
     * @param cbc - callback
     */
    private void deleteDatabase(String dbname, CallbackContext cbc) {
        DBRunner r = dbrmap.get(dbname);
        if (r != null) {
            try {
                r.q.put(new DBQuery(true, cbc));
            } catch(Exception e) {
                if (cbc != null) {
                    cbc.error("couldn't close database" + e);
                }
                FLog.e(TAG, "couldn't close database", e);
            }
        } else {
            boolean deleteResult = this.deleteDatabaseNow(dbname);
            if (deleteResult) {
                cbc.success("database deleted");
            } else {
                cbc.error("couldn't delete database");
            }
        }
    }

    /**
     * Delete a database.
     *
     * @param dbname   The name of the database file
     *
     * @return true if successful or false if an exception was encountered
     */
    @SuppressLint("NewApi")
    private boolean deleteDatabaseNow(String dbname) {
        File dbfile = this.getContext().getDatabasePath(dbname);
        return android.database.sqlite.SQLiteDatabase.deleteDatabase(dbfile);
    }

    /**
     * Get a database from the db map.
     *
     * @param dbname The name of the database.
     */
    private SQLiteDatabase getDatabase(String dbname) {
        DBRunner r = dbrmap.get(dbname);
        return (r == null) ? null :  r.mydb;
    }

    /**
     * Executes a batch request and sends the results via cbc.
     *
     * @param dbname     The name of the database.
     * @param queryarr   Array of query strings
     * @param jsonparams Array of JSON query parameters
     * @param queryIDs   Array of query ids
     * @param cbc        Callback context from Cordova API
     */
    @SuppressLint("NewApi")
    private void executeSqlBatch(String dbname, String[] queryarr, JSONArray[] jsonparams,
                                 String[] queryIDs, CallbackContext cbc) {

        SQLiteDatabase mydb = getDatabase(dbname);

        if (mydb == null) {
            // not allowed - can only happen if someone has closed (and possibly deleted) a database and then re-used the database
            cbc.error("database has been closed");
            return;
        }

        String query;
        String query_id;
        int len = queryarr.length;
        JSONArray batchResults = new JSONArray();

        for (int i = 0; i < len; i++) {
            query_id = queryIDs[i];

            JSONObject queryResult = null;
            String errorMessage = "unknown";

            try {
                boolean needRawQuery = true;

                query = queryarr[i];

                QueryType queryType = getQueryType(query);

                if (queryType == QueryType.update || queryType == QueryType.delete) {
                    SQLiteStatement myStatement = null;
                    int rowsAffected = -1; // (assuming invalid)

                    try {
                        myStatement = mydb.compileStatement(query);
                        if (jsonparams != null) {
                            bindArgsToStatement(myStatement, jsonparams[i]);
                        }

                        rowsAffected = myStatement.executeUpdateDelete();
                        // Indicate valid results:
                        needRawQuery = false;
                    } catch (SQLiteException ex) {
                        // Indicate problem & stop this query:
                        errorMessage = ex.getMessage();
                        FLog.e(TAG, "SQLiteStatement.executeUpdateDelete() failed", ex);
                        needRawQuery = false;
                    } finally {
                        closeQuietly(myStatement);
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

                    long insertId; // (invalid) = -1

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
                        FLog.e(TAG, "SQLiteDatabase.executeInsert() failed", ex);
                    } finally {
                       closeQuietly(myStatement);
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
                        FLog.e(TAG, "SQLiteDatabase.beginTransaction() failed", ex);
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
                        FLog.e(TAG, "SQLiteDatabase.setTransactionSuccessful/endTransaction() failed", ex);
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
                        FLog.e(TAG, "SQLiteDatabase.endTransaction() failed", ex);
                    }
                }

                // raw query for other statements:
                if (needRawQuery) {
                    queryResult = this.executeSqlStatementQuery(mydb, query, jsonparams[i], cbc);
                }
            } catch (Exception ex) {
                errorMessage = ex.getMessage();
                FLog.e(TAG, "SQLitePlugin.executeSql[Batch](): failed", ex);
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
                FLog.e(TAG, "SQLitePlugin.executeSql[Batch]() failed", ex);
            }
        }

        cbc.success(batchResults);
    }

    private QueryType getQueryType(String query) {
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
     * Execute Sql Statement Query
     *
     * @param mydb - database
     * @param query - SQL query to execute
     * @param paramsAsJson - parameters to the query
     * @param cbc - callback object
     *
     * @return results in string form
     */
    private JSONObject executeSqlStatementQuery(SQLiteDatabase mydb,
                                                String query, JSONArray paramsAsJson,
                                                CallbackContext cbc) throws Exception {
        JSONObject rowsResult = new JSONObject();

        Cursor cur = null;
        try {
            try {
                String[] params;

                params = new String[paramsAsJson.length()];

                for (int j = 0; j < paramsAsJson.length(); j++) {
                    if (paramsAsJson.isNull(j)) {
                        params[j] = "";
                    } else {
                        params[j] = paramsAsJson.getString(j);
                    }
                }

                cur = mydb.rawQuery(query, params);
            } catch (Exception ex) {
                FLog.e(TAG, "SQLitePlugin.executeSql[Batch]() failed", ex);
                throw ex;
            }

            // If query result has rows
            if (cur != null && cur.moveToFirst()) {
                JSONArray rowsArrayResult = new SQLiteArray(cur.getCount());
                String key;
                int colCount = cur.getColumnCount();

                // Build up JSON result object for each row
                do {
                    JSONObject row = new SQLiteObject(colCount);
                    try {
                        for (int i = 0; i < colCount; ++i) {
                            key = cur.getColumnName(i);
                            bindRow(row, key, cur, i);
                        }

                        rowsArrayResult.put(row);

                    } catch (JSONException e) {
                        FLog.e(TAG, e.getMessage(), e);
                    }
                } while (cur.moveToNext());

                try {
                    rowsResult.put("rows", rowsArrayResult);
                } catch (JSONException e) {
                   FLog.e(TAG, e.getMessage(), e);
                }
            }
        } finally {
            closeQuietly(cur);
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

    private void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private class DBRunner implements Runnable {
        final String dbname;
        final int openFlags;
        private String assetFilename;
        private boolean androidLockWorkaround;
        final BlockingQueue<DBQuery> q;
        final CallbackContext openCbc;

        SQLiteDatabase mydb;

        DBRunner(final String dbname, JSONObject options, CallbackContext cbc) {
            this.dbname = dbname;
            int openFlags = SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.CREATE_IF_NECESSARY;
            try {
                this.assetFilename = options.has("assetFilename") ? options.getString("assetFilename") : null;
                if (this.assetFilename != null && this.assetFilename.length() > 0) {
                    boolean readOnly = options.has("readOnly") && options.getBoolean("readOnly");
                    openFlags = readOnly ? SQLiteDatabase.OPEN_READONLY : openFlags;
                }
            } catch (Exception ex){
                FLog.e(TAG,"Error retrieving assetFilename this.mode from options:",ex);
            }
            this.openFlags = openFlags;
            this.androidLockWorkaround = options.has("androidLockWorkaround");
            if (this.androidLockWorkaround)
                FLog.i(TAG, "Android db closing/locking workaround applied");

            this.q = new LinkedBlockingQueue<DBQuery>();
            this.openCbc = cbc;
        }

        public void run() {
            try {
                this.mydb = openDatabase(dbname, this.assetFilename, this.openFlags, this.openCbc);
            } catch (Exception e) {
                FLog.e(TAG, "unexpected error, stopping db thread", e);
                dbrmap.remove(dbname);
                return;
            }

            DBQuery dbq = null;

            try {
                dbq = q.take();

                while (!dbq.stop) {
                    executeSqlBatch(dbname, dbq.queries, dbq.jsonparams, dbq.queryIDs, dbq.cbc);

                    // XXX workaround for Android locking/closing issue:
                    if (androidLockWorkaround && dbq.queries.length == 1 && dbq.queries[0].equals("COMMIT")) {
                        // FLog.v(TAG, "close and reopen db");
                        closeDatabaseNow(dbname);
                        this.mydb = openDatabase(dbname, "", this.openFlags, null);
                        // FLog.v(TAG, "close and reopen db finished");
                    }

                    dbq = q.take();
                }
            } catch (Exception e) {
                FLog.e(TAG, "unexpected error", e);
            }

            if (dbq != null && dbq.close) {
                try {
                    closeDatabaseNow(dbname);

                    dbrmap.remove(dbname); // (should) remove ourself

                    if (!dbq.delete) {
                        dbq.cbc.success("database removed");
                    } else {
                        try {
                            boolean deleteResult = deleteDatabaseNow(dbname);
                            if (deleteResult) {
                                dbq.cbc.success("database removed");
                            } else {
                                dbq.cbc.error("couldn't delete database");
                            }
                        } catch (Exception e) {
                            FLog.e(TAG, "couldn't delete database", e);
                            dbq.cbc.error("couldn't delete database: " + e);
                        }
                    }
                } catch (Exception e) {
                    FLog.e(TAG, "couldn't close database", e);
                    if (dbq.cbc != null) {
                        dbq.cbc.error("couldn't close database: " + e);
                    }
                }
            }
        }
    }

    private final class DBQuery {
        // XXX TODO replace with DBRunner action enum:
        final boolean stop;
        final boolean close;
        final boolean delete;
        final String[] queries;
        final String[] queryIDs;
        final JSONArray[] jsonparams;
        final CallbackContext cbc;

        DBQuery(String[] myqueries, String[] qids, JSONArray[] params, CallbackContext c) {
            this.stop = false;
            this.close = false;
            this.delete = false;
            this.queries = myqueries;
            this.queryIDs = qids;
            this.jsonparams = params;
            this.cbc = c;
        }

        DBQuery(boolean delete, CallbackContext cbc) {
            this.stop = true;
            this.close = true;
            this.delete = delete;
            this.queries = null;
            this.queryIDs = null;
            this.jsonparams = null;
            this.cbc = cbc;
        }

        // signal the DBRunner thread to stop:
        DBQuery() {
            this.stop = true;
            this.close = false;
            this.delete = false;
            this.queries = null;
            this.queryIDs = null;
            this.jsonparams = null;
            this.cbc = null;
        }
    }

    private enum Action {
        open,
        close,
        attach,
        delete,
        executeSqlBatch,
        backgroundExecuteSqlBatch,
        echoStringValue
    }

    private enum QueryType {
        update,
        insert,
        delete,
        select,
        begin,
        commit,
        rollback,
        other
    }
}
