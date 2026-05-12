'use strict';

const { loadPlugin, flushAsync } = require('./helpers/loadPlugin');

test('SQLitePlugin.sqlBatch packages array statements into a single backgroundExecuteSqlBatch call', async () => {
  const native = {
    open: jest.fn((_opts, success) => success()),
    backgroundExecuteSqlBatch: jest.fn((opts, success) => {
      const results = opts.executes.map(() => ({ rows: [], rowsAffected: 0, insertId: 0 }));
      success(results);
    }),
  };
  const { factory } = loadPlugin(native);
  const db = factory.openDatabase({ name: 't.db' }, () => {}, () => {});
  const ok = jest.fn();
  const err = jest.fn();

  db.sqlBatch([
    'CREATE TABLE IF NOT EXISTS a(x INTEGER)',
    ['INSERT INTO a VALUES (?)', [1]],
  ], ok, err);

  await flushAsync();
  await flushAsync();

  expect(native.backgroundExecuteSqlBatch).toHaveBeenCalledTimes(1);
  const [opts] = native.backgroundExecuteSqlBatch.mock.calls[0];
  const sqls = opts.executes.map(e => e.sql);
  expect(sqls).toContain('CREATE TABLE IF NOT EXISTS a(x INTEGER)');
  expect(sqls).toContain('INSERT INTO a VALUES (?)');
});
