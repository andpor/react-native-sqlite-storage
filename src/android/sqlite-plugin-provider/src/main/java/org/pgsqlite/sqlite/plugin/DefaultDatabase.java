package org.pgsqlite.sqlite.plugin;

import android.database.sqlite.SQLiteDatabase;

import java.io.IOException;

/**
 * Written by Sergei Dryganets Jul 24/2017
 */
public class DefaultDatabase implements Database {
	private final SQLiteDatabase database;

	public DefaultDatabase(SQLiteDatabase database) {
		this.database = database;
	}

	@Override
	public void execSQL(String sql) {
		database.execSQL(sql);
	}

	@Override
	public SQLStatement compileStatement(String sql) {
		return new DefaultStatement(database.compileStatement(sql));
	}

	@Override
	public ICursor rawQuery(String sql, String[] params) {
		return new DefaultCursor(database.rawQuery(sql, params));
	}

	@Override
	public boolean isOpen() {
		return database.isOpen();
	}

	@Override
	public void beginTransaction() {
		database.beginTransaction();
	}

	@Override
	public void setTransactionSuccessful() {
		database.setTransactionSuccessful();
	}

	@Override
	public void endTransaction() {
		database.endTransaction();
	}

	@Override
	public void close() throws IOException {
		database.close();
	}
}
