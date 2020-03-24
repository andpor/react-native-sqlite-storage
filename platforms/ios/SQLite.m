/*
 * SQLite.m
 *
 * Created by Andrzej Porebski on 10/29/15.
 * Copyright (c) 2015 Andrzej Porebski.
 *
 * This software is largely based on the SQLLite Storage Cordova Plugin created by Chris Brody & Davide Bertola.
 * The implementation was adopted and converted to use React Native bindings.
 *
 * See https://github.com/litehelpers/Cordova-sqlite-storage
 *
 * This library is available under the terms of the MIT License (2008).
 * See http://opensource.org/licenses/alphabetical for full text.
 */

#import <Foundation/Foundation.h>

#import "SQLite.h"
#import "SQLiteResult.h"

#import <React/RCTLog.h>
#import <React/RCTUtils.h>
#import <React/RCTBridge.h>
#import <React/RCTEventDispatcher.h>

/*
 * Copyright (C) 2015-Present Andrzej Porebski
 * Copyright (C) 2012-2015 Chris Brody
 * Copyright (C) 2011 Davide Bertola
 *
 * This library is available under the terms of the MIT License (2008).
 * See http://opensource.org/licenses/alphabetical for full text.
 */

#import "sqlite3.h"

#include <regex.h>

static void sqlite_regexp(sqlite3_context* context, int argc, sqlite3_value** values) {
  if ( argc < 2 ) {
    sqlite3_result_error(context, "SQL function regexp() called with missing arguments.", -1);
    return;
  }
  
  char* reg  = (char*) sqlite3_value_text(values[0]);
  char* text = (char*) sqlite3_value_text(values[1]);
  
  if ( argc != 2 || reg == 0 || text == 0) {
    sqlite3_result_error(context, "SQL function regexp() called with invalid arguments.", -1);
    return;
  }
  
  int ret;
  regex_t regex;
  
  ret = regcomp(&regex, reg, REG_EXTENDED | REG_NOSUB);
  if ( ret != 0 ) {
    sqlite3_result_error(context, "error compiling regular expression", -1);
    return;
  }
  
  ret = regexec(&regex, text , 0, NULL, 0);
  regfree(&regex);
  
  sqlite3_result_int(context, (ret != REG_NOMATCH));
}


@implementation SQLite

RCT_EXPORT_MODULE();

@synthesize openDBs;
@synthesize appDBPaths;

- (id) init
{
  RCTLog(@"Initializing SQLitePlugin");
  self = [super init];
  if (self) {
    openDBs = [NSMutableDictionary dictionaryWithCapacity:0];
    appDBPaths = [NSMutableDictionary dictionaryWithCapacity:0];
#if !__has_feature(objc_arc)
    [openDBs retain];
    [appDBPaths retain];
#endif
    
    NSString *docs = [NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES) objectAtIndex: 0];
    RCTLog(@"Detected docs path: %@", docs);
    [appDBPaths setObject: docs forKey:@"docs"];
    
    NSString *libs = [NSSearchPathForDirectoriesInDomains(NSLibraryDirectory, NSUserDomainMask, YES) objectAtIndex: 0];
    RCTLog(@"Detected Library path: %@", libs);
    [appDBPaths setObject: libs forKey:@"libs"];
    
    NSString *nosync = [libs stringByAppendingPathComponent:@"LocalDatabase"];
    NSError *err;
    if ([[NSFileManager defaultManager] fileExistsAtPath: nosync])
    {
      RCTLog(@"no cloud sync at path: %@", nosync);
      [appDBPaths setObject: nosync forKey:@"nosync"];
    }
    else
    {
      if ([[NSFileManager defaultManager] createDirectoryAtPath: nosync withIntermediateDirectories:NO attributes: nil error:&err])
      {
        NSURL *nosyncURL = [ NSURL fileURLWithPath: nosync];
        if (![nosyncURL setResourceValue: [NSNumber numberWithBool: YES] forKey: NSURLIsExcludedFromBackupKey error: &err])
        {
          RCTLog(@"IGNORED: error setting nobackup flag in LocalDatabase directory: %@", err);
        }
        RCTLog(@"no cloud sync at path: %@", nosync);
        [appDBPaths setObject: nosync forKey:@"nosync"];
      }
      else
      {
        // fallback:
        RCTLog(@"WARNING: error adding LocalDatabase directory: %@", err);
        [appDBPaths setObject: libs forKey:@"nosync"];
      }
    }

    NSString *groupName = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"AppGroupName"];
    NSURL *groupURL = [[NSFileManager defaultManager] containerURLForSecurityApplicationGroupIdentifier:groupName];
    if (groupURL != NULL)
    {
       NSString* shared = groupURL.path;
       RCTLog(@"Detected Shared path: %@", shared);
       [appDBPaths setObject: shared forKey:@"shared"];
    }
  }
  return self;
}

