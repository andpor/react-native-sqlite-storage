/*
 * Copyright (c) 2015, Andrzej Porebski
 * Copyright (c) 2012-2015, Chris Brody
 * Copyright (c) 2005-2010, Nitobi Software Inc.
 * Copyright (c) 2010, IBM Corporation
 */

package io.liteglue;

import android.annotation.SuppressLint;

import android.app.Activity;
import android.util.Base64;
import android.util.Log;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;

import java.io.File;
import java.lang.IllegalArgumentException;
import java.lang.Number;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

public class SQLitePlugin extends ReactContextBaseJavaModule {

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

    protected Activity activity = null;
    protected ExecutorService threadPool;

    public SQLitePlugin(ReactApplicationContext reactContext, Activity activity) {
        super(reactContext);
        this.activity = activity;
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

    protected ExecutorService getThreadPool(){
        return this.threadPool;
    }

    protected Activity getActivity(){
        return this.activity;
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
            Log.e(SQLitePlugin.class.getSimpleName(), "unexpected error", e);
            throw(e);
        }

        try {
            return executeAndPossiblyThrow(action, args, cbc);
        } catch (JSONException e) {
            // TODO: signal JSON problem to JS
            Log.e(SQLitePlugin.class.getSimpleName(), "unexpected error", e);
            throw(e);
        }
    }

