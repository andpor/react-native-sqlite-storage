'use strict';

const { loadPlugin } = require('./helpers/loadPlugin');

describe('enablePromise(true)', () => {
  test('turns openDatabase into a Promise that resolves on native success', async () => {
    const native = { open: jest.fn((_opts, success) => success()) };
    const { factory } = loadPlugin(native);
    factory.enablePromise(true);
    const db = await factory.openDatabase({ name: 't.db' });
    expect(db).toBeTruthy();
    expect(native.open).toHaveBeenCalledTimes(1);
  });

  test('causes openDatabase to reject on native error', async () => {
    const native = { open: jest.fn((_opts, _s, error) => error('boom')) };
    const { factory } = loadPlugin(native);
    factory.enablePromise(true);
    await expect(factory.openDatabase({ name: 't.db' })).rejects.toBeDefined();
  });
});
