/*
 * sqlite.ios.core.js
 *
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

var NativeModules = require('react-native').NativeModules;
var DB_STATE_INIT, DB_STATE_OPEN, READ_ONLY_REGEX, SQLiteFactory, SQLitePlugin, SQLitePluginTransaction, argsArray, dblocations, newSQLError, root, txLocks;

var plugin = {};

READ_ONLY_REGEX = /^(\s|;)*(?:alter|create|delete|drop|insert|reindex|replace|update)/i;

DB_STATE_INIT = "INIT";

DB_STATE_OPEN = "OPEN";

txLocks = {};

newSQLError = function(error, code) {
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
};

let nextTick = setImmediate ||  function(fun) {
  setTimeout(fun, 0);
};
if (global.window) {
  nextTick = window.setImmediate || function(fun) {
    window.setTimeout(fun, 0);
  };  
}

/*
 Utility that avoids leaking the arguments object. See
 https://www.npmjs.org/package/argsarray
 */

argsArray = function(fun) {
  return function() {
    var args, i, len;
    len = arguments.length;
    if (len) {
      args = [];
      i = -1;
      while (++i < len) {
        args[i] = arguments[i];
      }
      return fun.call(this, args);
    } else {
      return fun.call(this, []);
    }
  };
};

plugin.exec = function(method, options, success, error) {
  if (plugin.sqlitePlugin.DEBUG){
    plugin.log('SQLite.' + method + '(' + JSON.stringify(options) + ')');
  }
  NativeModules["SQLite"][method](options,success,error);
};

plugin.log = function(...messages) {
  if (plugin.sqlitePlugin.DEBUG) {
    console.log(...messages)
  }
}

SQLitePlugin = function(openargs, openSuccess, openError) {
  var dbname;
  if (!(openargs && openargs['name'])) {
    throw newSQLError("Cannot create a SQLitePlugin db instance without a db name");
  }
  dbname = openargs.name;
  if (typeof dbname !== 'string') {
    throw newSQLError('sqlite plugin database name must be a string');
  }
  this.openargs = openargs;
  this.dbname = dbname;
  this.openSuccess = openSuccess;
  this.openError = openError;
  this.openSuccess || (this.openSuccess = function() {
    plugin.log("DB opened: " + dbname);
  });
  this.openError || (this.openError = function(e) {
    plugin.log(e.message);
  });
  this.open(this.openSuccess, this.openError);
};

SQLitePlugin.prototype.databaseFeatures = {
  isSQLitePluginDatabase: true
};

SQLitePlugin.prototype.openDBs = {};

SQLitePlugin.prototype.addTransaction = function(t) {
  if (!txLocks[this.dbname]) {
    txLocks[this.dbname] = {
      queue: [],
      inProgress: false
    };
  }
  txLocks[this.dbname].queue.push(t);
  if (this.dbname in this.openDBs && this.openDBs[this.dbname] !== DB_STATE_INIT) {
    this.startNextTransaction();
  } else {
    if (this.dbname in this.openDBs) {
      plugin.log('new transaction is waiting for open operation');
    } else {
      plugin.log('database is closed, new transaction is [stuck] waiting until db is opened again!');
    }
  }
};

SQLitePlugin.prototype.transaction = function(fn, error, success) {
  if (!this.openDBs[this.dbname]) {
    error(newSQLError('database not open'));
    return;
  }
  this.addTransaction(new SQLitePluginTransaction(this, fn, error, success, true, false));
};

SQLitePlugin.prototype.readTransaction = function(fn, error, success) {
  if (!this.openDBs[this.dbname]) {
    error(newSQLError('database not open'));
    return;
  }
  this.addTransaction(new SQLitePluginTransaction(this, fn, error, success, false, true));
};

SQLitePlugin.prototype.startNextTransaction = function() {
  var self;
  self = this;
  nextTick((function(_this) {
    return function() {
      var txLock;
      if (!(_this.dbname in _this.openDBs) || _this.openDBs[_this.dbname] !== DB_STATE_OPEN) {
        plugin.log('cannot start next transaction: database not open');
        return;
      }
      txLock = txLocks[self.dbname];
      if (!txLock) {
        plugin.log('cannot start next transaction: database connection is lost');
      } else if (txLock.queue.length > 0 && !txLock.inProgress) {
        txLock.inProgress = true;
        txLock.queue.shift().start();
      }
    };
  })(this));
};

