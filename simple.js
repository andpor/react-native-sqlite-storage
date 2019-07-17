/**
 * @flow strict-local
 */

'use strict';

const { NativeModules } = require('react-native');
const { SQLite } = NativeModules;

export type DataType = number | string;

export type QueryFailResult = {
  qid: number,
  result: {
    message: string
  },
  type: 'error'
};

export type QuerySuccessResult<T> = {
  qid: number,
  result: {
    insertId?: number,
    rows?: T[],
    rowsAffected?: number
  },
  type: 'success'
};

export type QueryResult<T> = QueryFailResult | QuerySuccessResult<T>;

class Database {
  lastQueryID: number;
  name: string;

  constructor(name: string) {
    this.lastQueryID = 0;
    this.name = name;
  }

  /**
   * Get all results from the query.
   */
  async all<T>(sql: string, params?: DataType[]): Promise<T[]> {
    const results = await this.executeBatch<T>([{ sql, params }]);
    const result = results[0];
    if (result.type === 'success') {
      if (result.result.rows == null) {
        // Statement was not a SELECT.
        return [];
      } else {
        return result.result.rows;
      }
    } else {
      throw new Error(result.result.message);
    }
  }

  /**
   * Close the database.
   */
  async close(): Promise<void> {
    return new Promise((resolve, reject) => {
      SQLite.close({ path: this.name }, () => resolve(), reject);
    });
  }

  /**
   * Execute a statement.
   */
  async exec(sql: string, params?: DataType[]): Promise<void> {
    await this.all(sql, params);
  }

  /**
   * Execute a batch of queries, returning the status and output of each.
   */
  async executeBatch<T>(
    queries: { sql: string, params?: DataType[] }[]
  ): Promise<QueryResult<T>[]> {
    if (queries.length === 0) {
      return [];
    }

    const executes: { qid: number, sql: string, params: ?(DataType[]) }[] = [];
    for (const query of queries) {
      this.lastQueryID += 1;
      executes.push({
        qid: this.lastQueryID,
        sql: query.sql,
        params: query.params == null ? [] : query.params
      });
    }

    return new Promise((resolve, reject) => {
      SQLite.executeSqlBatch(
        {
          dbargs: { dbname: this.name },
          executes
        },
        resolve,
        reject
      );
    });
  }

  /**
   * Get the first result from the query.
   */
  async get<T>(sql: string, params?: DataType[]): Promise<?T> {
    const rows = await this.all<T>(sql, params);
    return rows.length > 0 ? rows[0] : null;
  }
}

export type { Database };

/**
 * Open the database.
 */
async function open(filename: string): Promise<Database> {
  return new Promise((resolve, reject) => {
    SQLite.open(
      { name: filename, dblocation: 'nosync' },
      () => resolve(new Database(filename)),
      reject
    );
  });
}

module.exports = { open };
