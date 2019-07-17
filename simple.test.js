/* global beforeEach, jest, test, expect */
/* @flow strict-local */

'use strict';

const { NativeModules } = require('react-native');
const sqlite = require('./simple');

jest.mock(
  'react-native',
  () => {
    return {
      NativeModules: {
        SQLite: {
          close: jest.fn((args, success, error) => {
            success('database removed');
          }),

          executeSqlBatch: jest.fn(),

          open: jest.fn((args, success, error) => {
            success('Database opened');
          })
        }
      }
    };
  },
  { virtual: true }
);

beforeEach(() => {
  jest.clearAllMocks();
});

test('Open database', async () => {
  await sqlite.open('filename');

  expect(NativeModules.SQLite.open).toHaveBeenCalledWith(
    { name: 'filename', dblocation: expect.any(String) },
    expect.any(Function),
    expect.any(Function)
  );
});

test('Get all results', async () => {
  NativeModules.SQLite.executeSqlBatch.mockImplementationOnce(
    (args, success, error) => {
      success([
        {
          qid: 1,
          result: { rows: [{ id: 1, val: 'test' }, { id: 2, val: 'case' }] },
          type: 'success'
        }
      ]);
    }
  );

  // Execute.
  const db = await sqlite.open('filename');
  const results = await db.all('SELECT id, val FROM foo WHERE bar = ?', [
    'baz'
  ]);

  expect(results).toEqual([{ id: 1, val: 'test' }, { id: 2, val: 'case' }]);

  expect(NativeModules.SQLite.executeSqlBatch).toHaveBeenCalledWith(
    {
      dbargs: { dbname: 'filename' },
      executes: [
        {
          qid: 1,
          sql: 'SELECT id, val FROM foo WHERE bar = ?',
          params: ['baz']
        }
      ]
    },
    expect.any(Function),
    expect.any(Function)
  );
});

test('Execute statement', async () => {
  NativeModules.SQLite.executeSqlBatch.mockImplementationOnce(
    (args, success, error) => {
      success([
        {
          qid: 1,
          result: { rowsAffected: 0 },
          type: 'success'
        }
      ]);
    }
  );

  // Execute.
  const db = await sqlite.open('filename');
  const results = await db.exec('BEGIN');

  expect(results).toBeUndefined();

  expect(NativeModules.SQLite.executeSqlBatch).toHaveBeenCalledWith(
    {
      dbargs: { dbname: 'filename' },
      executes: [
        {
          qid: 1,
          sql: 'BEGIN',
          params: []
        }
      ]
    },
    expect.any(Function),
    expect.any(Function)
  );
});

test('Execute invalid statement', async () => {
  NativeModules.SQLite.executeSqlBatch.mockImplementationOnce(
    (args, success, error) => {
      success([
        {
          qid: 1,
          result: {
            message:
              'near "INVALID": syntax error (code 1 SQLITE_ERROR): , while compiling: INVALID'
          },
          type: 'error'
        }
      ]);
    }
  );

  // Execute.
  const db = await sqlite.open('filename');
  await expect(db.exec('INVALID')).rejects.toThrow(
    'near "INVALID": syntax error (code 1 SQLITE_ERROR): , while compiling: INVALID'
  );

  expect(NativeModules.SQLite.executeSqlBatch).toHaveBeenCalledWith(
    {
      dbargs: { dbname: 'filename' },
      executes: [
        {
          qid: 1,
          sql: 'INVALID',
          params: []
        }
      ]
    },
    expect.any(Function),
    expect.any(Function)
  );
});

test('Execute empty batch', async () => {
  const db = await sqlite.open('filename');
  const results = await db.executeBatch([]);

  expect(results).toEqual([]);
  expect(NativeModules.SQLite.executeSqlBatch).not.toHaveBeenCalled();
});

test('Get item', async () => {
  NativeModules.SQLite.executeSqlBatch.mockImplementationOnce(
    (args, success, error) => {
      success([
        {
          qid: 1,
          result: { rows: [{ id: 1, val: 'test' }] },
          type: 'success'
        }
      ]);
    }
  );

  // Execute.
  const db = await sqlite.open('filename');
  const result = await db.get('SELECT id, val FROM foo WHERE id = ?', [1]);

  expect(result).toEqual({ id: 1, val: 'test' });

  expect(NativeModules.SQLite.executeSqlBatch).toHaveBeenCalledWith(
    {
      dbargs: { dbname: 'filename' },
      executes: [
        {
          qid: 1,
          sql: 'SELECT id, val FROM foo WHERE id = ?',
          params: [1]
        }
      ]
    },
    expect.any(Function),
    expect.any(Function)
  );
});

test('Get item no results', async () => {
  NativeModules.SQLite.executeSqlBatch.mockImplementationOnce(
    (args, success, error) => {
      success([
        {
          qid: 1,
          result: { rows: [] },
          type: 'success'
        }
      ]);
    }
  );

  // Execute.
  const db = await sqlite.open('filename');
  const result = await db.get('SELECT id, val FROM foo WHERE id = ?', [1]);

  expect(result).toBeNull();

  expect(NativeModules.SQLite.executeSqlBatch).toHaveBeenCalledWith(
    {
      dbargs: { dbname: 'filename' },
      executes: [
        {
          qid: 1,
          sql: 'SELECT id, val FROM foo WHERE id = ?',
          params: [1]
        }
      ]
    },
    expect.any(Function),
    expect.any(Function)
  );
});

test('Close database', async () => {
  const db = await sqlite.open('filename');
  await db.close();

  expect(NativeModules.SQLite.close).toHaveBeenCalledWith(
    { path: 'filename' },
    expect.any(Function),
    expect.any(Function)
  );
});
