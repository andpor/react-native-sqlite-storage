'use strict';

const { loadPlugin } = require('./helpers/loadPlugin');

test('SQLiteFactory.deleteDatabase invokes SQLite.delete with {path, dblocation}', () => {
  const native = { delete: jest.fn((_opts, success) => success()) };
  const { factory } = loadPlugin(native);
  const ok = jest.fn();
  const err = jest.fn();
  factory.deleteDatabase({ name: 't.db', location: 'Library' }, ok, err);

  expect(native.delete).toHaveBeenCalledTimes(1);
  const [opts] = native.delete.mock.calls[0];
  expect(opts.path).toBe('t.db');
  expect(opts.dblocation).toBe('libs');
});
