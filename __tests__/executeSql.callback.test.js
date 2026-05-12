'use strict';

const { loadPlugin, flushAsync } = require('./helpers/loadPlugin');

test('SQLitePlugin.executeSql runs the statement via backgroundExecuteSqlBatch (never via single executeSql)', async () => {
  const native = {
    open: jest.fn((_opts, success) => success()),
    backgroundExecuteSqlBatch: jest.fn((opts, success) => {
      const results = opts.executes.map(() => ({ rows: [], rowsAffected: 0, insertId: 0 }));
      success(results);
    }),
    executeSql: jest.fn(),
    backgroundExecuteSql: jest.fn(),
  };
  const { factory } = loadPlugin(native);
  const db = factory.openDatabase({ name: 't.db' }, () => {}, () => {});
  const ok = jest.fn();
  const err = jest.fn();
  db.executeSql('SELECT 1', [], ok, err);

  await flushAsync();
  await flushAsync();

  expect(native.backgroundExecuteSqlBatch).toHaveBeenCalledTimes(1);
  const [opts] = native.backgroundExecuteSqlBatch.mock.calls[0];
  expect(Array.isArray(opts.executes)).toBe(true);
  expect(opts.executes.length).toBeGreaterThanOrEqual(1);
  const sqls = opts.executes.map(e => e.sql);
  expect(sqls).toContain('SELECT 1');
  expect(native.executeSql).not.toHaveBeenCalled();
  expect(native.backgroundExecuteSql).not.toHaveBeenCalled();
});
