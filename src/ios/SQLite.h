/*
 * SQLite.h
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

#import <React/RCTBridgeModule.h>

// Used to remove dependency on sqlite3.h in this header:
struct sqlite3;

enum WebSQLError {
    UNKNOWN_ERR = 0,
    DATABASE_ERR = 1,
    VERSION_ERR = 2,
    TOO_LARGE_ERR = 3,
    QUOTA_ERR = 4,
    SYNTAX_ERR = 5,
    CONSTRAINT_ERR = 6,
    TIMEOUT_ERR = 7
};
typedef int WebSQLError;

@interface SQLite : NSObject <RCTBridgeModule> {
    NSMutableDictionary *openDBs;
}

@property (nonatomic, copy) NSMutableDictionary *openDBs;
@property (nonatomic, copy) NSMutableDictionary *appDBPaths;

// Open / Close
-(void) open: (NSDictionary *) options success:(RCTResponseSenderBlock)success error:(RCTResponseSenderBlock)error;
-(void) close: (NSDictionary *) options success:(RCTResponseSenderBlock)success error:(RCTResponseSenderBlock)error;
-(void) attach: (NSDictionary *) options success:(RCTResponseSenderBlock)success error:(RCTResponseSenderBlock)error;
-(void) delete: (NSDictionary *) options success:(RCTResponseSenderBlock)success error:(RCTResponseSenderBlock)error;

// Batch processing interface
-(void) backgroundExecuteSqlBatch: (NSDictionary *) options success:(RCTResponseSenderBlock)success error:(RCTResponseSenderBlock)error;
-(void) executeSqlBatch: (NSDictionary *) options success:(RCTResponseSenderBlock)success error:(RCTResponseSenderBlock)error;

// Single requests interface
-(void) backgroundExecuteSql:(NSDictionary *) options success:(RCTResponseSenderBlock)success error:(RCTResponseSenderBlock)error;
-(void) executeSql:(NSDictionary *) options success:(RCTResponseSenderBlock)success error:(RCTResponseSenderBlock)error;

// Echo Test
-(void) echoStringValue:(NSDictionary *) options success:(RCTResponseSenderBlock)success error:(RCTResponseSenderBlock)error;
@end
