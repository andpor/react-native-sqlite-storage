'use strict';

const { loadPlugin } = require('./helpers/loadPlugin');

test('SQLitePlugin.attach invokes SQLite.attach with path/dbName/dbAlias', () => {
  const native = {
    open: jest.fn((_opts, success) => success()),
    attach: jest.fn((_opts, success) => success()),
  };
  const { factory } = loadPlugin(native);
  const db = factory.openDatabase({ name: 'main.db' }, () => {}, () => {});
  const ok = jest.fn();
  const err = jest.fn();
  db.attach('other.db', 'other', ok, err);

  expect(native.attach).toHaveBeenCalledTimes(1);
  const [opts] = native.attach.mock.calls[0];
  expect(opts).toEqual({ path: 'main.db', dbName: 'other.db', dbAlias: 'other' });
});
