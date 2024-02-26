/*
 * sqlite.js
 *
 * This software is largely based on the React Native SQLite Storage library created by Andrzej Porebski. It is modified
 * by Mendix to suit our needs and to support A+ Promises.
 *
 * Copyright (c) 2023 Mendix.
 *
 * Original:
 * Created by Andrzej Porebski on 10/29/15.
 * Copyright (c) 2015-2016 Andrzej Porebski.
 *
 * This software is largely based on the SQLLite Storage Cordova Plugin created by Chris Brody & Davide Bertola.
 * The implementation was adopted and converted to use React Native bindings.
 *
 * See https://github.com/litehelpers/Cordova-sqlite-storage
 *
 * This library is available under the terms of the MIT License (2008).
 * See http://opensource.org/licenses/alphabetical for full text.
 */

const NativeModules = require("react-native").NativeModules;

const READ_ONLY_REGEX = /^(\s|;)*(?:alter|create|delete|drop|insert|reindex|replace|update)/i;

const DB_STATE_INIT = "INIT";

const DB_STATE_OPEN = "OPEN";

const txLocks = {};

function newSQLError(error, code) {
  var sqlError;
  sqlError = error;
  if (!code) {
    code = 0;
  }
  if (!sqlError) {
    sqlError = new Error("a plugin had an error but provided no response");
    sqlError.code = code;
  }
  if (typeof sqlError === "string") {
    sqlError = new Error(error);
    sqlError.code = code;
  }
  if (!sqlError.code && sqlError.message) {
    sqlError.code = code;
  }
  if (!sqlError.code && !sqlError.message) {
    sqlError = new Error("an unknown error was returned: " + JSON.stringify(sqlError));
    sqlError.code = code;
  }
  return sqlError;
}

/*
 Utility that avoids leaking the arguments object. See
 https://www.npmjs.org/package/argsarray
 */

function argsArray(fun) {
  return function() {
    let i = arguments.length;
    if (i) {
      const args = [];
      while (i--) {
        args[i] = arguments[i];
      }
      return fun.call(this, args);
    }
    return fun.call(this, []);
  };
}

const plugin = {
  exec: function(method, options, success, error) {
    if (plugin.DEBUG){
      plugin.log("SQLite." + method + "(" + JSON.stringify(options) + ")");
    }
    NativeModules["SQLite"][method](options,success,error);
  },
  log: function(...messages) {
    if (plugin.DEBUG) {
      console.log(...messages)
    }
  },
  DEBUG: false
};

function SQLitePlugin(openargs) {
  if (!(openargs && openargs["name"])) {
    throw newSQLError("Cannot create a SQLitePlugin db instance without a db name");
  }
  const dbname = openargs.name;
  if (typeof dbname !== "string") {
    throw newSQLError("sqlite plugin database name must be a string");
  }
  this.openargs = openargs;
  this.dbname = dbname;
}

SQLitePlugin.prototype.openDBs = {};

SQLitePlugin.prototype.transaction = async function(fn) {
  return transaction(this, fn, true, false);
};

SQLitePlugin.prototype.readTransaction = async function(fn) {
  return transaction(this, fn, false, true);
};

async function transaction(sqlitePlugin, fn, txLock, readOnly) {
  if (!sqlitePlugin.openDBs[sqlitePlugin.dbname]) {
    return Promise.reject(newSQLError("database not open"));
  }

  if (sqlitePlugin.dbname in sqlitePlugin.openDBs) {
    if (sqlitePlugin.openDBs[sqlitePlugin.dbname] === DB_STATE_INIT) {
      throw new Error("database is still opening");
    }
  } else {
    throw new Error("database is closed!");
  }

  const tx = new SQLitePluginTransaction(sqlitePlugin, txLock, readOnly);
  const txData = txLocks[sqlitePlugin.dbname] ?? (txLocks[sqlitePlugin.dbname] = {
    queue: Promise.resolve(),
    inProgress: false
  });

  // serialize all transactions to avoid parallel transactions on a single database
  const result = txData.queue.then(async () => {
    await tx.start();
    try {
      const result = await fn(tx);
      await tx.finish();
      return result;
    } catch (e) {
      await tx.abort();
      throw e;
    }
  });

  txData.queue = result.catch(() => {});
  return result;
}

SQLitePlugin.prototype.executeSql = function(statement, params) {
  return this.transaction((tx) => tx.executeSql(statement, params));
};

SQLitePlugin.prototype.open = async function() {
  if (this.dbname in this.openDBs && this.openDBs[this.dbname] === DB_STATE_OPEN) {
    plugin.log("database already open: " + this.dbname);
    return Promise.resolve(this);
  }

  plugin.log("OPEN database: " + this.dbname);

  return new Promise((resolve, reject) => {
    const success = (function(_this) {
      return function() {
        if (!_this.openDBs[_this.dbname]) {
          plugin.log("database was closed during open operation");
        }
        if (_this.dbname in _this.openDBs) {
          _this.openDBs[_this.dbname] = DB_STATE_OPEN;
        }
        resolve(_this);
      };
    })(this);

    const failure = (function(_this) {
      return function() {
        plugin.log("OPEN database: " + _this.dbname + " failed");
        delete _this.openDBs[_this.dbname];

        reject(newSQLError("Could not open database"));
      };
    })(this);

    this.openDBs[this.dbname] = DB_STATE_INIT;
    plugin.exec("open", this.openargs, success, failure);
  });
};

