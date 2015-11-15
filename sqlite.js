/*
 * sqlite.js
 *
 * Created by Andrzej Porebski on 10/29/15.
 * Copyright (c) 2015 Andrzej Porebski.
 *
 * This library is available under the terms of the MIT License (2008).
 * See http://opensource.org/licenses/alphabetical for full text.
 */
var plugin = {SQLiteFactory} = require('./lib/sqlite.core.js');

var config = [
  [false,"SQLitePlugin","transaction",false,true],
  [false,"SQLitePlugin","readTransaction",false,true],
  [false,"SQLitePlugin","close",false,false],
  [false,"SQLitePlugin","executeSql",true,false],
  [false,"SQLitePluginTransaction","executeSql",true,false],
  [false,"SQLiteFactory","deleteDatabase",false,false],
  [true,"SQLiteFactory","openDatabase",false,false]
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
};

function createCallbackRuntime() {
  config.forEach(entry => {
    let [returnValueExpected,prototype,fn,argsNeedPadding,reverseCallbacks]= entry;
    plugin[prototype].prototype[fn] = originalFns[prototype + "." + fn];
  });
  console.log("Callback based runtime ready");
};

function createPromiseRuntime() {
  config.forEach(entry => {
    let [returnValueExpected,prototype,fn,argsNeedPadding,reverseCallbacks]= entry;
    let originalFn = plugin[prototype].prototype[fn]
    plugin[prototype].prototype[fn] = function(...args){
      if (argsNeedPadding && args.length == 1){
        args.push([]);
      }
      var promise = new Promise(function(resolve,reject){
        let success = function(...args){
          if (!returnValueExpected) {
           return resolve(args);
          }
        };
        let error = function(err){
          console.log('error: ',fn,...args,arguments);
          reject(err);
          return false;
        };
        var retValue = originalFn.call(this,...args,reverseCallbacks ? error : success, reverseCallbacks ? success : error);
        if (returnValueExpected){
          return resolve(retValue);
        }
      }.bind(this));

      return promise;
    }
  });
  console.log("Promise based runtime ready");
};

SQLiteFactory.prototype.enablePromise = enablePromiseRuntime;

module.exports = new SQLiteFactory();