- (void)runInBackground:(void (^)())block
{
  dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), block);
}

-(id) getDBPath:(NSString *)dbFile at:(NSString *)atkey {
  if (dbFile == NULL) {
    return NULL;
  }
  
  NSString *dbdir = appDBPaths[atkey];
  NSString *dbPath = [dbdir stringByAppendingPathComponent: dbFile];
  return dbPath;
}

RCT_EXPORT_METHOD(echoStringValue: (NSDictionary *) options success:(RCTResponseSenderBlock)success error:(RCTResponseSenderBlock)error)
{
  NSString * string_value = options[@"value"];
  RCTLog(@"echo string value: %@", string_value);
  
  SQLiteResult* pluginResult = [SQLiteResult resultWithStatus:SQLiteStatus_OK messageAsString:string_value];
  [pluginResult.status intValue] == SQLiteStatus_OK ? success(@[pluginResult.message]) : error(@[pluginResult.message]);
}

RCT_EXPORT_METHOD(open: (NSDictionary *) options success:(RCTResponseSenderBlock)success error:(RCTResponseSenderBlock)error)
{
  SQLiteResult* pluginResult = nil;
  NSString *dbname;
  int sqlOpenFlags = SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE;
  
  @synchronized (self) {
    NSString *dbfilename = options[@"name"];
    if (dbfilename == NULL) {
      RCTLog(@"No db name specified for open");
      pluginResult = [SQLiteResult resultWithStatus:SQLiteStatus_OK messageAsString:@"You must specify database name"];
    } else {
      NSDictionary *dbInfo = openDBs[dbfilename];
      if (dbInfo != NULL && dbInfo[@"dbPointer"] != NULL) {
        RCTLog(@"Reusing existing database connection for db name %@", dbfilename);
        pluginResult = [SQLiteResult resultWithStatus:SQLiteStatus_OK messageAsString:@"Database opened"];
      } else {
        NSString *assetFilePath = options[@"assetFilename"];
        if (assetFilePath != NULL && assetFilePath.length > 0) {
          @try {
            if ([assetFilePath isEqualToString:@"1"]){
              NSString *targetBundleDirPath = [[NSBundle mainBundle] resourcePath];
              targetBundleDirPath = [targetBundleDirPath stringByAppendingPathComponent: @"www"];
              assetFilePath = [targetBundleDirPath stringByAppendingPathComponent: dbfilename];
              RCTLog(@"Built path to pre-populated DB asset from app bundle www subdirectory: %@",assetFilePath);
            } else if ([assetFilePath hasPrefix:@"~"]) {
              assetFilePath = [assetFilePath substringFromIndex:1];
              NSString *targetBundleDirPath = [[NSBundle mainBundle] resourcePath];
              assetFilePath = [targetBundleDirPath stringByAppendingPathComponent: assetFilePath];
              RCTLog(@"Built path to pre-populated DB asset from app bundle subdirectory: %@",assetFilePath);
            } else {
              NSURL * documentsDirUrl = [[[NSFileManager defaultManager] URLsForDirectory:NSDocumentDirectory
                                                                        inDomains:NSUserDomainMask] lastObject];
              assetFilePath = [documentsDirUrl.path stringByAppendingPathComponent:assetFilePath];
              RCTLog(@"Built path to pre-populated DB asset from app sandbox documents directory: %@",assetFilePath);
            }
          } @catch(NSException *ex){
            RCTLog(@"Error building path for pre-populated DB asset %@",ex.reason);
          }
        }
      
        if (options[@"readOnly"] && assetFilePath != NULL){
          sqlOpenFlags = SQLITE_OPEN_READONLY;
          dbname = assetFilePath;
        } else {
          NSString *dblocation = options[@"dblocation"];
          if (dblocation == NULL) dblocation = @"nosync";
          RCTLog(@"target database location: %@", dblocation);
        
          dbname = [self getDBPath:dbfilename at:dblocation];
        
          /* Option to create from resource (pre-populated) if db does not exist: */
          if (![[NSFileManager defaultManager] fileExistsAtPath:dbname] && assetFilePath != NULL) {
            RCTLog(@"Copying pre-populated asset to the destination directory");
            [self createFromResource:assetFilePath withDbname:dbname];
          }
        }
      
        RCTLog(@"Opening db in mode %@, full path: %@", (sqlOpenFlags == SQLITE_OPEN_READONLY) ? @"READ ONLY" : @"READ_WRITE",dbname);
      
        const char *name = [dbname UTF8String];
        sqlite3 *db;
      
        if (sqlite3_open_v2(name, &db,sqlOpenFlags, NULL) != SQLITE_OK) {
          pluginResult = [SQLiteResult resultWithStatus:SQLiteStatus_ERROR messageAsString:@"Unable to open DB"];
          return;
        } else {
          sqlite3_create_function(db, "regexp", 2, SQLITE_ANY, NULL, &sqlite_regexp, NULL, NULL);
          const char *key = NULL;
          
#ifdef SQLCIPHER
          NSString *dbkey = options[@"key"];
          if (dbkey != NULL) {
            key = [dbkey UTF8String];
            if (key != NULL) {
              sqlite3_key(db, key, strlen(key));
            }
          }
#endif

          sqlite3_exec(db, "PRAGMA journal_mode=WAL;", NULL, NULL, NULL);

          // Attempt to read the SQLite master table [to support SQLCipher version]:
          if(sqlite3_exec(db, (const char*)"SELECT count(*) FROM sqlite_master;", NULL, NULL, NULL) == SQLITE_OK) {
            NSValue *dbPointer = [NSValue valueWithPointer:db];
            openDBs[dbfilename] = @{ @"dbPointer": dbPointer, @"dbPath" : dbname};
            NSString *msg = (key != NULL) ? @"Secure database opened" : @"Database opened";
            pluginResult = [SQLiteResult resultWithStatus:SQLiteStatus_OK messageAsString: msg];
            RCTLog(@"%@", msg);
          } else {
            NSString *msg = [NSString stringWithFormat:@"Unable to open %@", (key != NULL) ? @"secure database with key" : @"database"];
            pluginResult = [SQLiteResult resultWithStatus:SQLiteStatus_ERROR messageAsString:msg];
            RCTLog(@"%@", msg);
            sqlite3_close (db);
            [openDBs removeObjectForKey:dbfilename];
          }
        }
      }
    }
  }
  
  if (sqlite3_threadsafe()) {
    RCTLog(@"Good news: SQLite is thread safe!");
  } else {
    RCTLog(@"Warning: SQLite is not thread safe.");
  }
  
  [pluginResult.status intValue] == SQLiteStatus_OK ? success(@[pluginResult.message]) : error(@[pluginResult.message]);
  RCTLog(@"open cb finished ok");
}

