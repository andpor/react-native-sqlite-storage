package org.pgsqlite.sqlcipher;

import net.sqlcipher.Cursor;

import org.pgsqlite.sqlite.plugin.ICursor;

import java.io.IOException;

/**
 * Written by Sergei Dryganets Jul 24/2017
 */
public class SqliteCipherCursor implements ICursor {
	private final Cursor cursor;

	public SqliteCipherCursor(Cursor cursor) {
		this.cursor = cursor;
	}

	@Override
	public boolean moveToFirst() {
		return cursor.moveToFirst();
	}

	@Override
	public boolean moveToNext() {
		return cursor.moveToNext();
	}

	@Override
	public int getColumnCount() {
		return cursor.getColumnCount();
	}

	@Override
	public String getColumnName(int i) {
		return cursor.getColumnName(i);
	}

	@Override
	public int getType(int i) {
		return cursor.getType(i);
	}

	@Override
	public long getLong(int i) {
		return cursor.getLong(i);
	}

	@Override
	public double getDouble(int i) {
		return cursor.getDouble(i);
	}

	@Override
	public String getString(int i) {
		return cursor.getString(i);
	}

	@Override
	public byte[] getBlob(int i) {
		return cursor.getBlob(i);
	}

	@Override
	public int getCount() {
		return cursor.getCount();
	}

	@Override
	public void close() throws IOException {
		cursor.close();
	}
}