SQLitePlugin.prototype.abortAllPendingTransactions = function() {
  var j, len1, ref, tx, txLock;
  txLock = txLocks[this.dbname];
  if (!!txLock && txLock.queue.length > 0) {
    ref = txLock.queue;
    for (j = 0, len1 = ref.length; j < len1; j++) {
      tx = ref[j];
      tx.abortFromQ(newSQLError('Invalid database handle'));
    }
    txLock.queue = [];
    txLock.inProgress = false;
  }
};

SQLitePlugin.prototype.sqlBatch = function(sqlStatements, success, error) {
  var batchList, j, len1, myfn, st;
  if (!sqlStatements || sqlStatements.constructor !== Array) {
    throw newSQLError('sqlBatch expects an array');
  }
  batchList = [];
  for (j = 0, len1 = sqlStatements.length; j < len1; j++) {
    st = sqlStatements[j];
    if (st.constructor === Array) {
      if (st.length === 0) {
        throw newSQLError('sqlBatch array element of zero (0) length');
      }
      batchList.push({
        sql: st[0],
        params: st.length === 0 ? [] : st[1]
      });
    } else {
      batchList.push({
        sql: st,
        params: []
      });
    }
  }
  myfn = function(tx) {
    var elem, k, len2, results;
    results = [];
    for (k = 0, len2 = batchList.length; k < len2; k++) {
      elem = batchList[k];
      results.push(tx.addStatement(elem.sql, elem.params, null, null));
    }
    return results;
  };
  let mysuccess = function() {
    if (!!success) {
      return success();
    }
  };
  let myerror = function(e) {
    if (!!error) {
      return error(e);
    } else {
      plugin.log("Error handler not provided: ",e);
    }
  };

  this.addTransaction(new SQLitePluginTransaction(this, myfn, myerror, mysuccess, true, false));
};


SQLitePlugin.prototype.open = function(success, error) {
  var openerrorcb, opensuccesscb;

  if (this.dbname in this.openDBs && this.openDBs[this.dbname] === DB_STATE_OPEN) {
    plugin.log('database already open: ' + this.dbname);
    nextTick((function(_this) {
      return function() {
        success(_this);
      };
    })(this));
  } else {
    plugin.log('OPEN database: ' + this.dbname);
    opensuccesscb = (function(_this) {
      return function() {
        var txLock;
        if (!_this.openDBs[_this.dbname]) {
          plugin.log('database was closed during open operation');
        }
        if (_this.dbname in _this.openDBs) {
          _this.openDBs[_this.dbname] = DB_STATE_OPEN;
        }
        if (!!success) {
          success(_this);
        }
        txLock = txLocks[_this.dbname];
        if (!!txLock && txLock.queue.length > 0 && !txLock.inProgress) {
          _this.startNextTransaction();
        }
      };
    })(this);
    openerrorcb = (function(_this) {
      return function() {
        plugin.log('OPEN database: ' + _this.dbname + ' failed, aborting any pending transactions');
        if (!!error) {
          error(newSQLError('Could not open database'));
        }
        delete _this.openDBs[_this.dbname];
        _this.abortAllPendingTransactions();
      };
    })(this);
    this.openDBs[this.dbname] = DB_STATE_INIT;
    plugin.exec("open",this.openargs,opensuccesscb, openerrorcb);
  }
};

SQLitePlugin.prototype.close = function(success, error) {
  if (this.dbname in this.openDBs) {
    if (txLocks[this.dbname] && txLocks[this.dbname].inProgress) {
      plugin.log('cannot close: transaction is in progress');
      error(newSQLError('database cannot be closed while a transaction is in progress'));
      return;
    }
    plugin.log('CLOSE database: ' + this.dbname);
    delete this.openDBs[this.dbname];
    if (txLocks[this.dbname]) {
      plugin.log('closing db with transaction queue length: ' + txLocks[this.dbname].queue.length);
    } else {
      plugin.log('closing db with no transaction lock state');
    }
    let mysuccess = function(t, r) {
      if (!!success) {
        return success(r);
      }
    };
    let myerror = function(t, e) {
      if (!!error) {
        return error(e);
      } else {
        plugin.log("Error handler not provided: ",e);
      }
    };
    plugin.exec("close",{path: this.dbname}, mysuccess, myerror);
  } else {
    var err = 'cannot close: database is not open';
    plugin.log(err);
    if (error) {
      nextTick(function() {
        return error(err);
      });
    }
  }
};

