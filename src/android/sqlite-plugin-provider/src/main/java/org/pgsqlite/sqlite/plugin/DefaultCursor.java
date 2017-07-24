package org.pgsqlite.sqlite.plugin;

import android.database.Cursor;

import java.io.IOException;

/**
 * Written by Sergei Dryganets Jul 24/2017
 */
public class DefaultCursor implements ICursor {
	private final Cursor mCursor;

	public DefaultCursor(android.database.Cursor cursor) {
		mCursor = cursor;
	}

	@Override
	public boolean moveToFirst() {
		return mCursor.moveToFirst();
	}

	@Override
	public boolean moveToNext() {
		return mCursor.moveToNext();
	}

	@Override
	public int getColumnCount() {
		return mCursor.getColumnCount();
	}

	@Override
	public String getColumnName(int i) {
		return mCursor.getColumnName(i);
	}

	@Override
	public int getType(int i) {
		return mCursor.getType(i);
	}

	@Override
	public long getLong(int i) {
		return mCursor.getLong(i);
	}

	@Override
	public double getDouble(int i) {
		return mCursor.getDouble(i);
	}

	@Override
	public String getString(int i) {
		return mCursor.getString(i);
	}

	@Override
	public byte[] getBlob(int i) {
		return mCursor.getBlob(i);
	}

	@Override
	public int getCount() {
		return mCursor.getCount();
	}

	@Override
	public void close() throws IOException {
		mCursor.close();
	}
}
