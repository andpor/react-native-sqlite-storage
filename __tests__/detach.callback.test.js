'use strict';

const { loadPlugin, flushAsync } = require('./helpers/loadPlugin');

test('SQLitePlugin.detach issues a DETACH DATABASE statement through the transaction queue', async () => {
  const native = {
    open: jest.fn((_opts, success) => success()),
    backgroundExecuteSqlBatch: jest.fn((opts, success) => {
      const results = opts.executes.map(() => ({ rows: [], rowsAffected: 0, insertId: 0 }));
      success(results);
    }),
  };
  const { factory } = loadPlugin(native);
  const db = factory.openDatabase({ name: 'main.db' }, () => {}, () => {});
  const ok = jest.fn();
  const err = jest.fn();
  db.detach('other', ok, err);

  await flushAsync();
  await flushAsync();

  expect(native.backgroundExecuteSqlBatch).toHaveBeenCalled();
  const allSql = native.backgroundExecuteSqlBatch.mock.calls
    .flatMap(([opts]) => opts.executes.map(e => e.sql));
  expect(allSql.some(sql => /^DETACH DATABASE /i.test(sql))).toBe(true);
});