SQLitePlugin.prototype.close = async function() {
  if (this.dbname in this.openDBs) {
    if (txLocks[this.dbname] && txLocks[this.dbname].inProgress) {
      plugin.log("cannot close: transaction is in progress");
      return Promise.reject(newSQLError("database cannot be closed while a transaction is in progress"));
    }

    plugin.log("CLOSE database: " + this.dbname);

    delete this.openDBs[this.dbname];
    if (txLocks[this.dbname]) {
      plugin.log("closing db with transaction queue length: " + txLocks[this.dbname].queue.length);
    } else {
      plugin.log("closing db with no transaction lock state");
    }

    return new Promise((resolve, reject) => {
      plugin.exec("close",{path: this.dbname}, resolve, reject);
    });
  }

  const err = "cannot close: database is not open";
  plugin.log(err);
  return Promise.reject(err);
};

SQLitePlugin.prototype.attach = function(dbNameToAttach, dbAlias) {
  if (this.dbname in this.openDBs) {
    if (txLocks[this.dbname] && txLocks[this.dbname].inProgress) {
      plugin.log("cannot attach: transaction is in progress");
      return Promise.reject(newSQLError("database cannot be attached while a transaction is in progress"));
    }

    plugin.log("ATTACH database " + dbNameToAttach + " to " + this.dbname + " with alias " + dbAlias);

    return new Promise((resolve, reject) => {
      plugin.exec("attach",{path: this.dbname, dbName: dbNameToAttach, dbAlias}, resolve, reject);
    });
  }

  const err = "cannot attach: database is not open";
  return Promise.reject(err);
};

SQLitePlugin.prototype.detach = function(dbAlias) {
  if (this.dbname in this.openDBs) {
    if (txLocks[this.dbname] && txLocks[this.dbname].inProgress) {
      plugin.log("cannot attach: transaction is in progress");
      return Promise.reject(newSQLError("database cannot be attached while a transaction is in progress"));
    }

    plugin.log("DETACH database " + dbAlias + " from " + this.dbname);

    return new Promise((resolve, reject) => {
      const myerror = function(e) {
        plugin.log("ERR", e);
        reject(e);
      };
      this.executeSql("DETACH DATABASE " + dbAlias, [], resolve, myerror);
    });
  }

  const err = "cannot attach: database is not open";
  plugin.log(err);
  return Promise.reject(err);
};


function SQLitePluginTransaction(db, txlock, readOnly) {
  this.db = db;
  this.txlock = txlock;
  this.readOnly = readOnly;
  this.queue = Promise.resolve();
  this.finalized = false;
  this.started = false;
}

SQLitePluginTransaction.prototype.start = async function() {
  if (this.started) {
    return Promise.reject({
      message: "InvalidStateError: Transaction already started."
    });
  }
  if (this.txlock) {
    await executeSql(this, this.db.dbname, "BEGIN", []);
  }
  this.started = true;
  return Promise.resolve();
};

SQLitePluginTransaction.prototype.executeSql = function(sql, values) {
  if (!this.started) {
    return Promise.reject({
      message: "InvalidStateError: Transaction not started yet."
    });
  }
  if (this.finalized) {
    return Promise.reject({
      message: "InvalidStateError: DOM Exception 11: This transaction is already finalized. Transactions are committed" +
          " after its success or failure handlers are called.",
      code: 11
    });
  }
  if (this.readOnly && READ_ONLY_REGEX.test(sql)) {
    return Promise.reject({message: "invalid sql for a read-only transaction"});
  }

  return executeSql(this, this.db.dbname, sql, values);
}

async function executeSql(tx, dbname, sql, values) {
  const result = tx.queue.then(() => {
    txLocks[dbname].inProgress = true;

    const sqlStatement = typeof sql === "string" ? sql : sql.toString();
    const params = transformParameters(values);

    return new Promise((resolve, reject) => {
      function success(responseArray) {
        const response = responseArray[0].result;
        const rows = response.rows || [];
        const resultSetRowList = {
          rows: {
            item: function(i) {
              return rows[i];
            },
            /**
             * non-standard Web SQL Database method to expose a copy of raw results
             * @return {Array}
             */
            raw: function() {
              return rows.slice();
            },
            length: rows.length
          },
          rowsAffected: response.rowsAffected || 0,
          insertId: response.insertId || void 0
        };
        txLocks[dbname].inProgress = false;
        resolve([tx, resultSetRowList]);
      }

      function failure(responseArray) {
        const response = responseArray[0].result;
        txLocks[dbname].inProgress = false;
        reject(newSQLError(response));
      }

      plugin.exec("backgroundExecuteSqlBatch",{
        dbargs: { dbname },
        executes: [{
          qid: 1111,
          sql: sqlStatement,
          params: params
        }]
      }, success, failure);
    });
  });
  tx.queue = result.catch(() => {});
  return result;
}

