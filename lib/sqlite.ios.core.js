/*
 * sqlite.ios.core.js
 *
 * Created by Andrzej Porebski on 10/29/15.
 * Copyright (c) 2015 Andrzej Porebski.
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
var DB_STATE_INIT, DB_STATE_OPEN, READ_ONLY_REGEX, SQLiteFactory, SQLitePlugin, SQLitePluginTransaction, argsArray, dblocations, newSQLError, nextTick, root, txLocks;

var plugin = {};

READ_ONLY_REGEX = /^\s*(?:drop|delete|insert|update|create)\s/i;

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

nextTick = window.setImmediate || function(fun) {
  window.setTimeout(fun, 0);
};


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
    console.log('SQLite.' + method + '(' + JSON.stringify(options) + ')');
  }
  NativeModules["SQLite"][method](options,success,error);
};

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
    console.log("DB opened: " + dbname);
  });
  this.openError || (this.openError = function(e) {
    console.log(e.message);
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
      console.log('new transaction is waiting for open operation');
    } else {
      console.log('database is closed, new transaction is [stuck] waiting until db is opened again!');
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
        console.log('cannot start next transaction: database not open');
        return;
      }
      txLock = txLocks[self.dbname];
      if (!txLock) {
        console.log('cannot start next transaction: database connection is lost');
        return;
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

SQLitePlugin.prototype.open = function(success, error) {
  var openerrorcb, opensuccesscb;
  if (this.dbname in this.openDBs) {
    console.log('database already open: ' + this.dbname);
    nextTick((function(_this) {
      return function() {
        success(_this);
      };
    })(this));
  } else {
    console.log('OPEN database: ' + this.dbname);
    opensuccesscb = (function(_this) {
      return function() {
        var txLock;
        if (!_this.openDBs[_this.dbname]) {
          console.log('database was closed during open operation');
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
        console.log('OPEN database: ' + _this.dbname + ' failed, aborting any pending transactions');
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
      console.log('cannot close: transaction is in progress');
      error(newSQLError('database cannot be closed while a transaction is in progress'));
      return;
    }
    console.log('CLOSE database: ' + this.dbname);
    delete this.openDBs[this.dbname];
    if (txLocks[this.dbname]) {
      console.log('closing db with transaction queue length: ' + txLocks[this.dbname].queue.length);
    } else {
      console.log('closing db with no transaction lock state');
    }
    mysuccess = function(t, r) {
      if (!!success) {
        return success(r);
      }
    };
    myerror = function(t, e) {
      if (!!error) {
        return error(e);
      } else {
        console.log("Error handler not provided: ",e);
      }
    };
    plugin.exec("close",{path: this.dbname}, mysuccess, myerror);
  } else {
    var err = 'cannot close: database is not open';
    console.log(err);
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
      console.log("Error handler not provided: ",e);
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
    return;
  }
  if (that.readOnly && READ_ONLY_REGEX.test(sql)) {
    that.handleStatementFailure(error, {
      message: 'invalid sql for a read-only transaction'
    });
    return;
  }
  mysuccess = function(t, r) {
    if (!!success) {
      return success(t,r);
    }
  };
  myerror = function(t, e) {
    if (!!error) {
      return error(e);
    } else {
      console.log("Error handler not provided: ",e);
    }
  };
  that.addStatement(sql, values, mysuccess, myerror);
};

SQLitePluginTransaction.prototype.addStatement = function(sql, values, success, error) {
  var j, len1, params, t, v;
  params = [];
  if (!!values && values.constructor === Array) {
    for (j = 0, len1 = values.length; j < len1; j++) {
      v = values[j];
      t = typeof v;
      params.push((v === null || v === void 0 || t === 'number' || t === 'string' ? v : v instanceof Blob ? v.valueOf() : v.toString()));
    }
  }
  this.executes.push({
    success: success,
    error: error,
    sql: sql,
    params: params
  });
};

SQLitePluginTransaction.prototype.handleStatementSuccess = function(handler, response) {
  // console.log('handler response:',response,response.rows);
  var payload, rows;
  if (!handler) {
    return;
  }
  rows = response.rows || [];
  // console.log('handler rows now:',rows);
  payload = {
    rows: {
      item: function(i) {
        return rows[i];
      },
      length: rows.length
    },
    rowsAffected: response.rowsAffected || 0,
    insertId: response.insertId || void 0
  };
  // console.log('handler response payload:',payload);
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
  var batchExecutes, handlerFor, i, mycb, mycbmap, request, tropts, tx, txFailure, waiting;
  txFailure = null;
  tropts = [];
  batchExecutes = this.executes;
  waiting = batchExecutes.length;
  this.executes = [];
  tx = this;
  handlerFor = function(index, didSucceed) {
    return function(response) {
      var err;
      try {
        if (didSucceed) {
          tx.handleStatementSuccess(batchExecutes[index].success, response);
        } else {
          tx.handleStatementFailure(batchExecutes[index].error, newSQLError(response));
        }
      } catch (_error) {
        err = _error;
        if (!txFailure) {
          txFailure = newSQLError(err);
        }
      }
      if (--waiting === 0) {
        if (txFailure) {
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
  mycbmap = {};
  while (i < batchExecutes.length) {
    request = batchExecutes[i];
    mycbmap[i] = {
      success: handlerFor(i, true),
      error: handlerFor(i, false)
    };
    tropts.push({
      qid: 1111,
      sql: request.sql,
      params: request.params
    });
    i++;
  }
  mycb = function(result) {
    var j, last, q, r, ref, res, type;
    last = result.length - 1;
    for (i = j = 0, ref = last; 0 <= ref ? j <= ref : j >= ref; i = 0 <= ref ? ++j : --j) {
      r = result[i];
      type = r.type;
      res = r.result;
      q = mycbmap[i];
      if (q) {
        if (q[type]) {
          q[type](res);
        }
      }
    }
  };
  myerror = function(error) {
    console.log("batch execution error: ",error);
  };

  plugin.exec("backgroundExecuteSqlBatch",{
      dbargs: {
        dbname: this.db.dbname
      },
      executes: tropts
    },mycb, myerror);
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

dblocations = ["docs", "libs", "nosync"];

SQLiteFactory = function(){};

SQLiteFactory.prototype.DEBUG = function(debug) {
  console.log("Setting debug to:",debug);
  plugin.sqlitePlugin.DEBUG = debug;
};

SQLiteFactory.prototype.sqliteFeatures = function() {
  return {
    isSQLitePlugin: true
  };
};

  /*
  NOTE: this function should NOT be translated from Javascript
  back to CoffeeScript by js2coffee.
  If this function is edited in Javascript then someone will
  have to translate it back to CoffeeScript by hand.
   */
