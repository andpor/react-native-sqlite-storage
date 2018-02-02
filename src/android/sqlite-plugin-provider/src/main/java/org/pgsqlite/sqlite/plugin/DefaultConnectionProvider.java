package org.pgsqlite.sqlite.plugin;

import android.content.Context;
import android.database.DatabaseErrorHandler;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

/**
 * Written by Sergei Dryganets Jul 24/2017
 */
public class DefaultConnectionProvider implements DatabaseConnectionProvider {

	@Override
	public Database openDatabase(String databasePath, String password, int openFlags) {
		return new DefaultDatabase(SQLiteDatabase.openDatabase(databasePath, null, openFlags, new DBErrorHandler()));
	}

	private class DBErrorHandler implements DatabaseErrorHandler {
		@Override
		public void onCorruption(SQLiteDatabase dbObj) {
			throw new SQLException("Database is corrupted");
		}
	}
}
