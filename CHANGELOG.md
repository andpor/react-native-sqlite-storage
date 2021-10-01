# Version History

v6.0.1
 1. Add missing error function to plugin.

v6.0.0
 1. Added RNW>=0.62 WinRT CPP implementation thanks to tsytsarkin (https://github.com/andpor/react-native-sqlite-storage/pull/461)
 2. Fix xcode 12 compatibility (https://github.com/andpor/react-native-sqlite-storage/pull/447)
 3. Add warn method to the plugin (https://github.com/andpor/react-native-sqlite-storage/issues/402)

v5.0.0
 1. Change RN to Google Maven (https://github.com/andpor/react-native-sqlite-storage/pull/405)
 2. Native Android with JSON1 support (https://github.com/andpor/react-native-sqlite-storage/pull/392)
 3. Resolve errors linking library via CocoaPods in iOS (https://github.com/andpor/react-native-sqlite-storage/pull/396)
 4. Documentation enhancements for Android native (https://github.com/andpor/react-native-sqlite-storage/pull/393)
 5. Documentation enhancements (https://github.com/andpor/react-native-sqlite-storage/pull/385)
 
v4.1.0
 1. New native SQLite libraries for Android. Dropping support for armeabi.
 
v4.0.0
 1. RN 0.60 compatibility (https://github.com/andpor/react-native-sqlite-storage/pull/361)
 
v3.3.10
 1. Fix package org.pgsqlite not found error for RN 0.58.4 (https://github.com/andpor/react-native-sqlite-storage/pull/324)
 
v3.3.9
 1. Gradle upgrade to 3.1.4 and addition of google repo (https://github.com/andpor/react-native-sqlite-storage/issues/294)
 
v3.3.8
 1. UWP implementation for SqliteStorage (https://github.com/andpor/react-native-sqlite-storage/pull/302)
 2. Fix the window is not defined (https://github.com/andpor/react-native-sqlite-storage/pull/295)
 
v3.3.7
 1. Remove rnpm-postlink (#292)
 2. Use correct parameters for SQLite.openDatabase (#291)
 3. Pulling SDK versions from root project (#287) - Android only
 
v3.3.6
 1. Fix INTEGER column value overflow.
 
v3.3.5
 1. All JSON conversions in Android modules have been eliminated in favor of direct React Native interfaces. This should provide siginificant performance improvement.
 2. Main Queue Warning issue fixed in iOS.
 3. Exception handling in logic processing asset imports have been fixed and error codes are properly propagated to JS callabcks.
 4. Examples have been revamped and library was tested with XCode 9 and Android Studio 3.1.1 (Gradle 2.2)
 
v3.3.4
 1. New version of native binaries compile with latest sqlite version 3.20.1 supporting pcre extension to enable REGEXP function for Android. (https://github.com/andpor/react-native-sqlite-storage/pull/205)
 2. Fixes Xcode warning for potentially insecure string. (https://github.com/andpor/react-native-sqlite-storage/pull/199)
 3. Remove createJSModules @ovveride marker - RN 0.47 compatibility. (https://github.com/andpor/react-native-sqlite-storage/pull/188)
 4. Podfile add macOS support (https://github.com/andpor/react-native-sqlite-storage/pull/179)
 5. instructions directory added on npmignore. (https://github.com/andpor/react-native-sqlite-storage/pull/174)
 
v3.3.3
 1. Fix for the db location string [Issue #172] (https://github.com/andpor/react-native-sqlite-storage/issues/172)
 2. #define in iOS casebase for SQLCIPHER. If you include this #define in your main project settings, this library will pick up appropriate key handling code automatically.
 
v3.3.2
 1. Yoga import fix
 
v3.3.1
 1. Comment of SQLCipher code in iOS implementation as a quick fix. [Issue #155] (https://github.com/andpor/react-native-sqlite-storage/issues/155)
 
v3.3.0 (Extended thanks to dryganets for his significant contributions to this release)
 1. Access to MutableDictonary openDBs properly synchronized [PR #130] (https://github.com/andpor/react-native-sqlite-storage/pull/130)
 2. Database attach flow fixed. Threading model fix in order to have all queries executed in the same order [PR #131] (https://github.com/andpor/react-native-sqlite-storage/pull/131)
 3. All statements and queries are closed in finally statements in order to fix SQLiteCipher corner case crashes [PR #132] (https://github.com/andpor/react-native-sqlite-storage/pull/132)
 4. Minor style fix	in index.ios.callback.js and index.ios.promise.js [PR #136] (https://github.com/andpor/react-native-sqlite-storage/pull/136)
 5. Fix determination logic for opened db [PR #139] (https://github.com/andpor/react-native-sqlite-storage/pull/139)
 6. Clean up in lib/sqlite.core.js [PR #138] (https://github.com/andpor/react-native-sqlite-storage/pull/138)
 7. Production grade logging for the Android plugin [PR #137] (https://github.com/andpor/react-native-sqlite-storage/pull/137)
 8. Remove pre-honeycomb workaround code in Android that was causing issues in SQL Cipher [PR #147] (https://github.com/andpor/react-native-sqlite-storage/pull/147)
 9. Fix broken Markdown headings [PR #153] (https://github.com/andpor/react-native-sqlite-storage/pull/153)
 10. Drop usage of the dead rnpm repository [PR #148] (https://github.com/andpor/react-native-sqlite-storage/pull/148)

v3.2.2
 1. Corrects the CocoaPods based development set-up instructions and includes sample Podfile. [Issue #125] (https://github.com/andpor/react-native-sqlite-storage/issues/125)
 
v3.2.1
 1. Sample apps in test directory adjusted for React Native 0.41 and plugability in AwesomeProject. [Issue #120] (https://github.com/andpor/react-native-sqlite-storage/issues/120)
 
v3.2.0 
 1. This is a backward incompatible release with baseline React Native 0.40 support.
 2. [React Native 0.40 compatibility fixes] (https://github.com/andpor/react-native-sqlite-storage/pull/110) - thanks K-Leon for this contribution
 
v3.1.3
 1. Add support for ATTACH (thanks to itinance for this contribution)
 2. Example applications are now hosted in separate repo [react-native-sqlite-examples] (https://github.com/andpor/react-native-sqlite-storage-examples)


v3.1.2
 1. Add support for CocoaPods (thanks to JAStanton for this contribution)
 2. Set base iOS build to 8.0

v3.1.1
 1.  Fix for Cordova issue #517: reject ALTER, REINDEX and REPLACE operations in readTransactions
 2.  Stop remaining transaction callback in case of an error with no error handler returning false

v3.1.0
 1. Backward incompatible change. Boolean params will now be converted and stored as int type, 0 and 1, in compliance with SQLite specifications. Issue [#63] (https://github.com/andpor/react-native-sqlite-storage/issues/63)
 2. Database decoupled from the Activity lifecycle on Android. With this change, the database will not be closed without explicit instructions to close it. SQLitePluginPackage constructor change. Pull Request [#62] (https://github.com/andpor/react-native-sqlite-storage/pull/62)
 3. Correct handling for executeSql with object as sql value (solves a possible crash on iOS)
 4. Backfill cordova-sqlite-storage fix - readTransaction allows modification in case of extra semicolon before SQL. Issue [#460] (https://github.com/litehelpers/Cordova-sqlite-storage/issues/460)

v3.0.0
 1. Default location changes for iOS for App Store iCloud compliance - backward incompatible release. Default now is no-sync location instead of docs.
 2. Ability to point to read-only db file in app bundle directly without requiring it to be copied elsewhere.
 2. Check if db is open before throwing an exception (triggered in android lock workaround)
 3. Fix for issue #57. Can't find variable: Blob

v2.1.6
 1. rnpm linking for iOS - contributed by @clozr
 2. Backfill Cordova read transaction bug fix.

v2.1.5
 1. Allow retrieval of pre-populated db files from user defined locations in application bundle as well as the sandbox.
 2. Implement Activity lifecycle mgmt in Android native
 3. Fix issue [#37] (https://github.com/andpor/react-native-sqlite-storage/issues/37) - Int Column type value overflow
 4. Fix issue [#38] (https://github.com/andpor/react-native-sqlite-storage/issues/38) - Transactions not aborted with Promise runtime
 5. Backfill fixes from Cordova SQLite Storage
    - add sqlBatch to facilitate batch exec of simple SQL statements (Android + iOS)
    - add echoTest for plugin integrity test

v2.1.4 - tested with React 0.21.0
 1. Expose a bulk data retrieval interface from JS
 2. Fix JS 'strict' mode execution errors

v2.1.3
 1. Fix the runtime error in reflection.

v2.1.2
 1. Android Native SQLite connectivity
 2. Change how React parameters are processed to map a Number to Java Double
 3. Implement hooks for activity lifecycle and automatic db closing on destroy
 4. Fix How To Get Started instructions for Android

v2.1.1 - Fixes issues with XCode path and React Native version compatibility

v2.1 - Android support

v2.0 - Full support for Promise API. Backward compatible with Callbacks.

v1.0 - Initial release for iOS with full support of all operations based on plan JavaScript callbacks.