SQLiteFactory.prototype.openDatabase = argsArray(function(args) {
  var dblocation, errorcb, first, okcb, openargs;
  if (args.length < 1) {
    return null;
  }
  first = args[0];
  openargs = null;
  okcb = null;
  errorcb = null;
  if (first.constructor === String) {
    openargs = {
      name: first
    };
    if (args.length >= 5) {
      okcb = args[4];
      if (args.length > 5) {
        errorcb = args[5];
      }
    }
  } else {
    openargs = first;
    if (args.length >= 2) {
      okcb = args[1];
      if (args.length > 2) {
        errorcb = args[2];
      }
    }
  }

  dblocation = !!openargs.location ? dblocations[openargs.location] : null;
  openargs.dblocation = dblocation || dblocations[0];
  if (!!openargs.createFromLocation && openargs.createFromLocation === 1) {
    openargs.createFromResource = "1";
  }
  if (!!openargs.androidDatabaseImplementation && openargs.androidDatabaseImplementation === 2) {
    openargs.androidOldDatabaseImplementation = 1;
  }
  if (!!openargs.androidLockWorkaround && openargs.androidLockWorkaround === 1) {
    openargs.androidBugWorkaround = 1;
  }
  return new SQLitePlugin(openargs, okcb, errorcb);
});

SQLiteFactory.prototype.deleteDatabase = function(first,success, error) {
  var args, dblocation;
  args = {};
  if (first.constructor === String) {
    args.path = first;
    args.dblocation = dblocations[0];
  } else {
    if (!(first && first['name'])) {
      throw new Error("Please specify db name");
    }
    args.path = first.name;
    dblocation = !!first.location ? dblocations[first.location] : null;
    args.dblocation = dblocation || dblocations[0];
  }

  mysuccess = function(r) {
    if (!!success) {
      return success(r);
    }
  };
  myerror = function(e) {
    if (!!error) {
      return error(e);
    } else {
      console.log("Error handler not provided: ",e);
    }
  };

  delete SQLitePlugin.prototype.openDBs[args.path];
  success = success || function(){};
  error = error || function(){};
  plugin.exec("delete",args,success,error);
};

plugin.sqlitePlugin = {
  SQLiteFactory : SQLiteFactory,
  SQLitePluginTransaction : SQLitePluginTransaction,
  SQLitePlugin : SQLitePlugin
};

module.exports = plugin.sqlitePlugin;