-(void)createFromResource:(NSString *)prepopulatedDb withDbname:(NSString *)dbname {
  RCTLog(@"Looking for prepopulated DB at: %@", prepopulatedDb);
  
  if ([[NSFileManager defaultManager] fileExistsAtPath:prepopulatedDb]) {
    RCTLog(@"Found prepopulated DB: %@", prepopulatedDb);
    NSError *error;
    BOOL success = [[NSFileManager defaultManager] copyItemAtPath:prepopulatedDb toPath:dbname error:&error];
    
    if(success) {
      RCTLog(@"Copied prepopulated DB content to: %@", dbname);
    } else {
      RCTLog(@"Unable to copy DB file: %@", [error localizedDescription]);
    }
  }
}


RCT_EXPORT_METHOD(close: (NSDictionary *) options success:(RCTResponseSenderBlock)success error:(RCTResponseSenderBlock)error)
{
  SQLiteResult* pluginResult = nil;
  @synchronized (self) {
    NSString *dbFileName = options[@"path"];
    if (dbFileName == NULL) {
      // Should not happen:
      RCTLog(@"No db name specified for close");
      pluginResult = [SQLiteResult resultWithStatus:SQLiteStatus_ERROR messageAsString:@"You must specify database path"];
    } else {
      NSDictionary *dbInfo = openDBs[dbFileName];
      if (dbInfo == NULL || dbInfo[@"dbPointer"] == NULL) {
        RCTLog(@"close: db name was not found in open databases: %@", dbFileName);
        pluginResult = [SQLiteResult resultWithStatus:SQLiteStatus_ERROR messageAsString:@"Specified db was not open"];
      } else {
        sqlite3 *db = [((NSValue *) dbInfo[@"dbPointer"]) pointerValue];
        NSString *dbPath = dbInfo[@"dbPath"];
            
        if ([[NSFileManager defaultManager] fileExistsAtPath:dbPath]) {
          RCTLog(@"close: database still exists at path %@, proceeding to close it.",dbPath);
        }
            
        if (db == NULL) {
          // Should not happen:
          RCTLog(@"close: db name was not open: %@", dbFileName);
          pluginResult = [SQLiteResult resultWithStatus:SQLiteStatus_ERROR messageAsString:@"Specified db was not open"];
        } else {
          RCTLog(@"close: closing db: %@", dbFileName);
            sqlite3_close (db);
            [openDBs removeObjectForKey:dbFileName];
            pluginResult = [SQLiteResult resultWithStatus:SQLiteStatus_OK messageAsString:@"DB closed"];
        }

        if ([[NSFileManager defaultManager]fileExistsAtPath:dbPath]) {
          RCTLog(@"database file still exists after close");
        } else {
          RCTLog(@"database file doesn't exists after close");
        }
      }
    }
  }
  
  [pluginResult.status intValue] == SQLiteStatus_OK ? success(@[pluginResult.message]) : error(@[pluginResult.message]);
}

