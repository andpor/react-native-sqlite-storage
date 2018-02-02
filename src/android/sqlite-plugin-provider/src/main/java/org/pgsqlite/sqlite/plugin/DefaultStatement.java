package org.pgsqlite.sqlite.plugin;

import android.database.sqlite.SQLiteStatement;

import java.io.IOException;

/**
 * Written by Sergei Dryganets Jul 24/2017
 */
public class DefaultStatement implements SQLStatement {
	private final SQLiteStatement mStatement;

	public DefaultStatement(SQLiteStatement statement) {
		mStatement = statement;
	}

	@Override
	public void bindDouble(int i, double value) {
		mStatement.bindDouble(i, value);
	}

	@Override
	public void bindString(int i, String value) {
		mStatement.bindString(i, value);
	}

	@Override
	public void bindNull(int i) {
		mStatement.bindNull(i);
	}

	@Override
	public void bindLong(int i, long value) {
		mStatement.bindLong(i, value);
	}

	@Override
	public long executeInsert() {
		return mStatement.executeInsert();
	}

	@Override
	public int executeUpdateDelete() {
		return mStatement.executeUpdateDelete();
	}

	@Override
	public void close() throws IOException {
		mStatement.close();
	}
}
