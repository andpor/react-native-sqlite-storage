package org.pgsqlite.sqlcipher;

import org.pgsqlite.sqlite.plugin.SQLStatement;

import java.io.IOException;

/**
 * Written by Sergei Dryganets Jul 24/2017
 */
public class SqliteCipherStatement implements SQLStatement {
	private final net.sqlcipher.database.SQLiteStatement statement;
	public SqliteCipherStatement(net.sqlcipher.database.SQLiteStatement statement) {
		this.statement = statement;
	}

	@Override
	public void bindDouble(int i, double value) {
		statement.bindDouble(i, value);
	}

	@Override
	public void bindString(int i, String value) {
		statement.bindString(i, value);
	}

	@Override
	public void bindNull(int i) {
		statement.bindNull(i);
	}

	@Override
	public void bindLong(int i, long value) {
		statement.bindLong(i, value);
	}

	@Override
	public long executeInsert() {
		return statement.executeInsert();
	}

	@Override
	public int executeUpdateDelete() {
		return statement.executeUpdateDelete();
	}

	@Override
	public void close() throws IOException {
		statement.close();
	}
}
