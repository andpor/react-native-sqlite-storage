/*
 * SQLiteResult.m
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
#import "SQLiteResult.h"

@interface SQLiteResult ()

- (SQLiteResult *)initWithStatus:(SQLiteStatus)statusOrdinal message:(id)theMessage;

@end

@implementation SQLiteResult
@synthesize status, message;

- (SQLiteResult*)init
{
  return [self initWithStatus:SQLiteStatus_NO_RESULT message:nil];
}

- (SQLiteResult*)initWithStatus:(SQLiteStatus)statusOrdinal message:(id)theMessage
{
  self = [super init];
  if (self) {
    status = [NSNumber numberWithInt:statusOrdinal];
    message = theMessage;
  }
  return self;
}

+ (SQLiteResult*)resultWithStatus:(SQLiteStatus)statusOrdinal messageAsString:(NSString*)theMessage
{
  return [[self alloc] initWithStatus:statusOrdinal message:theMessage];
}

+ (SQLiteResult*)resultWithStatus:(SQLiteStatus)statusOrdinal messageAsArray:(NSArray*)theMessage
{
  return [[self alloc] initWithStatus:statusOrdinal message:theMessage];
}

+ (SQLiteResult*)resultWithStatus:(SQLiteStatus)statusOrdinal messageAsDictionary:(NSDictionary*)theMessage
{
  return [[self alloc] initWithStatus:statusOrdinal message:theMessage];
}



@end