function transformParameters(values) {
  return (!!values && values.constructor === Array)
      ? values.map((v) => {
        const t = typeof v;
        if (v === null || v === void 0 || t === "number" || t === "string"){
          return v;
        }
        if (t === "boolean") {
          //Convert true -> 1 / false -> 0
          return ~~v;
        }
        if (t !== "function") {
          plugin.warn("addStatement - parameter of type <"+t+"> converted to string using toString()")
          return v.toString();
        }
        let errorMsg = "Unsupported parameter type <"+t+"> found in addStatement()";
        plugin.error(errorMsg);
        throw newSQLError(errorMsg);
      })
      : [];
}

SQLitePluginTransaction.prototype.abort = async function() {
  if (!this.started) {
    return Promise.reject({
      message: "InvalidStateError: Transaction not started yet."
    });
  }
  if (this.finalized) {
    return;
  }

  const tx = this;
  tx.finalized = true;
  txLocks[tx.db.dbname].inProgress = false;
  if (this.txlock) {
    try {
      await executeSql(this, this.db.dbname, "ROLLBACK", []);
    } catch (e) {
      return Promise.reject(newSQLError("error while trying to roll back: " + e.message, e.code));
    }
  }
  return tx;
};

SQLitePluginTransaction.prototype.finish = async function() {
  if (!this.started) {
    return Promise.reject({
      message: "InvalidStateError: Transaction not started yet."
    });
  }
  if (this.finalized) {
    return;
  }

  const tx = this;
  this.finalized = true;
  txLocks[tx.db.dbname].inProgress = false;
  if (this.txlock) {
    try {
      await executeSql(this, this.db.dbname, "COMMIT", []);
    } catch (e) {
      return Promise.reject(newSQLError("error while trying to roll back: " + e.message, e.code));
    }
  }
  return tx;
};


dblocations = {
  "default" : "nosync",
  "Documents" : "docs",
  "Library" : "libs",
  "Shared" : "shared"
};

function SQLiteFactory() {}

SQLiteFactory.prototype.DEBUG = function(debug) {
  plugin.log("Setting debug to:",debug);
  plugin.DEBUG = debug;
};

SQLiteFactory.prototype.openDatabase = argsArray(async function(args) {
  if (args.length < 1) {
    return null;
  }
  const first = args[0];
  let openargs;
  if (first.constructor === String) {
    openargs = {
      name: first,
      dblocation : dblocations["default"]
    };
  } else {
    openargs = first;
    const readOnly = !!openargs.readOnly;
    if (!readOnly && (!openargs.location || openargs.location.constructor !== String || !dblocations.hasOwnProperty(openargs.location))) {
      openargs.dblocation = dblocations["default"];
    } else {
      openargs.dblocation = dblocations[openargs.location];
    }

    if (!!openargs.createFromLocation) {
      if (openargs.createFromLocation === 1) {
        openargs.assetFilename = "1";
      } else if (typeof openargs.createFromLocation == "string"){
        openargs.assetFilename = openargs.createFromLocation;
      }
    }

    if (!!openargs.androidDatabaseImplementation && openargs.androidDatabaseImplementation === 2) {
      openargs.androidOldDatabaseImplementation = 1;
    }

    if (!!openargs.androidLockWorkaround && openargs.androidLockWorkaround === 1) {
      openargs.androidBugWorkaround = 1;
    }
  }

  const sqlitePlugin = new SQLitePlugin(openargs);
  try {
    const db = await sqlitePlugin.open();
    plugin.log("DB opened: " + openargs.name);
    return db;
  } catch (e) {
    plugin.log(e.message);
    throw e;
  }
});

SQLiteFactory.prototype.echoTest = function() {
  return new Promise((resolve, reject) => {
    const inputTestValue = "test-string";
    const success = function(testValue) {
      return testValue === inputTestValue ? resolve() : reject(`Mismatch: got: ${testValue} , expected: ${inputTestValue}`);
    };

    plugin.exec("echoStringValue",{value: inputTestValue}, success, reject);
  });
};

SQLiteFactory.prototype.deleteDatabase = function(first) {
  var args = {};
  if (first.constructor === String) {
    args.path = first;
    args.dblocation = dblocations["default"];
  } else {
    if (!(first && first["name"])) {
      throw new Error("Please specify db name via name property");
    }
    args.path = first.name;
    if (!first.location || first.location.constructor !== String || !dblocations.hasOwnProperty(first.location)) {
      args.dblocation = dblocations["default"];
    } else {
      args.dblocation = dblocations[first.location];
    }
  }

  return new Promise((resolve, reject) => {
    const success = function(r) {
      delete SQLitePlugin.prototype.openDBs[args.path];
      return resolve(r);
    };

    plugin.exec("delete", args, success, reject);
  });
};

module.exports = new SQLiteFactory();
