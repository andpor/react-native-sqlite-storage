'use strict';

describe('NativeSQLiteModule resolver', () => {
  beforeEach(() => {
    jest.resetModules();
  });

  test('resolveSQLite prefers TurboModuleRegistry.get("SQLite") when it returns a module', () => {
    jest.doMock('react-native', () => {
      const turboSentinel = { __tag: 'turbo' };
      return {
        NativeModules: { SQLite: { __tag: 'native' } },
        TurboModuleRegistry: {
          get: jest.fn(name => (name === 'SQLite' ? turboSentinel : null)),
        },
      };
    });
    const { resolveSQLite } = require('../src/NativeSQLiteModule');
    const rn = require('react-native');
    const resolved = resolveSQLite();
    expect(resolved).toBe(rn.TurboModuleRegistry.get('SQLite'));
    expect(resolved.__tag).toBe('turbo');
  });

  test('resolveSQLite falls back to NativeModules.SQLite when TurboModuleRegistry.get returns null', () => {
    const nativeSentinel = { __tag: 'native' };
    jest.doMock('react-native', () => ({
      NativeModules: { SQLite: nativeSentinel },
      TurboModuleRegistry: { get: jest.fn(() => null) },
    }));
    const { resolveSQLite } = require('../src/NativeSQLiteModule');
    const rn = require('react-native');
    const resolved = resolveSQLite();
    expect(resolved).toBe(rn.NativeModules.SQLite);
    expect(resolved.__tag).toBe('native');
  });

  test('resolveSQLite returns null when neither TurboModule nor NativeModule is present', () => {
    jest.doMock('react-native', () => ({
      NativeModules: {},
      TurboModuleRegistry: { get: jest.fn(() => null) },
    }));
    const { resolveSQLite } = require('../src/NativeSQLiteModule');
    expect(resolveSQLite()).toBeNull();
  });

  test('getSQLite memoises the resolved module across calls', () => {
    const spy = jest.fn(() => ({ __tag: 'turbo' }));
    jest.doMock('react-native', () => ({
      NativeModules: {},
      TurboModuleRegistry: { get: spy },
    }));
    const { getSQLite, __resetForTests } = require('../src/NativeSQLiteModule');
    __resetForTests();
    const first = getSQLite();
    const second = getSQLite();
    expect(first).toBe(second);
    expect(spy).toHaveBeenCalledTimes(1);
  });
});