RCT_EXPORT_METHOD(attach: (NSDictionary *) options success:(RCTResponseSenderBlock)success error:(RCTResponseSenderBlock)error)
{
  SQLiteResult* pluginResult = nil;
  
  NSString *dbFileName = options[@"path"]; // path to current database instance

  NSString *dbName = options[@"dbName"]; // dbName that should be attached to dbFileName
  NSString *dbAlias = options[@"dbAlias"]; // the Alias that should be use with ATTACH DATABASE
  
  if (dbName == NULL || dbAlias == NULL) {
    // Should not happen:
    RCTLog(@"No dbName or dbAlias specified for attach");
    pluginResult = [SQLiteResult resultWithStatus:SQLiteStatus_ERROR messageAsString:@"You must specify dbName and dbAlias"];
  } else {
    RCTLog(@"Will attach %@ with alias %@", dbName, dbAlias);
    @synchronized (self) {
      NSDictionary *dbInfo = openDBs[dbFileName];
      NSDictionary *dbInfoToAttach = openDBs[dbName];
      
      if (dbInfo == NULL || dbInfo[@"dbPointer"] == NULL) {
        RCTLog(@"attach: db name was not found in open databases: %@", dbFileName);
        pluginResult = [SQLiteResult resultWithStatus:SQLiteStatus_ERROR messageAsString:@"Specified db was not open"];
      }
      else if (dbInfoToAttach == NULL || dbInfoToAttach[@"dbPointer"] == NULL) {
        RCTLog(@"attach: db name was not found in open databases: %@", dbName);
        pluginResult = [SQLiteResult resultWithStatus:SQLiteStatus_ERROR messageAsString:@"Specified db for ALIAS was not open"];
      } else {
        sqlite3 *db = [((NSValue *) dbInfo[@"dbPointer"]) pointerValue];
        NSString *dbPathToAttach = dbInfoToAttach[@"dbPath"];
        
        NSString* sql = [NSString stringWithFormat:@"ATTACH DATABASE '%@' AS %@", dbPathToAttach, dbAlias];
        
        if(sqlite3_exec(db, [sql UTF8String], NULL, NULL, NULL) == SQLITE_OK) {
          pluginResult = [SQLiteResult resultWithStatus:SQLiteStatus_OK messageAsString:@"Database attached successfully."];
        } else {
          pluginResult = [SQLiteResult resultWithStatus:SQLiteStatus_ERROR messageAsString:@"Unable to attach DB"];
        }
      }
    }
  }
  
  [pluginResult.status intValue] == SQLiteStatus_OK ? success(@[pluginResult.message]) : error(@[pluginResult.message]);
}