SQLitePlugin.prototype.attach = function(dbNameToAttach, dbAlias, success, error) {
  if (this.dbname in this.openDBs) {
    if (txLocks[this.dbname] && txLocks[this.dbname].inProgress) {
      plugin.log('cannot attach: transaction is in progress');
      error(newSQLError('database cannot be attached while a transaction is in progress'));
      return;
    }
    plugin.log('ATTACH database ' + dbNameToAttach + ' to ' + this.dbname + ' with alias ' + dbAlias);

    let mysuccess = function(t, r) {
      if (!!success) {
        return success(r);
      }
    };
    let myerror = function(e) {
      if (!!error) {
        return error(e);
      } else {
        plugin.log("Error handler not provided: ",e);
      }
    };
    plugin.exec("attach",{path: this.dbname, dbName: dbNameToAttach, dbAlias}, mysuccess, myerror);
  } else {
    let err = 'cannot attach: database is not open';
    if (error) {
      nextTick(function() {
        return error(err);
      });
    }
  }
};

SQLitePlugin.prototype.detach = function(dbAlias, success, error) {
  if (this.dbname in this.openDBs) {
    if (txLocks[this.dbname] && txLocks[this.dbname].inProgress) {
      plugin.log('cannot attach: transaction is in progress');
      error(newSQLError('database cannot be attached while a transaction is in progress'));
      return;
    }
    plugin.log('DETACH database ' + dbAlias + ' from ' + this.dbname);

    let mysuccess = function(t, r) {
      if (!!success) {
        return success(r);
      }
    };
    let myerror = function(e) {
      plugin.log('ERR', e);
      if (!!error) {
        return error(e);
      } else {
        plugin.log("Error handler not provided: ",e);
      }
    };
    this.executeSql('DETACH DATABASE ' + dbAlias, [], mysuccess, myerror)
  } else {
    var err = 'cannot attach: database is not open';
    plugin.log(err);
    if (error) {
      nextTick(function() {
        return error(err);
      });
    }
  }
};

SQLitePlugin.prototype.executeSql = function(statement, params, success, error) {
  var myerror, myfn, mysuccess;
  mysuccess = function(t, r) {
    if (!!success) {
      return success(r);
    }
  };
  myerror = function(t, e) {
    if (!!error) {
      return error(e);
    } else {
      plugin.log("Error handler not provided: ",e);
    }
  };
  myfn = function(tx) {
    tx.addStatement(statement, params, mysuccess, myerror);
  };
  this.addTransaction(new SQLitePluginTransaction(this, myfn, null, null, false, false));
};

SQLitePluginTransaction = function(db, fn, error, success, txlock, readOnly) {
  if (typeof fn !== "function") {
    /*
     This is consistent with the implementation in Chrome -- it
     throws if you pass anything other than a function. This also
     prevents us from stalling our txQueue if somebody passes a
     false value for fn.
     */
    let err = newSQLError("transaction expected a function");
    if (!!error) {
      return error(err);
    } else {
      throw err;
    }
  }
  this.db = db;
  this.fn = fn;
  this.error = error;
  this.success = success;
  this.txlock = txlock;
  this.readOnly = readOnly;
  this.executes = [];
  if (txlock) {
    this.addStatement("BEGIN", [], null, function(tx, err) {
      throw newSQLError("unable to begin transaction: " + err.message, err.code);
    });
  } else {
    this.addStatement("SELECT 1", [], null, null);
  }
};

SQLitePluginTransaction.prototype.start = function() {
  var err;
  try {
    this.fn(this);
    this.run();
  } catch (_error) {
    err = _error;
    txLocks[this.db.dbname].inProgress = false;
    this.db.startNextTransaction();
    if (this.error) {
      this.error(newSQLError(err));
    }
  }
};

SQLitePluginTransaction.prototype.executeSql = function(sql, values, success, error) {
  var that = this;
  if (that.finalized) {
    throw {
      message: 'InvalidStateError: DOM Exception 11: This transaction is already finalized. Transactions are committed' +
      ' after its success or failure handlers are called. If you are using a Promise to handle callbacks, be aware that' +
      ' implementations following the A+ standard adhere to run-to-completion semantics and so Promise resolution occurs' +
      ' on a subsequent tick and therefore after the transaction commits.',
      code: 11
    };
  }
  if (that.readOnly && READ_ONLY_REGEX.test(sql)) {
    that.handleStatementFailure(error, {
      message: 'invalid sql for a read-only transaction'
    });
    return;
  }
  let mysuccess = function(t, r) {
    if (!!success) {
      return success(t,r);
    }
  };
  let myerror = function(t, e) {
    if (!!error) {
      return error(e);
    } else {
      plugin.log("Error handler not provided: ",e);
    }
  };
  that.addStatement(sql, values, mysuccess, myerror);
};