    private boolean executeAndPossiblyThrow(Action action, JSONArray args, CallbackContext cbc)
            throws JSONException {

        boolean status = true;
        JSONObject o;
        String dbname;

        switch (action) {
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

            case delete:
                o = args.getJSONObject(0);
                dbname = o.getString("path");

                deleteDatabase(dbname, cbc);

                break;

            case executeSqlBatch:
            case backgroundExecuteSqlBatch:
                String[] queries = null;
                String[] queryIDs = null;

                JSONArray jsonArr = null;
                int paramLen = 0;
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
                        paramLen = jsonArr.length();
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
                        Log.e(SQLitePlugin.class.getSimpleName(), "couldn't add to queue", e);
                        cbc.error("couldn't add to queue");
                    }
                } else {
                    cbc.error("database not open");
                }
                break;
        }

        return status;
    }

    /**
     * Clean up and close all open databases.
     * @TODO     @Override
     */
    public void onDestroy() {
        while (!dbrmap.isEmpty()) {
            String dbname = dbrmap.keySet().iterator().next();

            this.closeDatabaseNow(dbname);

            DBRunner r = dbrmap.get(dbname);
            try {
                // stop the db runner thread:
                r.q.put(new DBQuery());
            } catch(Exception e) {
                Log.e(SQLitePlugin.class.getSimpleName(), "couldn't stop db thread", e);
            }
            dbrmap.remove(dbname);
        }
    }

    // --------------------------------------------------------------------------
    // LOCAL METHODS
    // --------------------------------------------------------------------------

    private void startDatabase(String dbname, JSONObject options, CallbackContext cbc) {
        // TODO: is it an issue that we can orphan an existing thread?  What should we do here?
        // If we re-use the existing DBRunner it might be in the process of closing...
        DBRunner r = dbrmap.get(dbname);

        // Brody TODO: It may be better to terminate the existing db thread here & start a new one, instead.
        if (r != null) {
            // don't orphan the existing thread; just re-open the existing database.
            // In the worst case it might be in the process of closing, but even that's less serious
            // than orphaning the old DBRunner.
            cbc.success("database open");
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
    private SQLiteAndroidDatabase openDatabase(String dbname, boolean createFromAssets, CallbackContext cbc, boolean old_impl) throws Exception {
        try {
            // ASSUMPTION: no db (connection/handle) is already stored in the map
            // [should be true according to the code in DBRunner.run()]

            File dbfile = this.getActivity().getDatabasePath(dbname);

            if (!dbfile.exists() && createFromAssets) this.createFromAssets(dbname, dbfile);

            if (!dbfile.exists()) {
                dbfile.getParentFile().mkdirs();
            }

            Log.v("info", "Open sqlite db: " + dbfile.getAbsolutePath());

            SQLiteAndroidDatabase mydb = old_impl ? new SQLiteAndroidDatabase() : new SQLiteDatabaseNDK();
            mydb.open(dbfile);

            if (cbc != null) // XXX Android locking/closing BUG workaround
                cbc.success("database open");

            return mydb;
        } catch (Exception e) {
            if (cbc != null) // XXX Android locking/closing BUG workaround
                cbc.error("can't open database " + e);
            throw e;
        }
    }

    /**
     * If a prepopulated DB file exists in the assets folder it is copied to the dbPath.
     * Only runs the first time the app runs.
     */
    private void createFromAssets(String myDBName, File dbfile)
    {
        InputStream in = null;
        OutputStream out = null;

            try {
                in = this.getActivity().getAssets().open("www/" + myDBName);
                String dbPath = dbfile.getAbsolutePath();
                dbPath = dbPath.substring(0, dbPath.lastIndexOf("/") + 1);

                File dbPathFile = new File(dbPath);
                if (!dbPathFile.exists())
                    dbPathFile.mkdirs();

                File newDbFile = new File(dbPath + myDBName);
                out = new FileOutputStream(newDbFile);

                // XXX TODO: this is very primitive, other alternatives at:
                // http://www.journaldev.com/861/4-ways-to-copy-file-in-java
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0)
                    out.write(buf, 0, len);
    
                Log.v("info", "Copied prepopulated DB content to: " + newDbFile.getAbsolutePath());
            } catch (IOException e) {
                Log.v("createFromAssets", "No prepopulated DB found, Error=" + e.getMessage());
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException ignored) {
                    }
                }
    
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException ignored) {
                    }
                }
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
            } catch(Exception e) {
                if (cbc != null) {
                    cbc.error("couldn't close database" + e);
                }
                Log.e(SQLitePlugin.class.getSimpleName(), "couldn't close database", e);
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

    private void deleteDatabase(String dbname, CallbackContext cbc) {
        DBRunner r = dbrmap.get(dbname);
        if (r != null) {
            try {
                r.q.put(new DBQuery(true, cbc));
            } catch(Exception e) {
                if (cbc != null) {
                    cbc.error("couldn't close database" + e);
                }
                Log.e(SQLitePlugin.class.getSimpleName(), "couldn't close database", e);
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
        File dbfile = this.getActivity().getDatabasePath(dbname);

        try {
            return this.getActivity().deleteDatabase(dbfile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(SQLitePlugin.class.getSimpleName(), "couldn't delete database", e);
            return false;
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
        mydb = connector.newSQLiteConnection(dbFile.getAbsolutePath(),
          SQLiteOpenFlags.READWRITE | SQLiteOpenFlags.CREATE);
      }

      /**
       * Close a database (in the current thread).
       */
      @Override
      void closeDatabaseNow() {
        try {
          if (mydb != null)
            mydb.dispose();
        } catch (Exception e) {
            Log.e(SQLitePlugin.class.getSimpleName(), "couldn't close database, ignoring", e);
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
       * @param jsonparams Array of JSON query parameters
       * @param queryIDs   Array of query ids
       * @param cbc        Callback context from Cordova API
       */
      @Override
      void executeSqlBatch( String[] queryarr, JSONArray[] jsonparams,
                            String[] queryIDs, CallbackContext cbc) {

        if (mydb == null) {
            // not allowed - can only happen if someone has closed (and possibly deleted) a database and then re-used the database
            cbc.error("database has been closed");
            return;
        }

        int len = queryarr.length;
        JSONArray batchResults = new JSONArray();

        for (int i = 0; i < len; i++) {
            int rowsAffectedCompat = 0;
            boolean needRowsAffectedCompat = false;
            String query_id = queryIDs[i];

            JSONObject queryResult = null;
            String errorMessage = "unknown";

            try {
                String query = queryarr[i];

                long lastTotal = mydb.getTotalChanges();
                queryResult = this.executeSqlStatementNDK(query, jsonparams[i], cbc);
                long newTotal = mydb.getTotalChanges();
                long rowsAffected = newTotal - lastTotal;

                queryResult.put("rowsAffected", rowsAffected);
                if (rowsAffected > 0) {
                    long insertId = mydb.getLastInsertRowid();
                    if (insertId > 0) {
                        queryResult.put("insertId", insertId);
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                errorMessage = ex.getMessage();
                Log.v("executeSqlBatch", "SQLitePlugin.executeSql[Batch](): Error=" + errorMessage);
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
                ex.printStackTrace();
                Log.v("executeSqlBatch", "SQLitePlugin.executeSql[Batch](): Error=" + ex.getMessage());
                // TODO what to do?
            }
        }

        cbc.success(batchResults);
      }

      /**
       * Get rows results from query cursor.
       *
       * @return results in string form
       */
      private JSONObject executeSqlStatementNDK(String query, JSONArray paramsAsJson,
                                                CallbackContext cbc) throws Exception {
        JSONObject rowsResult = new JSONObject();

        boolean hasRows = false;

        SQLiteStatement myStatement = mydb.prepareStatement(query);

        try {
            String[] params = null;

            params = new String[paramsAsJson.length()];

            for (int i = 0; i < paramsAsJson.length(); ++i) {
                if (paramsAsJson.isNull(i)) {
                    myStatement.bindNull(i + 1);
                } else {
                    Object p = paramsAsJson.get(i);
                    if (p instanceof Float || p instanceof Double) 
                        myStatement.bindDouble(i + 1, paramsAsJson.getDouble(i));
                    else if (p instanceof Number) 
                        myStatement.bindLong(i + 1, paramsAsJson.getLong(i));
                    else
                        myStatement.bindTextNativeString(i + 1, paramsAsJson.getString(i));
                }
            }

            hasRows = myStatement.step();
        } catch (Exception ex) {
            ex.printStackTrace();
            String errorMessage = ex.getMessage();
            Log.v("executeSqlBatch", "SQLitePlugin.executeSql[Batch](): Error=" + errorMessage);

            // cleanup statement and throw the exception:
            myStatement.dispose();
            throw ex;
        }

        // If query result has rows
        if (hasRows) {
            JSONArray rowsArrayResult = new JSONArray();
            String key = "";
            int colCount = myStatement.getColumnCount();

            // Build up JSON result object for each row
            do {
                JSONObject row = new JSONObject();
                try {
                    for (int i = 0; i < colCount; ++i) {
                        key = myStatement.getColumnName(i);

                        switch (myStatement.getColumnType(i)) {
                        case SQLColumnType.NULL:
                            row.put(key, JSONObject.NULL);
                            break;

                        case SQLColumnType.REAL:
                            row.put(key, myStatement.getColumnDouble(i));
                            break;

                        case SQLColumnType.INTEGER:
                            row.put(key, myStatement.getColumnLong(i));
                            break;

                        case SQLColumnType.BLOB:
                        case SQLColumnType.TEXT:
                        default: // (just in case)
                            row.put(key, myStatement.getColumnTextNativeString(i));
                        }

                    }

                    rowsArrayResult.put(row);

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } while (myStatement.step());

            try {
                rowsResult.put("rows", rowsArrayResult);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        myStatement.dispose();

        return rowsResult;
      }
    }

    private class DBRunner implements Runnable {
        final String dbname;
        private boolean createFromAssets;
        private boolean oldImpl;
        private boolean bugWorkaround;

        final BlockingQueue<DBQuery> q;
        final CallbackContext openCbc;

        SQLiteAndroidDatabase mydb;

        DBRunner(final String dbname, JSONObject options, CallbackContext cbc) {
            this.dbname = dbname;
            this.createFromAssets = options.has("createFromResource");
            this.oldImpl = options.has("androidOldDatabaseImplementation");
            Log.v(SQLitePlugin.class.getSimpleName(), "Android db implementation: " + (oldImpl ? "OLD" : "sqlite4java (NDK)"));
            this.bugWorkaround = this.oldImpl && options.has("androidBugWorkaround");
            if (this.bugWorkaround)
                Log.v(SQLitePlugin.class.getSimpleName(), "Android db closing/locking workaround applied");

            this.q = new LinkedBlockingQueue<DBQuery>();
            this.openCbc = cbc;
        }

        public void run() {
            try {
                this.mydb = openDatabase(dbname, this.createFromAssets, this.openCbc, this.oldImpl);
            } catch (Exception e) {
                Log.e(SQLitePlugin.class.getSimpleName(), "unexpected error, stopping db thread", e);
                dbrmap.remove(dbname);
                return;
            }

            DBQuery dbq = null;

            try {
                dbq = q.take();

                while (!dbq.stop) {
                    mydb.executeSqlBatch(dbq.queries, dbq.jsonparams, dbq.queryIDs, dbq.cbc);

                    // NOTE: androidLock[Bug]Workaround is not necessary and IGNORED for sqlite4java (NDK version).
                    if (this.bugWorkaround && dbq.queries.length == 1 && dbq.queries[0].equals("COMMIT"))
                        mydb.bugWorkaround();

                    dbq = q.take();
                }
            } catch (Exception e) {
                Log.e(SQLitePlugin.class.getSimpleName(), "unexpected error", e);
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
                        } catch (Exception e) {
                            Log.e(SQLitePlugin.class.getSimpleName(), "couldn't delete database", e);
                            dbq.cbc.error("couldn't delete database: " + e);
                        }
                    }                    
                } catch (Exception e) {
                    Log.e(SQLitePlugin.class.getSimpleName(), "couldn't close database", e);
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

    private static enum Action {
        open,
        close,
        delete,
        executeSqlBatch,
        backgroundExecuteSqlBatch,
    }
}

/* vim: set expandtab : */