RCT_EXPORT_METHOD(delete: (NSDictionary *) options success:(RCTResponseSenderBlock)success error:(RCTResponseSenderBlock)error)
{
  SQLiteResult* pluginResult = nil;
  NSString *dbfilename = options[@"path"];
  NSString *dblocation = options[@"dblocation"];
    
  @synchronized (self) {
    if (dbfilename == NULL) {
      // Should not happen:
      RCTLog(@"No db name specified for delete");
      pluginResult = [SQLiteResult resultWithStatus:SQLiteStatus_ERROR messageAsString:@"You must specify database path"];
    } else {
      if (dblocation == NULL) dblocation = @"nosync";
      NSString *dbPath = [self getDBPath:dbfilename at:dblocation];
    
      if ([[NSFileManager defaultManager] fileExistsAtPath:dbPath]) {
        RCTLog(@"delete full db path: %@", dbPath);
        [[NSFileManager defaultManager]removeItemAtPath:dbPath error:nil];
        [openDBs removeObjectForKey:dbfilename];
        pluginResult = [SQLiteResult resultWithStatus:SQLiteStatus_OK messageAsString:@"DB deleted"];
      } else {
        RCTLog(@"delete: db was not found: %@", dbPath);
        pluginResult = [SQLiteResult resultWithStatus:SQLiteStatus_ERROR messageAsString:@"The database does not exist on that path"];
      }
    }
  }
  
  [pluginResult.status intValue] == SQLiteStatus_OK ? success(@[pluginResult.message]) : error(@[pluginResult.message]);
}


RCT_EXPORT_METHOD(backgroundExecuteSqlBatch: (NSDictionary *) options success:(RCTResponseSenderBlock)success error:(RCTResponseSenderBlock)error)
{
  [self runInBackground:^{
    [self executeSqlBatch: options success:success error:error];
  }];
}

RCT_EXPORT_METHOD(executeSqlBatch: (NSDictionary *) options success:(RCTResponseSenderBlock)success error:(RCTResponseSenderBlock)error)
{
  NSMutableArray *results = [NSMutableArray arrayWithCapacity:0];
  NSMutableDictionary *dbargs = options[@"dbargs"];
  NSMutableArray *executes = options[@"executes"];
  
  SQLiteResult *pluginResult;
  
  @synchronized(self) {
    for (NSMutableDictionary *dict in executes) {
      SQLiteResult *result = [self executeSqlWithDict:dict andArgs:dbargs];
      if ([result.status intValue] == SQLiteStatus_ERROR) {
        /* add error with result.message: */
        NSMutableDictionary *r = [NSMutableDictionary dictionaryWithCapacity:4];
        [r setObject:dict[@"qid"] forKey:@"qid"];
        [r setObject:@"error" forKey:@"type"];
        [r setObject:result.message forKey:@"error"];
        [r setObject:result.message forKey:@"result"];
        [results addObject: r];
      } else {
        /* add result with result.message: */
        NSMutableDictionary *r = [NSMutableDictionary dictionaryWithCapacity:3];
        [r setObject:dict[@"qid"] forKey:@"qid"];
        [r setObject:@"success" forKey:@"type"];
        [r setObject:result.message forKey:@"result"];
        [results addObject: r];
      }
    }
    
    pluginResult = [SQLiteResult resultWithStatus:SQLiteStatus_OK messageAsArray:results];
  }
  
  success(@[pluginResult.message]);
}

