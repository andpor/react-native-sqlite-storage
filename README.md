# react-native-sqlite-storage
SQLite3 Native Plugin for React Native for both Android (Classic and Native) and iOS

Inspired by fantastic work done by Chris Brody I did not want to re-invent the wheel. The original Cordova plugin was written so well and adhered to latest WebSQL API that there was no need to come up with anything much different. So the Cordova plugin was ported to React Native.

Features:
  1. iOS and Android supported via identical JavaScript API.
  2. Android in pure Java and Native modes
  3. SQL transactions
  4. JavaScript interface via plain callbacks or Promises.
  5. Pre-populated SQLite database import from application bundle and sandbox

Please let me know your projects that use these SQLite React Native modules. I will list them in the reference section. If there are any features that you think would benefit this library please post them.

The library had been developed for React 14 using XCode 6. It has been tested with React 0.21.0 and XCode 7 - it works fine out of the box without any need for tweaks or code changes. For XCode 7 the only difference is that sqlite ios library name suffix is tbd instead of dylib.

#Version History
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
 3. Implent hooks for activity lifecycle and automatic db closing on destroy
 4. Fix How To Get Started instructions for Android

v2.1.1 - Fixes issues with XCode path and React Native version compatibility

v2.1 - Android support

v2.0 - Full support for Promise API. Backward compatible with Callbacks.

v1.0 - Intial release for iOS with full support of all operations based on plan JavaScript callbacks.

#How to use (iOS):

#### Step 1. NPM install

```shell
npm install --save react-native-sqlite-storage
```

#### Step 2. XCode SQLite project dependency set up

Drag the SQLite Xcode project as a dependency project into your React Native XCode project

