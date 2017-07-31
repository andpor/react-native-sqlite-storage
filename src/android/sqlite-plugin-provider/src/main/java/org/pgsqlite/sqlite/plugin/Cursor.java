package org.pgsqlite.sqlite.plugin;

import java.io.Closeable;

/**
 * Written by Sergei Dryganets Jul 24/2017
 */
public interface Cursor extends Closeable {

	/*
     * Values returned by {@link #getType(int)}.
     * These should be consistent with the corresponding types defined in CursorWindow.h
     */
	/** Value returned by {@link #getType(int)} if the specified column is null */
	int FIELD_TYPE_NULL = 0;

	/** Value returned by {@link #getType(int)} if the specified  column type is integer */
	int FIELD_TYPE_INTEGER = 1;

	/** Value returned by {@link #getType(int)} if the specified column type is float */
	int FIELD_TYPE_FLOAT = 2;

	/** Value returned by {@link #getType(int)} if the specified column type is string */
	int FIELD_TYPE_STRING = 3;

	/** Value returned by {@link #getType(int)} if the specified column type is blob */
	int FIELD_TYPE_BLOB = 4;

	boolean moveToFirst();

	boolean moveToNext();

	int getColumnCount();

	String getColumnName(int i);

	int getType(int i);

	long getLong(int i);

	double getDouble(int i);

	String getString(int i);

	byte[] getBlob(int i);

	int getCount();
}
