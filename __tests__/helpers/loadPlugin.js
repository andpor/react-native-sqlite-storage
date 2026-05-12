'use strict';

/**
 * Test helper: loads the library's public entry (sqlite.js) with an
 * injected native module. Returns the SQLiteFactory instance and the
 * mutable native object so tests can rewire method implementations.
 */
function loadPlugin(native) {
  jest.resetModules();
  jest.doMock('../../src/NativeSQLiteModule', () => ({
    resolveSQLite: () => native,
    getSQLite: () => native,
    __resetForTests: () => {},
  }));
  const factory = require('../../sqlite.js');
  return { factory, native };
}

function flushAsync() {
  return new Promise(resolve => setImmediate(resolve));
}

module.exports = { loadPlugin, flushAsync };
