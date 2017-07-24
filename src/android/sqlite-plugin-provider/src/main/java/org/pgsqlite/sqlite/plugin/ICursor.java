package org.pgsqlite.sqlite.plugin;

import java.io.Closeable;

/**
 * Written by Sergei Dryganets Jul 24/2017
 */
public interface ICursor extends Closeable {
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
