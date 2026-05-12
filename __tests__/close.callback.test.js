'use strict';

const { loadPlugin } = require('./helpers/loadPlugin');

test('SQLitePlugin.close invokes SQLite.close with {path: dbname}', () => {
  const native = {
    open: jest.fn((_opts, success) => success()),
    close: jest.fn((_opts, success) => success()),
  };
  const { factory } = loadPlugin(native);
  const db = factory.openDatabase({ name: 't.db' }, () => {}, () => {});
  const ok = jest.fn();
  const err = jest.fn();
  db.close(ok, err);
  expect(native.close).toHaveBeenCalledTimes(1);
  const [opts] = native.close.mock.calls[0];
  expect(opts).toEqual({ path: 't.db' });
  expect(ok).toHaveBeenCalled();
  expect(err).not.toHaveBeenCalled();
});
