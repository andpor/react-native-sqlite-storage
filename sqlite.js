/*
 * sqlite.js
 *
 * Created by Andrzej Porebski on 10/29/15.
 * Copyright (c) 2015-16 Andrzej Porebski.
 *
 * This library is available under the terms of the MIT License (2008).
 * See http://opensource.org/licenses/alphabetical for full text.
 */
var plugin = require('./lib/sqlite.core.js');
var {SQLiteFactory} = plugin;

var config = [

  // meaning: [returnValueExpected,prototype,fn,argsNeedPadding,reverseCallbacks,rejectOnError]

  [false,"SQLitePlugin","transaction",false,true,true],
  [false,"SQLitePlugin","readTransaction",false,true,true],
  [false,"SQLitePlugin","close",false,false,true],
  [false,"SQLitePlugin","executeSql",true,false,true],
  [false,"SQLitePlugin","sqlBatch",false,false,true],
  [false,"SQLitePlugin","attach",true,false,true],
  [false,"SQLitePlugin","detach",false,false,true],
  [false,"SQLitePluginTransaction","executeSql",true,false,false],
  [false,"SQLiteFactory","deleteDatabase",false,false,true],
  [true, "SQLiteFactory","openDatabase",false,false,true],
  [false,"SQLiteFactory","echoTest",false,false,true]
];

var originalFns = {};
config.forEach(entry => {
  let [returnValueExpected,prototype,fn]= entry;
  let originalFn = plugin[prototype].prototype[fn];
  originalFns[prototype + "." + fn] = originalFn;
});

function enablePromiseRuntime(enable){
  if (enable){
    createPromiseRuntime();
  } else {
    createCallbackRuntime();
  }
}
function createCallbackRuntime() {
  config.forEach(entry => {
    let [returnValueExpected,prototype,fn,argsNeedPadding,reverseCallbacks,rejectOnError]= entry;
    plugin[prototype].prototype[fn] = originalFns[prototype + "." + fn];
  });
  plugin.log("Callback based runtime ready");
}
function createPromiseRuntime() {
  config.forEach(entry => {
    let [returnValueExpected,prototype,fn,argsNeedPadding,reverseCallbacks,rejectOnError]= entry;
    let originalFn = plugin[prototype].prototype[fn];
    plugin[prototype].prototype[fn] = function(...args){
      if (argsNeedPadding && args.length == 1){
        args.push([]);
      }
      var promise = new Promise((resolve,reject) => {
        let success = function(...args){
          if (!returnValueExpected) {
           return resolve(args);
          }
        };
        let error = function(err){
          plugin.log('error: ',fn,...args,arguments);
          if (rejectOnError) {
            reject(err);
          }
          return false;
        };
        var retValue = originalFn.call(this,...args,reverseCallbacks ? error : success, reverseCallbacks ? success : error);
        if (returnValueExpected){
          return resolve(retValue);
        }
      });

      return promise;
    }
  });
  plugin.log("Promise based runtime ready");
}
SQLiteFactory.prototype.enablePromise = enablePromiseRuntime;

module.exports = new SQLiteFactory();