RCT_EXPORT_METHOD(backgroundExecuteSql: (NSDictionary *) options success:(RCTResponseSenderBlock)success error:(RCTResponseSenderBlock)error)
{
  [self runInBackground:^{
    [self executeSql:options success:success error:error];
  }];
}

RCT_EXPORT_METHOD(executeSql: (NSDictionary *) options success:(RCTResponseSenderBlock)success error:(RCTResponseSenderBlock)error)
{
  NSMutableDictionary *dbargs = options[@"dbargs"];
  NSMutableDictionary *ex = options[@"ex"];
  
  SQLiteResult* pluginResult;
  @synchronized (self) {
    pluginResult = [self executeSqlWithDict: ex andArgs: dbargs];
  }
  
  success(@[pluginResult.message]);
}

-(SQLiteResult *) executeSqlWithDict: (NSMutableDictionary*)options andArgs: (NSMutableDictionary*)dbargs
{
  NSString *dbfilename = dbargs[@"dbname"];
  if (dbfilename == NULL) {
    return [SQLiteResult resultWithStatus:SQLiteStatus_ERROR messageAsString:@"You must specify database path"];
  }
  
  NSMutableArray *params = options[@"params"]; // optional
  
  NSDictionary *dbInfo = openDBs[dbfilename];
  if (dbInfo == NULL || dbInfo[@"dbPointer"] == NULL){
    return [SQLiteResult resultWithStatus:SQLiteStatus_ERROR messageAsString:@"No such database, you must open it first"];
  }
  
  sqlite3 *db = [((NSValue *) dbInfo[@"dbPointer"]) pointerValue];
  
  NSString *sql = options[@"sql"];
  if (sql == NULL) {
    return [SQLiteResult resultWithStatus:SQLiteStatus_ERROR messageAsString:@"You must specify a sql query to execute"];
  }
  
  const char *sql_stmt = [sql UTF8String];
  NSDictionary *error = nil;
  sqlite3_stmt *statement;
  int result, i, column_type, count;
  int previousRowsAffected, nowRowsAffected, diffRowsAffected;
  long long previousInsertId, nowInsertId;
  BOOL keepGoing = YES;
  BOOL hasInsertId;
  NSMutableDictionary *resultSet = [NSMutableDictionary dictionaryWithCapacity:0];
  NSMutableArray *resultRows = [NSMutableArray arrayWithCapacity:0];
  NSMutableDictionary *entry;
  NSObject *columnValue;
  NSString *columnName;
  NSObject *insertId;
  NSObject *rowsAffected;
  
  hasInsertId = NO;
  previousRowsAffected = sqlite3_total_changes(db);
  previousInsertId = sqlite3_last_insert_rowid(db);
  
  if (sqlite3_prepare_v2(db, sql_stmt, -1, &statement, NULL) != SQLITE_OK) {
    error = [SQLite captureSQLiteErrorFromDb:db];
    keepGoing = NO;
  } else if (params != NULL) {
    for (int b = 0; b < params.count; b++) {
      [self bindStatement:statement withArg:[params objectAtIndex:b] atIndex:(b+1)];
    }
  }
  
  //    RCTLog(@"inside executeSqlWithDict");
  while (keepGoing) {
    result = sqlite3_step (statement);
    switch (result) {
        
      case SQLITE_ROW:
        i = 0;
        entry = [NSMutableDictionary dictionaryWithCapacity:0];
        count = sqlite3_column_count(statement);
        
        while (i < count) {
          columnValue = nil;
          columnName = [NSString stringWithFormat:@"%s", sqlite3_column_name(statement, i)];
          
          column_type = sqlite3_column_type(statement, i);
          switch (column_type) {
            case SQLITE_INTEGER:
              columnValue = [NSNumber numberWithLongLong: sqlite3_column_int64(statement, i)];
              break;
            case SQLITE_FLOAT:
              columnValue = [NSNumber numberWithDouble: sqlite3_column_double(statement, i)];
              break;
            case SQLITE_BLOB:
              columnValue = [SQLite getBlobAsBase64String: sqlite3_column_blob(statement, i)
                                               withLength: sqlite3_column_bytes(statement, i)];
#ifdef INCLUDE_SQL_BLOB_BINDING // TBD subjet to change:
              columnValue = [@"sqlblob:;base64," stringByAppendingString:columnValue];
#endif
              break;
            case SQLITE_TEXT:
              columnValue = [[NSString alloc] initWithBytes:(char *)sqlite3_column_text(statement, i)
                                                     length:sqlite3_column_bytes(statement, i)
                                                   encoding:NSUTF8StringEncoding];
#if !__has_feature(objc_arc)
              [columnValue autorelease];
#endif
              break;
            case SQLITE_NULL:
              // just in case (should not happen):
            default:
              columnValue = [NSNull null];
              break;
          }
          
          if (columnValue) {
            [entry setObject:columnValue forKey:columnName];
          }
          
          i++;
        }
        [resultRows addObject:entry];
        break;
        
      case SQLITE_DONE:
        nowRowsAffected = sqlite3_total_changes(db);
        diffRowsAffected = nowRowsAffected - previousRowsAffected;
        rowsAffected = [NSNumber numberWithInt:diffRowsAffected];
        nowInsertId = sqlite3_last_insert_rowid(db);
        if (nowRowsAffected > 0 && nowInsertId != 0) {
          hasInsertId = YES;
          insertId = [NSNumber numberWithLongLong:sqlite3_last_insert_rowid(db)];
        }
        keepGoing = NO;
        break;
        
      default:
        error = [SQLite captureSQLiteErrorFromDb:db];
        keepGoing = NO;
    }
  }
  
  sqlite3_finalize (statement);
  
  if (error) {
    return [SQLiteResult resultWithStatus:SQLiteStatus_ERROR messageAsDictionary:error];
  }
  
  [resultSet setObject:resultRows forKey:@"rows"];
  [resultSet setObject:rowsAffected forKey:@"rowsAffected"];
  if (hasInsertId) {
    [resultSet setObject:insertId forKey:@"insertId"];
  }
  return [SQLiteResult resultWithStatus:SQLiteStatus_OK messageAsDictionary:resultSet];
}

