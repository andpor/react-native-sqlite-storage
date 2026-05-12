'use strict';

const { NativeModules, TurboModuleRegistry } = require('react-native');

function resolveSQLite() {
  if (TurboModuleRegistry && typeof TurboModuleRegistry.get === 'function') {
    const turbo = TurboModuleRegistry.get('SQLite');
    if (turbo != null) {
      return turbo;
    }
  }
  return NativeModules && NativeModules.SQLite ? NativeModules.SQLite : null;
}

let cached;
function getSQLite() {
  if (cached === undefined) {
    cached = resolveSQLite();
  }
  return cached;
}

function __resetForTests() {
  cached = undefined;
}

module.exports = { resolveSQLite, getSQLite, __resetForTests };
