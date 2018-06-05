/*
 * Copyright (c) 2015, Andrzej Porebski
 * Copyright (c) 2012-2015, Chris Brody
 * Copyright (c) 2005-2010, Nitobi Software Inc.
 * Copyright (c) 2010, IBM Corporation
 */

package io.liteglue;

import android.content.Context;

import com.facebook.common.logging.FLog;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.lang.IllegalArgumentException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

public class SQLitePlugin extends ReactContextBaseJavaModule {

    public static final String TAG = SQLitePlugin.class.getSimpleName();

    /**
     * Multiple database runner map (static).
     * NOTE: no public static accessor to db (runner) map since it would not work with db threading.
     * FUTURE put DBRunner into a public class that can provide external accessor.
     */
    static ConcurrentHashMap<String, DBRunner> dbrmap = new ConcurrentHashMap<String, DBRunner>();

    /**
     * SQLiteGlueConnector (instance of SQLiteConnector) for NDK version:
     */
    static SQLiteConnector connector = new SQLiteConnector();

    protected ExecutorService threadPool;
    private Context context;

    public SQLitePlugin(ReactApplicationContext reactContext) {
        super(reactContext);
        this.context = reactContext.getApplicationContext();
        this.threadPool = Executors.newCachedThreadPool();
    }

    /**
     * Required React Native method
     */
    @Override
    public String getName() {
        return "SQLite";
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

    protected ExecutorService getThreadPool(){
        return this.threadPool;
    }

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
                ReadableArray[] queryParams = null;
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
     * @param dbname   The name of the database file
     */
    private SQLiteAndroidDatabase openDatabase(String dbname, String assetFilePath, int openFlags, CallbackContext cbc, boolean old_impl) throws Exception {
        InputStream in = null;
        File dbfile = null;
        try {
            boolean assetImportError = false;
            boolean assetImportRequested = assetFilePath != null && assetFilePath.length() > 0;
            if (assetImportRequested) {
                if (assetFilePath.compareTo("1") == 0) {
                    assetFilePath = "www/" + dbname;
                    in = this.getContext().getAssets().open(assetFilePath);
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
                        if (openFlags == SQLiteOpenFlags.READONLY) {
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
                openFlags = SQLiteOpenFlags.CREATE | SQLiteOpenFlags.READWRITE;
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

            // Pass in mode to open call
            SQLiteAndroidDatabase mydb = old_impl ? new SQLiteAndroidDatabase() : new SQLiteDatabaseNDK();
            mydb.open(dbfile, openFlags);

            if (cbc != null)
                cbc.success("database open");

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
    private void createFromAssets(String dbName, File dbfile, InputStream assetFileInputStream) throws Exception
    {
        OutputStream out = null;
        try {
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
        } finally {
            closeQuietly(out);
        }
    }

    /**
     * Close a database (in another thread).
     *
     * @param dbname   The name of the database file
     */
    private void closeDatabase(String dbname, CallbackContext cbc) {
        DBRunner r = dbrmap.get(dbname);
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
                cbc.success("couldn't close database");
            }
        }
    }

    /**
     * Close a database (in the current thread).
     *
     * @param dbname   The name of the database file
     */
    private void closeDatabaseNow(String dbname) {
        DBRunner r = dbrmap.get(dbname);

        if (r != null) {
            SQLiteAndroidDatabase mydb = r.mydb;

            if (mydb != null)
                mydb.closeDatabaseNow();
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
            File dbfile = this.getContext().getDatabasePath(dbNameToAttach);
            String filePathToAttached = dbfile.getAbsolutePath();
            String stmt = "ATTACH DATABASE '" + filePathToAttached + "' AS " + dbAlias;
            // TODO: remove qid it's hardcoded in js to be 1111 always anyway
            DBQuery query = new DBQuery(new String[]{stmt}, new String[]{"1111"}, null, cbc);
            try {
                runner.q.put(query);
            } catch (InterruptedException ex) {
                cbc.error("Can't put querry into the queue");
            }
        } else {
            cbc.error("Can't attach to database - it's not open yet");
        }
    }

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
    private boolean deleteDatabaseNow(String dbname) {
        File dbfile = this.getContext().getDatabasePath(dbname);

        try {
            return this.getContext().deleteDatabase(dbfile.getAbsolutePath());
        } catch (Exception ex) {
            FLog.e(TAG, "couldn't delete database", ex);
            return false;
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

    // NOTE: class hierarchy is ugly, done to reduce number of modules for manual installation.
    // FUTURE TBD SQLiteDatabaseNDK class belongs in its own module.
    class SQLiteDatabaseNDK extends SQLiteAndroidDatabase {
        SQLiteConnection mydb;

        /**
         * Open a database.
         *
         * @param dbFile   The database File specification
         */
        @Override
        void open(File dbFile) throws Exception {
            this.open(dbFile, SQLiteOpenFlags.READWRITE | SQLiteOpenFlags.CREATE);
        }

        /**
         * Open a database.
         *
         * @param dbFile   The database File specification
         */
        @Override
        void open(File dbFile, int mode) throws Exception {
            mydb = connector.newSQLiteConnection(dbFile.getAbsolutePath(),mode);
        }

        /**
         * Close a database (in the current thread).
         */
        @Override
        void closeDatabaseNow() {
            try {
                if (mydb != null)
                    mydb.dispose();
            } catch (Exception ex) {
                FLog.e(TAG, "couldn't close database, ignoring", ex);
            }
        }

        /**
         * Ignore Android bug workaround for NDK version
         */
        @Override
        void bugWorkaround() { }

        /**
         * Executes a batch request and sends the results via cbc.
         *
         * @param queryarr   Array of query strings
         * @param queryParams Array of JSON query parameters
         * @param queryIDs   Array of query ids
         * @param cbc        Callback context from Cordova API
         */
        @Override
        void executeSqlBatch( String[] queryarr, ReadableArray[]  queryParams,
                              String[] queryIDs, CallbackContext cbc) {

            if (mydb == null) {
                // not allowed - can only happen if someone has closed (and possibly deleted) a database and then re-used the database
                cbc.error("database has been closed");
                return;
            }

            int len = queryarr.length;
            WritableArray batchResults = Arguments.createArray();

            for (int i = 0; i < len; i++) {
                String query_id = queryIDs[i];

                WritableMap queryResult = null;
                String errorMessage = "unknown";

                try {
                    String query = queryarr[i];

                    long lastTotal = mydb.getTotalChanges();
                    queryResult = this.executeSqlStatementNDK(query, queryParams != null ? queryParams[i] : null, cbc);
                    long newTotal = mydb.getTotalChanges();
                    long rowsAffected = newTotal - lastTotal;

                    queryResult.putDouble("rowsAffected", rowsAffected);
                    if (rowsAffected > 0) {
                        long insertId = mydb.getLastInsertRowid();
                        if (insertId > 0) {
                            queryResult.putDouble("insertId", insertId);
                        }
                    }
                } catch (Exception ex) {
                    errorMessage = ex.getMessage();
                    FLog.e(TAG, "SQLitePlugin.executeSql[Batch]() failed", ex);
                }

                try {
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
                } catch (Exception ex) {
                    FLog.e(TAG, "SQLitePlugin.executeSql[Batch]() failed", ex);
                }
            }

            cbc.success(batchResults);
        }

        /**
         * Get rows results from query cursor.
         *
         * @return results in string form
         */
        private WritableMap executeSqlStatementNDK(String query, ReadableArray queryArgs,
                                                   CallbackContext cbc) throws Exception {
            WritableMap rowsResult = Arguments.createMap();

            boolean hasRows;

            SQLiteStatement myStatement = null;
            try {
                try {
                    myStatement = mydb.prepareStatement(query);
                    if (queryArgs != null) {
                        for (int i = 0; i < queryArgs.size(); ++i) {
                            ReadableType type = queryArgs.getType(i);
                            if (type == ReadableType.Number){
                                double tmp = queryArgs.getDouble(i);
                                if (tmp == (long) tmp) {
                                    myStatement.bindLong(i + 1, (long) tmp);
                                } else {
                                    myStatement.bindDouble(i + 1, tmp);
                                }
                            } else if (queryArgs.isNull(i)) {
                                myStatement.bindNull(i + 1);
                            } else {
                                myStatement.bindTextNativeString(i + 1, SQLitePluginConverter.getString(queryArgs,i,""));
                            }
                        }
                    }

                    hasRows = myStatement.step();
                } catch (Exception ex) {
                    FLog.e(TAG, "SQLitePlugin.executeSql[Batch]() failed", ex);
                    throw ex;
                }

                // If query result has rows
                if (hasRows) {
                    WritableArray rowsArrayResult = Arguments.createArray();
                    String key;
                    int colCount = myStatement.getColumnCount();

                    // Build up JSON result object for each row
                    do {
                        WritableMap row = Arguments.createMap();
                        for (int i = 0; i < colCount; ++i) {
                            key = myStatement.getColumnName(i);

                            switch (myStatement.getColumnType(i)) {
                                case SQLColumnType.NULL:
                                    row.putNull(key);
                                    break;

                                case SQLColumnType.REAL:
                                    row.putDouble(key, myStatement.getColumnDouble(i));
                                    break;

                                case SQLColumnType.INTEGER:
                                    row.putDouble(key, myStatement.getColumnLong(i));
                                    break;

                                case SQLColumnType.BLOB:
                                case SQLColumnType.TEXT:
                                default:
                                    row.putString(key, myStatement.getColumnTextNativeString(i));
                            }

                        }

                        rowsArrayResult.pushMap(row);
                    } while (myStatement.step());

                    rowsResult.putArray("rows", rowsArrayResult);
                }
            } finally {
                if (myStatement != null) {
                    myStatement.dispose();
                }
            }

            return rowsResult;
        }
    }

    private class DBRunner implements Runnable {
        final String dbname;
        private String assetFilename;
        private boolean oldImpl;
        private boolean androidLockWorkaround;
        final int openFlags;
        final BlockingQueue<DBQuery> q;
        final CallbackContext openCbc;

        SQLiteAndroidDatabase mydb;

        DBRunner(final String dbname, ReadableMap options, CallbackContext cbc) {
            this.dbname = dbname;
            int openFlags = SQLiteOpenFlags.CREATE | SQLiteOpenFlags.READWRITE;
            try {
                this.assetFilename = SQLitePluginConverter.getString(options,"assetFilename",null);
                if (this.assetFilename != null && this.assetFilename.length() > 0) {
                    boolean readOnly = SQLitePluginConverter.getBoolean(options,"readOnly",false);
                    openFlags = readOnly ? SQLiteOpenFlags.READONLY : openFlags;
                }
            } catch (Exception ex){
                FLog.e(TAG,"Error retrieving assetFilename or mode from options:",ex);
            }
            this.openFlags = openFlags;
            this.oldImpl = SQLitePluginConverter.getBoolean(options,"androidOldDatabaseImplementation",false);
            FLog.v(TAG, "Android db implementation: " + (oldImpl ? "OLD" : "sqlite4java (NDK)"));
            this.androidLockWorkaround = this.oldImpl && SQLitePluginConverter.getBoolean(options,"androidLockWorkaround",false);
            if (this.androidLockWorkaround)
                FLog.i(TAG, "Android db closing/locking workaround applied");

            this.q = new LinkedBlockingQueue<>();
            this.openCbc = cbc;
        }

        public void run() {
            try {
                this.mydb = openDatabase(dbname, this.assetFilename, this.openFlags, this.openCbc, this.oldImpl);
            } catch (Exception ex) {
                FLog.e(TAG, "Error opening database, stopping db thread", ex);
                if (this.openCbc != null) {
                    this.openCbc.error("Can't open database." + ex);
                }
                dbrmap.remove(dbname);
                return;
            }

            DBQuery dbq = null;

            try {
                dbq = q.take();

                while (!dbq.stop) {
                    mydb.executeSqlBatch(dbq.queries, dbq.queryParams, dbq.queryIDs, dbq.cbc);

                    // NOTE: androidLock[Bug]Workaround is not necessary and IGNORED for sqlite4java (NDK version).
                    if (this.androidLockWorkaround && dbq.queries.length == 1 && dbq.queries[0].equals("COMMIT"))
                        mydb.bugWorkaround();

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
                        dbq.cbc.success("database deleted");
                    } else {
                        try {
                            boolean deleteResult = deleteDatabaseNow(dbname);
                            if (deleteResult) {
                                dbq.cbc.success("database deleted");
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
}

/* vim: set expandtab : */