-(void)bindStatement:(sqlite3_stmt *)statement withArg:(NSObject *)arg atIndex:(NSUInteger)argIndex
{
  if ([arg isEqual:[NSNull null]]) {
    sqlite3_bind_null(statement, (int) argIndex);
  } else if ([arg isKindOfClass:[NSNumber class]]) {
    NSNumber *numberArg = (NSNumber *)arg;
    const char *numberType = [numberArg objCType];
    if (strcmp(numberType, @encode(int)) == 0) {
      sqlite3_bind_int(statement, (int) argIndex, (int) [numberArg integerValue]);
    } else if (strcmp(numberType, @encode(long long int)) == 0) {
      sqlite3_bind_int64(statement, (int) argIndex, [numberArg longLongValue]);
    } else if (strcmp(numberType, @encode(double)) == 0) {
      sqlite3_bind_double(statement, (int) argIndex, [numberArg doubleValue]);
    } else {
      sqlite3_bind_text(statement, (int) argIndex, [[arg description] UTF8String], -1, SQLITE_TRANSIENT);
    }
  } else { // NSString
    NSString *stringArg;
    
    if ([arg isKindOfClass:[NSString class]]) {
      stringArg = (NSString *)arg;
    } else {
      stringArg = [arg description]; // convert to text
    }
    
#ifdef INCLUDE_SQL_BLOB_BINDING // TBD subjet to change:
    // If the string is a sqlblob URI then decode it and store the binary directly.
    //
    // A sqlblob URI is formatted similar to a data URI which makes it easy to convert:
    //   sqlblob:[<mime type>][;charset=<charset>][;base64],<encoded data>
    //
    // The reason the `sqlblob` prefix is used instead of `data` is because
    // applications may want to use data URI strings directly, so the
    // `sqlblob` prefix disambiguates the desired behavior.
    if ([stringArg hasPrefix:@"sqlblob:"]) {
      // convert to data URI, decode, store as blob
      stringArg = [stringArg stringByReplacingCharactersInRange:NSMakeRange(0,7) withString:@"data"];
      NSData *data = [NSData dataWithContentsOfURL: [NSURL URLWithString:stringArg]];
      sqlite3_bind_blob(statement, (int) argIndex, data.bytes, data.length, SQLITE_TRANSIENT);
    }
    else
#endif
    {
      NSData *data = [stringArg dataUsingEncoding:NSUTF8StringEncoding];
      sqlite3_bind_text(statement, (int) argIndex, data.bytes, (int) data.length, SQLITE_TRANSIENT);
    }
  }
}

