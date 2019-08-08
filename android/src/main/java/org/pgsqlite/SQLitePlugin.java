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
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.facebook.common.logging.FLog;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

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
            this.execute(actionAsString, args, new CallbackContext(success, error));
        } catch (Exception ex){
            error.invoke("Unexpected error:"+ex.getMessage());
        }
    }

    @ReactMethod
    public void close(ReadableMap args, Callback success, Callback error) {
        String actionAsString = "close";
        try {
            this.execute(actionAsString, args, new CallbackContext(success, error));
        } catch (Exception ex){
            error.invoke("Unexpected error"+ex.getMessage());
        }
    }

    @ReactMethod
    public void attach(ReadableMap args, Callback success, Callback error) {
        String actionAsString = "attach";
        try {
            this.execute(actionAsString, args, new CallbackContext(success, error));
        } catch (Exception ex){
            error.invoke("Unexpected error"+ex.getMessage());
        }
    }

    @ReactMethod
    public void delete(ReadableMap args, Callback success, Callback error) {
        String actionAsString = "delete";
        try {
            this.execute(actionAsString, args, new CallbackContext(success, error));
        } catch (Exception ex){
            error.invoke("Unexpected error"+ex.getMessage());
        }
    }

    @ReactMethod
    public void backgroundExecuteSqlBatch(ReadableMap args, Callback success, Callback error) {
        String actionAsString = "backgroundExecuteSqlBatch";
        try {
            this.execute(actionAsString, args, new CallbackContext(success, error));
        } catch (Exception ex){
            error.invoke("Unexpected error"+ex.getMessage());
        }
    }

    @ReactMethod
    public void executeSqlBatch(ReadableMap args, Callback success, Callback error) {
        String actionAsString = "executeSqlBatch";
        try {
            this.execute(actionAsString, args, new CallbackContext(success, error));
        } catch (Exception ex){
            error.invoke("Unexpected error");
        }
    }

    @ReactMethod
    public void echoStringValue(ReadableMap args, Callback success, Callback error) {
        String actionAsString = "echoStringValue";
        try {
            this.execute(actionAsString, args, new CallbackContext(success, error));
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
    protected boolean execute(String actionAsString, ReadableMap args, CallbackContext cbc) throws Exception{

        Action action;
        try {
            action = Action.valueOf(actionAsString);
        } catch (IllegalArgumentException ex) {
            // shouldn't ever happen
            FLog.e(TAG, "unexpected error", ex);
            cbc.error("Unexpected error executing processing SQLite query");
            throw ex;
        }
        try {
            return executeAndPossiblyThrow(action, args, cbc);
        } catch (Exception ex) {
            FLog.e(TAG, "unexpected error", ex);
            cbc.error("Unexpected error executing processing SQLite query");
            throw ex;
        }
    }

    private boolean executeAndPossiblyThrow(Action action, ReadableMap args, CallbackContext cbc){
        String dbname;

        switch (action) {
            case echoStringValue:
                String echo_value = SQLitePluginConverter.getString(args,"value","");
                cbc.success(echo_value);
                break;

            case open:
                dbname = SQLitePluginConverter.getString(args,"name","");
                // open database and start reading its queue
                this.startDatabase(dbname, args, cbc);
                break;

            case close:
                dbname = SQLitePluginConverter.getString(args,"path","");
                // put request in the q to close the db
                this.closeDatabase(dbname, cbc);
                break;

            case attach:
                dbname = SQLitePluginConverter.getString(args,"path","");
                String dbAlias = SQLitePluginConverter.getString(args,"dbAlias","");
                String dbNameToAttach = SQLitePluginConverter.getString(args,"dbName","");
                this.attachDatabase(dbname,dbNameToAttach,dbAlias,cbc);
                break;

            case delete:
                dbname = SQLitePluginConverter.getString(args,"path","");
                deleteDatabase(dbname, cbc);
                break;

            case executeSqlBatch:
            case backgroundExecuteSqlBatch:
                String [] queries;
                String [] queryIDs = null;
                ReadableArray [] queryParams = null;
                ReadableMap dbArgs = (ReadableMap) SQLitePluginConverter.get(args,"dbargs",null);
                dbname = SQLitePluginConverter.getString(dbArgs,"dbname","");
                ReadableArray txArgs = (ReadableArray) SQLitePluginConverter.get(args,"executes",null);

                if (txArgs.isNull(0)) {
                    queries = new String[0];
                } else {
                    int len = txArgs.size();
                    queries = new String[len];
                    queryIDs = new String[len];
                    queryParams = new ReadableArray[len];

                    for (int i = 0; i < len; i++) {
                        ReadableMap queryArgs = (ReadableMap) SQLitePluginConverter.get(txArgs,i,null);
                        queries[i] = SQLitePluginConverter.getString(queryArgs,"sql","");
                        queryIDs[i] = SQLitePluginConverter.getString(queryArgs,"qid","");
                        queryParams[i] = (ReadableArray) SQLitePluginConverter.get(queryArgs,"params",null);
                    }
                }

                // put db query in the queue to be executed in the db thread:
                DBQuery q = new DBQuery(queries, queryIDs, queryParams, cbc);
                DBRunner r = dbrmap.get(dbname);
                if (r != null) {
                    try {
                        r.q.put(q);
                    } catch(Exception ex) {
                        FLog.e(TAG, "couldn't add to queue", ex);
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
    private void startDatabase(String dbname, ReadableMap options, CallbackContext cbc) {
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
                // this only happens when DBRunner is cycling the db for the locking work around.
                // otherwise, this should not happen - should be blocked at the execute("open") level
                throw new Exception("Database already open");
            }

            boolean assetImportError = false;
            boolean assetImportRequested = assetFilePath != null && assetFilePath.length() > 0;
            if (assetImportRequested) {
                if (assetFilePath.compareTo("1") == 0) {
                    assetFilePath = "www/" + dbname;
                    try {
                        in = this.getContext().getAssets().open(assetFilePath);
                        FLog.v(TAG, "Pre-populated DB asset FOUND  in app bundle www subdirectory: " + assetFilePath);
                    } catch (Exception ex){
                        assetImportError = true;
                        FLog.e(TAG, "pre-populated DB asset NOT FOUND in app bundle www subdirectory: " + assetFilePath);
                    }
                } else if (assetFilePath.charAt(0) == '~') {
                    assetFilePath = assetFilePath.startsWith("~/") ? assetFilePath.substring(2) : assetFilePath.substring(1);
                    try {
                        in = this.getContext().getAssets().open(assetFilePath);
                        FLog.v(TAG, "Pre-populated DB asset FOUND in app bundle subdirectory: " + assetFilePath);
                    } catch (Exception ex){
                        assetImportError = true;
                        FLog.e(TAG, "pre-populated DB asset NOT FOUND in app bundle www subdirectory: " + assetFilePath);
                    }
                } else {
                    File filesDir = this.getContext().getFilesDir();
                    assetFilePath = assetFilePath.startsWith("/") ? assetFilePath.substring(1) : assetFilePath;
                    try {
                        File assetFile = new File(filesDir, assetFilePath);
                        in = new FileInputStream(assetFile);
                        FLog.v(TAG, "Pre-populated DB asset FOUND in Files subdirectory: " + assetFile.getCanonicalPath());
                        if (openFlags == SQLiteDatabase.OPEN_READONLY) {
                            dbfile = assetFile;
                            FLog.v(TAG, "Detected read-only mode request for external asset.");
                        }
                    } catch (Exception ex){
                        assetImportError = true;
                        FLog.e(TAG, "Error opening pre-populated DB asset in app bundle www subdirectory: " + assetFilePath);
                    }
                }
            }

            if (dbfile == null) {
                openFlags = SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.CREATE_IF_NECESSARY;
                dbfile = this.getContext().getDatabasePath(dbname);

                if (!dbfile.exists() && assetImportRequested) {
                    if (assetImportError || in == null) {
                        FLog.e(TAG, "Unable to import pre-populated db asset");
                        throw new Exception("Unable to import pre-populated db asset");
                    } else {
                        FLog.v(TAG, "Copying pre-populated db asset to destination");
                        try {
                            this.createFromAssets(dbname, dbfile, in);
                        } catch (Exception ex){
                            FLog.e(TAG, "Error importing pre-populated DB asset", ex);
                            throw new Exception("Error importing pre-populated DB asset");
                        }
                    }
                }

                if (!dbfile.exists()) {
                    dbfile.getParentFile().mkdirs();
                }
            }

            FLog.v(TAG, "DB file is ready, proceeding to OPEN SQLite DB: " + dbfile.getAbsolutePath());

            SQLiteDatabase mydb = SQLiteDatabase.openDatabase(dbfile.getAbsolutePath(), null, openFlags);

            if (cbc != null)
                cbc.success("Database opened");

            return mydb;
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
    private void createFromAssets(String dbName, File dbfile, InputStream assetFileInputStream) throws Exception {
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

            FLog.v(TAG, "Copied pre-populated DB asset to: " + newDbFile.getAbsolutePath());
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
            } catch(Exception ex) {
                if (cbc != null) {
                    cbc.error("couldn't close database" + ex);
                }
                FLog.e(TAG, "couldn't close database", ex);
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
            DBQuery query = new DBQuery(new String [] {statement}, new String[] {"1111"}, null, cbc);
            try {
                runner.q.put(query);
            } catch (InterruptedException ex) {
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
            } catch(Exception ex) {
                if (cbc != null) {
                    cbc.error("couldn't close database" + ex);
                }
                FLog.e(TAG, "couldn't close database", ex);
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
     * @param queries   Array of query strings
     * @param queryParams Array of JSON query parameters
     * @param queryIDs   Array of query ids
     * @param cbc        Callback context from Cordova API
     */
    @SuppressLint("NewApi")
    private void executeSqlBatch(String dbname, String[] queries, ReadableArray[] queryParams,
                                 String[] queryIDs, CallbackContext cbc) {

        SQLiteDatabase mydb = getDatabase(dbname);

        if (mydb == null) {
            // not allowed - can only happen if someone has closed (and possibly deleted) a database and then re-used the database
            cbc.error("database has been closed");
            return;
        }

        String query;
        String query_id;
        int len = queries.length;
        WritableArray batchResults = Arguments.createArray();

        for (int i = 0; i < len; i++) {
            query_id = queryIDs[i];

            WritableMap queryResult = null;
            String errorMessage = "unknown";

            try {
                boolean needRawQuery = true;
                query = queries[i];
                QueryType queryType = getQueryType(query);

                if (queryType == QueryType.update || queryType == QueryType.delete) {
                    SQLiteStatement myStatement = null;
                    int rowsAffected = -1; // (assuming invalid)

                    try {
                        myStatement = mydb.compileStatement(query);
                        if (queryParams != null) {
                            bindArgsToStatement(myStatement, queryParams[i]);
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
                        queryResult = Arguments.createMap();
                        queryResult.putInt("rowsAffected", rowsAffected);
                    }
                }

                // INSERT:
                else if (queryType == QueryType.insert && queryParams != null) {
                    FLog.d("executeSqlBatch","INSERT");
                    needRawQuery = false;

                    SQLiteStatement myStatement = mydb.compileStatement(query);

                    bindArgsToStatement(myStatement, queryParams[i]);

                    long insertId; // (invalid) = -1

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
                        FLog.e(TAG, "SQLiteDatabase.executeInsert() failed", ex);
                    } finally {
                        closeQuietly(myStatement);
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
                        FLog.e(TAG, "SQLiteDatabase.beginTransaction() failed", ex);
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
                        FLog.e(TAG, "SQLiteDatabase.setTransactionSuccessful/endTransaction() failed", ex);
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
                        FLog.e(TAG, "SQLiteDatabase.endTransaction() failed", ex);
                    }
                }

                // raw query for other statements:
                if (needRawQuery) {
                    queryResult = this.executeSqlStatementQuery(mydb, query, queryParams != null ? queryParams[i] : null, cbc);
                }
            } catch (Exception ex) {
                errorMessage = ex.getMessage();
                FLog.e(TAG, "SQLitePlugin.executeSql[Batch](): failed", ex);
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

    private void bindArgsToStatement(SQLiteStatement myStatement, ReadableArray sqlArgs) {
        for (int i = 0; i < sqlArgs.size(); i++) {
            ReadableType type = sqlArgs.getType(i);
            if (type == ReadableType.Number){
                double tmp = sqlArgs.getDouble(i);
                if (tmp == (long) tmp) {
                    myStatement.bindLong(i + 1, (long) tmp);
                } else {
                    myStatement.bindDouble(i + 1, tmp);
                }
            } else if (sqlArgs.isNull(i)) {
                myStatement.bindNull(i + 1);
            } else {
                myStatement.bindString(i + 1, SQLitePluginConverter.getString(sqlArgs,i,""));
            }
        }
    }

    /**
     * Execute Sql Statement Query
     *
     * @param mydb - database
     * @param query - SQL query to execute
     * @param queryParams - parameters to the query
     * @param cbc - callback object
     *
     * @throws Exception
     * @return results in string form
     */
    private WritableMap executeSqlStatementQuery(SQLiteDatabase mydb,
                                                 String query, ReadableArray queryParams,
                                                 CallbackContext cbc) throws Exception {
        WritableMap rowsResult = Arguments.createMap();

        Cursor cur = null;
        try {
            try {
                String[] params = new String[0];
                if (queryParams != null) {
                    int size = queryParams.size();
                    params = new String[size];
                    for (int j = 0; j < size; j++) {
                        if (queryParams.isNull(j)) {
                            params[j] = "";
                        } else {
                            params[j] = SQLitePluginConverter.getString(queryParams, j, "");
                        }
                    }
                }

                cur = mydb.rawQuery(query, params);
            } catch (Exception ex) {
                FLog.e(TAG, "SQLitePlugin.executeSql[Batch]() failed", ex);
                throw ex;
            }

            // If query result has rows
            if (cur != null && cur.moveToFirst()) {
                WritableArray rowsArrayResult = Arguments.createArray();
                String key;
                int colCount = cur.getColumnCount();

                // Build up result object for each row
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
        } finally {
            closeQuietly(cur);
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
            default: /* (not expected) */
                row.putString(key, cur.getString(i));
                break;
        }
    }

    private void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ex) {
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

        DBRunner(final String dbname, ReadableMap options, CallbackContext cbc) {
            this.dbname = dbname;
            int openFlags = SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.CREATE_IF_NECESSARY;
            try {
                this.assetFilename = SQLitePluginConverter.getString(options,"assetFilename",null);
                if (this.assetFilename != null && this.assetFilename.length() > 0) {
                    boolean readOnly = SQLitePluginConverter.getBoolean(options,"readOnly",false);
                    openFlags = readOnly ? SQLiteDatabase.OPEN_READONLY : openFlags;
                }
            } catch (Exception ex){
                FLog.e(TAG,"Error retrieving assetFilename or mode from options:",ex);
            }
            this.openFlags = openFlags;
            this.androidLockWorkaround = SQLitePluginConverter.getBoolean(options,"androidLockWorkaround",false);
            if (this.androidLockWorkaround)
                FLog.i(TAG, "Android db closing/locking workaround applied");

            this.q = new LinkedBlockingQueue<DBQuery>();
            this.openCbc = cbc;
        }

        public void run() {
            try {
                this.mydb = openDatabase(dbname, this.assetFilename, this.openFlags, this.openCbc);
            } catch (SQLiteException ex) {
                FLog.e(TAG, "SQLite error opening database, stopping db thread", ex);
                if (this.openCbc != null) {
                    this.openCbc.error("Can't open database." + ex);
                }
                dbrmap.remove(dbname);
                return;
            } catch (Exception ex) {
                FLog.e(TAG, "Unexpected error opening database, stopping db thread", ex);
                if (openCbc != null) {
                    openCbc.error("Can't open database." + ex);
                }
                dbrmap.remove(dbname);
                return;
            }

            DBQuery dbq = null;

            try {
                dbq = q.take();

                while (!dbq.stop) {
                    executeSqlBatch(dbname, dbq.queries, dbq.queryParams, dbq.queryIDs, dbq.cbc);

                    // XXX workaround for Android locking/closing issue:
                    if (androidLockWorkaround && dbq.queries.length == 1 && dbq.queries[0].equals("COMMIT")) {
                        // FLog.v(TAG, "close and reopen db");
                        closeDatabaseNow(dbname);
                        this.mydb = openDatabase(dbname, "", this.openFlags, null);
                        // FLog.v(TAG, "close and reopen db finished");
                    }

                    dbq = q.take();
                }
            } catch (Exception ex) {
                FLog.e(TAG, "unexpected error", ex);
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
                        } catch (Exception ex) {
                            FLog.e(TAG, "couldn't delete database", ex);
                            dbq.cbc.error("couldn't delete database: " + ex);
                        }
                    }
                } catch (Exception ex) {
                    FLog.e(TAG, "couldn't close database", ex);
                    if (dbq.cbc != null) {
                        dbq.cbc.error("couldn't close database: " + ex);
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
        final ReadableArray[] queryParams;
        final CallbackContext cbc;

        DBQuery(String[] myqueries, String[] qids, ReadableArray[] params, CallbackContext c) {
            this.stop = false;
            this.close = false;
            this.delete = false;
            this.queries = myqueries;
            this.queryIDs = qids;
            this.queryParams = params;
            this.cbc = c;
        }

        DBQuery(boolean delete, CallbackContext cbc) {
            this.stop = true;
            this.close = true;
            this.delete = delete;
            this.queries = null;
            this.queryIDs = null;
            this.queryParams = null;
            this.cbc = cbc;
        }

        // signal the DBRunner thread to stop:
        DBQuery() {
            this.stop = true;
            this.close = false;
            this.delete = false;
            this.queries = null;
            this.queryIDs = null;
            this.queryParams = null;
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
