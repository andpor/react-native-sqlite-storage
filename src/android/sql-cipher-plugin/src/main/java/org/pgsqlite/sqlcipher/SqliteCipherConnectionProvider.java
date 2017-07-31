package org.pgsqlite.sqlcipher;

import android.content.Context;

import org.pgsqlite.sqlite.plugin.Database;
import org.pgsqlite.sqlite.plugin.DatabaseConnectionProvider;

import net.sqlcipher.DatabaseErrorHandler;
import net.sqlcipher.SQLException;
import net.sqlcipher.database.SQLiteDatabase;

/**
 * Written by Sergei Dryganets Jul 24/2017
 */
public class SqliteCipherConnectionProvider implements DatabaseConnectionProvider {
	private static volatile boolean sNativeLibraryLoaded;

	public SqliteCipherConnectionProvider(Context context) {
		if (!sNativeLibraryLoaded) {
			sNativeLibraryLoaded = true;
			SQLiteDatabase.loadLibs(context);
		}
	}

	@Override
	public Database openDatabase(String databasePath, String password, int openFlags) {
		return new SqliteCipherDatabase(SQLiteDatabase.openDatabase(databasePath, password, null,
				openFlags, null, new DBErrorHandler()));
	}

	private class DBErrorHandler implements DatabaseErrorHandler {
		@Override
		public void onCorruption(SQLiteDatabase dbObj) {
			throw new SQLException("Database is corrupted");
		}
	}
}
