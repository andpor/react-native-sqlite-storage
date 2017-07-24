package org.pgsqlite.sqlite.plugin;

import java.io.Closeable;

/**
 * Written by Sergei Dryganets Jul 24/2017
 */
public interface Database extends Closeable {
	void execSQL(String sql);
	SQLStatement compileStatement(String sql);
	ICursor rawQuery(String sql, String[] params);
	boolean isOpen();

	void beginTransaction();
	void setTransactionSuccessful();
	void endTransaction();
}
