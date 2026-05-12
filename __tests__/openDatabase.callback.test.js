'use strict';

const { loadPlugin } = require('./helpers/loadPlugin');

describe('SQLiteFactory.openDatabase (callback mode)', () => {
  test('openDatabase in callback mode invokes SQLite.open with the openargs dictionary', () => {
    const native = { open: jest.fn((opts, success /*, error*/) => { success(); }) };
    const { factory } = loadPlugin(native);
    const ok = jest.fn();
    const err = jest.fn();
    factory.openDatabase({ name: 't.db' }, ok, err);
    expect(native.open).toHaveBeenCalledTimes(1);
    const [opts] = native.open.mock.calls[0];
    expect(opts.name).toBe('t.db');
    expect(opts.dblocation).toBe('nosync');
    expect(ok).toHaveBeenCalledTimes(1);
    expect(err).not.toHaveBeenCalled();
  });

  test('openDatabase callback mode delivers error via the provided errorcb', () => {
    const native = { open: jest.fn((opts, success, error) => { error('boom'); }) };
    const { factory } = loadPlugin(native);
    const ok = jest.fn();
    const err = jest.fn();
    factory.openDatabase({ name: 't.db' }, ok, err);
    expect(err).toHaveBeenCalledTimes(1);
    const [received] = err.mock.calls[0];
    expect(received).toBeTruthy();
    expect(received.message || '').toMatch(/Could not open database/i);
  });
});
