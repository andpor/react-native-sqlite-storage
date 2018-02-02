package org.pgsqlite.sqlite.plugin;

import android.content.Context;

/**
 * Written by Sergei Dryganets Jul 24/2017
 */
public interface DatabaseConnectionProvider {

	int OPEN_READWRITE = 0x00000000;
	int OPEN_READONLY = 0x00000001;

	/**
	 * Open flag: Flag for {@link #openDatabase} to open the database without support for
	 * localized collators.
	 *
	 * {@more} This causes the collator <code>LOCALIZED</code> not to be created.
	 * You must be consistent when using this flag to use the setting the database was
	 * created with.  If this is set, {@link #setLocale} will do nothing.
	 */
	int NO_LOCALIZED_COLLATORS = 0x00000010;

	/**
	 * Open flag: Flag for {@link #openDatabase} to create the database file if it does not
	 * already exist.
	 */
	int CREATE_IF_NECESSARY = 0x10000000;

	/**
	 * Open flag: Flag for {@link #openDatabase} to open the database file with
	 * write-ahead logging enabled by default.
	 * Write-ahead logging cannot be used with read-only databases so the value of
	 * this flag is ignored if the database is opened read-only.
	 *
	 */
	int ENABLE_WRITE_AHEAD_LOGGING = 0x20000000;

	Database openDatabase(String databasePath, String password, int openFlags);
}

