package org.pgsqlite.sqlite.plugin;

import android.content.Context;
import android.database.DatabaseErrorHandler;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

/**
 * Written by Sergei Dryganets Jul 24/2017
 */
public class DefaultConnectionProvider implements DatabaseConnectionProvider {
	/**
	 * Open flag: Flag for {@link #openDatabase} to open the database without support for
	 * localized collators.
	 *
	 * {@more} This causes the collator <code>LOCALIZED</code> not to be created.
	 * You must be consistent when using this flag to use the setting the database was
	 * created with.  If this is set, {@link #setLocale} will do nothing.
	 */
	public static final int NO_LOCALIZED_COLLATORS = 0x00000010;

	/**
	 * Open flag: Flag for {@link #openDatabase} to open the database file with
	 * write-ahead logging enabled by default.
	 * Write-ahead logging cannot be used with read-only databases so the value of
	 * this flag is ignored if the database is opened read-only.
	 *
	 */
	public static final int ENABLE_WRITE_AHEAD_LOGGING = 0x20000000;

	@Override
	public Database openDatabase(String databasePath, String password, int openFlags) {
		return new DefaultDatabase(SQLiteDatabase.openDatabase(databasePath, null, openFlags, new DBErrorHandler()));
	}

	@Override
	public void init(Context context) {
	}

	private class DBErrorHandler implements DatabaseErrorHandler {
		@Override
		public void onCorruption(SQLiteDatabase dbObj) {
			throw new SQLException("Database is corrupted");
		}
	}
}
