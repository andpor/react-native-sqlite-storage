package org.pgsqlite.sqlite.plugin;

import java.io.Closeable;

/**
 * Written by Sergei Dryganets Jul 24/2017
 */
public interface SQLStatement extends Closeable {
	void bindDouble(int i, double value);

	void bindString(int i, String value);

	void bindNull(int i);

	void bindLong(int i, long value);

	long executeInsert();

	int executeUpdateDelete();
}
