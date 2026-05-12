'use strict';

function loadPluginWithNative(nativeModule) {
  jest.resetModules();
  jest.doMock('../src/NativeSQLiteModule', () => ({
    resolveSQLite: () => nativeModule,
    getSQLite: () => nativeModule,
    __resetForTests: () => {},
  }));
  return require('../lib/sqlite.core.js');
}

describe('plugin.exec', () => {
  test('plugin.exec throws a descriptive error if the native module is not linked', () => {
    const plugin = loadPluginWithNative(null);
    expect(() => plugin.exec('open', { name: 't.db' }, () => {}, () => {}))
      .toThrow(/not linked/i);
  });

  test('plugin.exec forwards (options, success, error) to the resolved native method', () => {
    const nativeOpen = jest.fn();
    const native = { open: nativeOpen };
    const plugin = loadPluginWithNative(native);
    const ok = jest.fn();
    const err = jest.fn();
    plugin.exec('open', { name: 't.db' }, ok, err);
    expect(nativeOpen).toHaveBeenCalledTimes(1);
    expect(nativeOpen).toHaveBeenCalledWith({ name: 't.db' }, ok, err);
  });
});
