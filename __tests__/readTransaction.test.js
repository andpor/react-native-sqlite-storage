'use strict';

const { loadPlugin, flushAsync } = require('./helpers/loadPlugin');

describe('SQLitePlugin.readTransaction', () => {
  test('rejects write SQL matching READ_ONLY_REGEX', async () => {
    const native = {
      open: jest.fn((_opts, success) => success()),
      backgroundExecuteSqlBatch: jest.fn((opts, success) => {
        const results = opts.executes.map(() => ({ rows: [], rowsAffected: 0, insertId: 0 }));
        success(results);
      }),
    };
    const { factory } = loadPlugin(native);
    const db = factory.openDatabase({ name: 't.db' }, () => {}, () => {});

    const stmtErr = jest.fn();
    const txErr = jest.fn();
    const txOk = jest.fn();
    db.readTransaction(tx => {
      tx.executeSql('INSERT INTO a VALUES (1)', [], () => {}, stmtErr);
    }, txErr, txOk);

    await flushAsync();
    await flushAsync();

    expect(stmtErr).toHaveBeenCalled();
    const [, e] = stmtErr.mock.calls[0];
    expect(e && e.message).toMatch(/read-only/i);
  });

  test('allows SELECT statements through to backgroundExecuteSqlBatch', async () => {
    const native = {
      open: jest.fn((_opts, success) => success()),
      backgroundExecuteSqlBatch: jest.fn((opts, success) => {
        const results = opts.executes.map(() => ({ rows: [{ '1': 1 }], rowsAffected: 0, insertId: 0 }));
        success(results);
      }),
    };
    const { factory } = loadPlugin(native);
    const db = factory.openDatabase({ name: 't.db' }, () => {}, () => {});

    const stmtErr = jest.fn();
    const stmtOk = jest.fn();
    db.readTransaction(tx => {
      tx.executeSql('SELECT * FROM a', [], stmtOk, stmtErr);
    }, () => {}, () => {});

    await flushAsync();
    await flushAsync();

    expect(stmtErr).not.toHaveBeenCalled();
    const sqls = native.backgroundExecuteSqlBatch.mock.calls
      .flatMap(([opts]) => opts.executes.map(e => e.sql));
    expect(sqls).toContain('SELECT * FROM a');
  });
});