![alt tag](https://raw.github.com/andpor/react-native-sqlite-storage/master/instructions/libs.png)

#### Step 3. XCode SQLite libraries dependency set up

Add libSQLite.a (from Workspace location) to the required Libraries and Frameworks. Also add sqlite3.0.tbd (XCode 7) or libsqlite3.0.dylib (XCode 6 and earlier) in the same fashion using Required Libraries view (Do not just add them manually as the build paths will not be properly set)

![alt tag](https://raw.github.com/andpor/react-native-sqlite-storage/master/instructions/addlibs.png)

#### Step 4. Application JavaScript require

Add var SQLite = require('react-native-sqlite-storage') to your index.ios.js

![alt tag](https://raw.github.com/andpor/react-native-sqlite-storage/master/instructions/require.png)

#### Step 5. Applicatin JavaScript code using the SQLite plugin

Add JS application code to use SQLite API in your index.ios.js etc. Here is some sample code. For full working example see test/index.ios.callback.js. Please note that Promise based API is now supported as well with full examples in the working React Native app under test/index.ios.promise.js

```javascript
errorCB(err) {
  console.log("SQL Error: " + err);
},

successCB() {
  console.log("SQL executed fine");
},

openCB() {
  console.log("Database OPENED");
},

var db = SQLite.openDatabase("test.db", "1.0", "Test Database", 200000, openCB, errorCB);
db.transaction((tx) => {
  tx.executeSql('SELECT * FROM Employees a, Departments b WHERE a.department = b.department_id', [], (tx, results) => {
      console.log("Query completed");
      
      // Get rows with Web SQL Database spec compliance.
      
      var len = results.rows.length;
      for (let i = 0; i < len; i++) {
        let row = results.rows.item(i);
        console.log(`Employee name: ${row.name}, Dept Name: ${row.deptName}`);
      }

      // Alternatively, you can use the non-standard raw method.
      
      /*
        let rows = results.rows.raw(); // shallow copy of rows Array

        rows.map(row => console.log(`Employee name: ${row.name}, Dept Name: ${row.deptName}`));
      */
    });
});
```

#How to use (Android):

#### Step 1 - NPM Install

```shell
npm install --save react-native-sqlite-storage
```
#### Step 2 - Update Gradle Settings

```gradle
// file: android/settings.gradle
...

include ':react-native-sqlite-storage'
project(':react-native-sqlite-storage').projectDir = new File(rootProject.projectDir, '../node_modules/react-native-sqlite-storage/src/android')
```

#### Step 3 - Update app Gradle Build

```gradle
// file: android/app/build.gradle
...

dependencies {
    ...
    compile project(':react-native-sqlite-storage')
}
```

#### Step 4 - Register React Package (this should work on React version but if it does not , try the ReactActivity based approach

```java
...
import org.pgsqlite.SQLitePluginPackage;

public class MainActivity extends Activity implements DefaultHardwareBackBtnHandler {

    private ReactInstanceManager mReactInstanceManager;
    private ReactRootView mReactRootView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mReactRootView = new ReactRootView(this);
        mReactInstanceManager = ReactInstanceManager.builder()
                .setApplication(getApplication())
                .setBundleAssetName("index.android.bundle")  // this is dependant on how you name you JS files, example assumes index.android.js
                .setJSMainModuleName("index.android")        // this is dependant on how you name you JS files, example assumes index.android.js
                .addPackage(new MainReactPackage())
                .addPackage(new SQLitePluginPackage(this))   // register SQLite Plugin here
                .setUseDeveloperSupport(BuildConfig.DEBUG)
                .setInitialLifecycleState(LifecycleState.RESUMED)
                .build();
        mReactRootView.startReactApplication(mReactInstanceManager, "AwesomeProject", null); //change "AwesomeProject" to name of your app
        setContentView(mReactRootView);
    }
...

```

Alternative approach on newer versions of React Native (0.18+):

```java
import org.pgsqlite.SQLitePluginPackage;

public class MainActivity extends ReactActivity {
  ......
  
  /**
   * A list of packages used by the app. If the app uses additional views
   * or modules besides the default ones, add more packages here.
   */
    @Override
    protected List<ReactPackage> getPackages() {
      return Arrays.<ReactPackage>asList(
        new SQLitePluginPackage(this))   // register SQLite Plugin here
        new MainReactPackage());
    }
}
```

#### Step 5 - Require and use in Javascript - see full examples (callbacks and Promise) in test directory.

```js
// file: index.android.js

var React = require('react-native');
var SQLite = require('react-native-sqlite-storage')
...
```

## Setting up your project to import a pre-populated SQLite database from application for iOS

#### Step 1 - Create 'www' folder.

Create a folder called 'www' (yes must be called precisely that else things won't work) in the project folder via Finder

#### Step 2 - Create the database file

Copy/paste your pre-populated database file into the 'www' folder. Give it the same name you are going to use in openDatabase call in your application

#### Step 3 - Add file to project

in XCode, right click on the main folder and select Add Files to 'your project name'

![alt tag](https://raw.github.com/andpor/react-native-sqlite-storage/master/instructions/addFilesToProject.png)

#### Step 4 - Choose files to add

In the Add Files dialog, navigate to the 'www' directory you created in Step 1, select it, make sure you check the option to Create Folder Reference

![alt tag](https://raw.github.com/andpor/react-native-sqlite-storage/master/instructions/addFilesToProjectSelect.png)

#### Step 5 - Verify project structure

Ensure your project structure after previous steps are executed looks like this

![alt tag](https://raw.github.com/andpor/react-native-sqlite-storage/master/instructions/projectStructureAfter.png)

### Step 6 - Adjust openDatabase call

Modify you openDatabase call in your application adding createFromLocation param. If you named your database file in step 2 'testDB' the openDatabase call should look like something like this:
```js

  ...
  1.SQLite.openDatabase({name : "testDB", createFromLocation : 1}, okCallback,errorCallback); 
  // default - if your folder is called www and data file is named the same as the dbName - testDB in this example
  2.SQLite.openDatabase({name : "testDB", createFromLocation : "~data/mydbfile.sqlite"}, okCallback,errorCallback); 
  // if your folder is called data rather than www or your filename does not match the name of the db
  3.SQLite.openDatabase({name : "testDB", createFromLocation : "/data/mydbfile.sqlite"}, okCallback,errorCallback); 
  // if your folder is not in app bundle but in app sanbox i.e. downloaded from some remote location.
  ...

```
For Android, the www directory is always relative to the assets directory for the app: src/main/assets

Enjoy!

#Original Cordova SQLite Bindings from Chris Brody
https://github.com/litehelpers/Cordova-sqlite-storage

The issues and limitations for the actual SQLite can be found on this site.

##Issues

1. Android binds all numeric SQL input values to double. This is due to the underlying React Native limitation where only a Numeric type is available on the interface point making it ambiguous to distinguish intgeres from doubles. Once I figure out the proper way to do this I will update the codebase [(Issue #4141)] (https://github.com/facebook/react-native/issues/4141).