SQLitePluginTransaction.prototype.addStatement = function(sql, values, success, error) {
  var j, len1, params, sqlStatement, t, v;
  sqlStatement = typeof sql === 'string' ? sql : sql.toString();
  params = [];
  if (!!values && values.constructor === Array) {
    for (j = 0, len1 = values.length; j < len1; j++) {
      v = values[j];
      t = typeof v;
      if (v === null || v === void 0 || t === 'number' || t === 'string'){
        params.push(v);
      } else if (t === 'boolean') {
        //Convert true -> 1 / false -> 0
        params.push(~~v);
      }
      else if (t !== 'function') {
        params.push(v.toString());
        plugin.warn('addStatement - parameter of type <'+t+'> converted to string using toString()')
      } else {
        let errorMsg = 'Unsupported parameter type <'+t+'> found in addStatement()';
        plugin.error(errorMsg);
        error(newSQLError(errorMsg));
        return;
      }
    }
  }
  this.executes.push({
    success: success,
    error: error,
    sql: sqlStatement,
    params: params
  });
};

SQLitePluginTransaction.prototype.handleStatementSuccess = function(handler, response) {
  // plugin.log('handler response:',response,response.rows);
  var payload, rows;
  if (!handler) {
    return;
  }
  rows = response.rows || [];
  // plugin.log('handler rows now:',rows);
  payload = {
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
  // plugin.log('handler response payload:',payload);
  handler(this, payload);
};

SQLitePluginTransaction.prototype.handleStatementFailure = function(handler, response) {
  if (!handler) {
    throw newSQLError("a statement with no error handler failed: " + response.message, response.code);
  }
  if (handler(this, response) !== false) {
    throw newSQLError("a statement error callback did not return false: " + response.message, response.code);
  }
};

SQLitePluginTransaction.prototype.run = function() {
  var batchExecutes, handlerFor, i, callbacks, request, tropts, tx, txFailure, waiting;
  txFailure = null;
  tropts = [];
  batchExecutes = this.executes;
  waiting = batchExecutes.length;
  this.executes = [];
  tx = this;
  handlerFor = function(index, didSucceed) {
    return function(response) {
      if (!txFailure) {
        try {
          if (didSucceed) {
            tx.handleStatementSuccess(batchExecutes[index].success, response);
          } else {
            tx.handleStatementFailure(batchExecutes[index].error, newSQLError(response));
          }
        } catch (err) {
          let errorMsg = JSON.stringify(err);
          if(errorMsg === "{}") errorMsg = err.toString();
          plugin.log("warning - exception while invoking a callback: " + errorMsg);
        }

        if (!didSucceed) {
          txFailure = newSQLError(response);
        }
      }
      if (--waiting === 0) {
        if (txFailure) {
          tx.executes = [];
          tx.abort(txFailure);
        } else if (tx.executes.length > 0) {
          tx.run();
        } else {
          tx.finish();
        }
      }
    };
  };
  
  i = 0;
  callbacks = [];
  while (i < batchExecutes.length) {
    request = batchExecutes[i];
    callbacks.push({
      success: handlerFor(i, true),
      error: handlerFor(i, false)
    });
    tropts.push({
      qid: 1111,
      sql: request.sql,
      params: request.params
    });
    i++;
  }

  let mysuccess = function(result) {
    var j, last, q, r, res, type;
    if (result.length == 0){
      return;
    }
    last = result.length - 1;
    for (j = 0; j <= last; ++j) {
      r = result[j];
      type = r.type;
      res = r.result;
      q = callbacks[j];
      if (q) {
        if (q[type]) {
          q[type](res);
        }
      }
    }
  };

  var myerror = function(error) {
    plugin.log("batch execution error: ",error);
  };

  plugin.exec("backgroundExecuteSqlBatch",{
    dbargs: {
      dbname: this.db.dbname
    },
    executes: tropts
  }, mysuccess, myerror);
};

SQLitePluginTransaction.prototype.abort = function(txFailure) {
  var failed, succeeded, tx;
  if (this.finalized) {
    return;
  }
  tx = this;
  succeeded = function(tx) {
    txLocks[tx.db.dbname].inProgress = false;
    tx.db.startNextTransaction();
    if (tx.error) {
      tx.error(txFailure);
    }
  };
  failed = function(tx, err) {
    txLocks[tx.db.dbname].inProgress = false;
    tx.db.startNextTransaction();
    if (tx.error) {
      tx.error(newSQLError("error while trying to roll back: " + err.message, err.code));
    }
  };
  this.finalized = true;
  if (this.txlock) {
    this.addStatement("ROLLBACK", [], succeeded, failed);
    this.run();
  } else {
    succeeded(tx);
  }
};

SQLitePluginTransaction.prototype.finish = function() {
  var failed, succeeded, tx;
  if (this.finalized) {
    return;
  }
  tx = this;
  succeeded = function(tx) {
    txLocks[tx.db.dbname].inProgress = false;
    tx.db.startNextTransaction();
    if (tx.success) {
      tx.success();
    }
  };
  failed = function(tx, err) {
    txLocks[tx.db.dbname].inProgress = false;
    tx.db.startNextTransaction();
    if (tx.error) {
      tx.error(newSQLError("error while trying to commit: " + err.message, err.code));
    }
  };
  this.finalized = true;
  if (this.txlock) {
    this.addStatement("COMMIT", [], succeeded, failed);
    this.run();
  } else {
    succeeded(tx);
  }
};

SQLitePluginTransaction.prototype.abortFromQ = function(sqlerror) {
  if (this.error) {
    this.error(sqlerror);
  }
};

dblocations = {
  'default' : 'nosync',
  'Documents' : 'docs',
  'Library' : 'libs'
};

SQLiteFactory = function(){};

SQLiteFactory.prototype.DEBUG = function(debug) {
  plugin.log("Setting debug to:",debug);
  plugin.sqlitePlugin.DEBUG = debug;
};

SQLiteFactory.prototype.sqliteFeatures = function() {
  return {
    isSQLitePlugin: true
  };
};

SQLiteFactory.prototype.openDatabase = argsArray(function(args) {
  var errorcb, first, okcb, openargs, readOnly;
  if (args.length < 1) {
    return null;
  }
  first = args[0];
  openargs = null;
  okcb = errorcb = () => {};
  if (first.constructor === String) {
    openargs = {
      name: first,
      dblocation : dblocations['default']
    };
    if (args.length >= 5) {
      okcb = args[4];
      if (args.length > 5) {
        errorcb = args[5];
      }
    }
  } else {
    openargs = first;
    readOnly = !!openargs.readOnly;
    if (!readOnly && (!openargs.location || openargs.location.constructor !== String || !dblocations.hasOwnProperty(openargs.location))) {
      openargs.dblocation = dblocations['default'];
    } else {
      openargs.dblocation = dblocations[openargs.location];
    }

    if (!!openargs.createFromLocation) {
      if (openargs.createFromLocation === 1) {
        openargs.assetFilename = "1";
      } else if (typeof openargs.createFromLocation == 'string'){
        openargs.assetFilename = openargs.createFromLocation;
      }
    }

    if (!!openargs.androidDatabaseImplementation && openargs.androidDatabaseImplementation === 2) {
      openargs.androidOldDatabaseImplementation = 1;
    }

    if (!!openargs.androidLockWorkaround && openargs.androidLockWorkaround === 1) {
      openargs.androidBugWorkaround = 1;
    }

    if (args.length >= 2) {
      okcb = args[1];
      if (args.length > 2) {
        errorcb = args[2];
      }
    }
  }

  return new SQLitePlugin(openargs, okcb, errorcb);
});

SQLiteFactory.prototype.echoTest = function(success, error) {
  let inputTestValue = 'test-string';
  let mysuccess = function(testValue) {
    if (testValue === inputTestValue) {
      return success();
    } else {
      return error(`Mismatch: got: ${testValue} , expected: ${inputTestValue}`);
    }
  };
  let myerror = function(e) {
    return error(e);
  };

  plugin.exec("echoStringValue",{value: inputTestValue}, mysuccess, myerror);
};

SQLiteFactory.prototype.deleteDatabase = function(first,success, error) {
  var args = {};
  if (first.constructor === String) {
    args.path = first;
    args.dblocation = dblocations['default'];
  } else {
    if (!(first && first['name'])) {
      throw new Error("Please specify db name via name property");
    }
    args.path = first.name;
    if (!first.location || first.location.constructor !== String || !dblocations.hasOwnProperty(first.location)) {
      args.dblocation = dblocations['default'];
    } else {
      args.dblocation = dblocations[first.location];
    }
  }

  let mysuccess = function(r) {
    delete SQLitePlugin.prototype.openDBs[args.path];
    if (!!success) {
      return success(r);
    }
  };
  let myerror = function(e) {
    if (!!error) {
      return error(e);
    } else {
      plugin.log("deleteDatabase error handler not provided: ",e);
    }
  };

  plugin.exec("delete",args,mysuccess,myerror);
};

plugin.sqlitePlugin = {
  SQLiteFactory : SQLiteFactory,
  SQLitePluginTransaction : SQLitePluginTransaction,
  SQLitePlugin : SQLitePlugin,
  log: plugin.log
};

module.exports = plugin.sqlitePlugin;
