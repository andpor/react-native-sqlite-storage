package org.pgsqlite.sqlite.plugin;

import android.content.Context;

/**
 * Written by Sergei Dryganets Jul 24/2017
 */
public interface DatabaseConnectionProvider {

	int OPEN_READWRITE = 0x00000000;
	int OPEN_READONLY = 0x00000001;

	/**
	 * Open flag: Flag for {@link #openDatabase} to create the database file if it does not
	 * already exist.
	 */
	int CREATE_IF_NECESSARY = 0x10000000;

	void init(Context context);
	Database openDatabase(String databasePath, String password, int openFlags);
}