-(void)dealloc
{
  int i;
  @synchronized (self) {
    NSArray *keys = [openDBs allKeys];
    NSDictionary *dbInfo;
    NSString *key;
    sqlite3 *db;
  
    /* close db the user forgot */
    for (i=0; i<[keys count]; i++) {
      key = [keys objectAtIndex:i];
      dbInfo = openDBs[key];
      db = [((NSValue *) dbInfo[@"dbPointer"]) pointerValue];
      sqlite3_close (db);
    }
  
#if !__has_feature(objc_arc)
    [openDBs release];
    [appDBPaths release];
    [super dealloc];
#endif
  }
}

+(NSDictionary *)captureSQLiteErrorFromDb:(struct sqlite3 *)db
{
  int code = sqlite3_errcode(db);
  int webSQLCode = [SQLite mapSQLiteErrorCode:code];
#if INCLUDE_SQLITE_ERROR_INFO
  int extendedCode = sqlite3_extended_errcode(db);
#endif
  const char *message = sqlite3_errmsg(db);
  
  NSMutableDictionary *error = [NSMutableDictionary dictionaryWithCapacity:4];
  
  [error setObject:[NSNumber numberWithInt:webSQLCode] forKey:@"code"];
  [error setObject:[NSString stringWithUTF8String:message] forKey:@"message"];
  
#if INCLUDE_SQLITE_ERROR_INFO
  [error setObject:[NSNumber numberWithInt:code] forKey:@"sqliteCode"];
  [error setObject:[NSNumber numberWithInt:extendedCode] forKey:@"sqliteExtendedCode"];
  [error setObject:[NSString stringWithUTF8String:message] forKey:@"sqliteMessage"];
#endif
  
  return error;
}

+(int)mapSQLiteErrorCode:(int)code
{
  // map the sqlite error code to
  // the websql error code
  switch(code) {
    case SQLITE_ERROR:
      return SYNTAX_ERR;
    case SQLITE_FULL:
      return QUOTA_ERR;
    case SQLITE_CONSTRAINT:
      return CONSTRAINT_ERR;
    default:
      return UNKNOWN_ERR;
  }
}

+(NSString*)getBlobAsBase64String:(const char*)blob_chars
                       withLength:(int)blob_length
{
  NSData* data = [NSData dataWithBytes:blob_chars length:blob_length];
  
  // Encode a string to Base64
  NSString* result = [data base64EncodedStringWithOptions:0];
  
#if !__has_feature(objc_arc)
  [result autorelease];
#endif
  
  return result;
}

+ (BOOL)requiresMainQueueSetup
{
  return YES;
}

@end /* vim: set expandtab : */
