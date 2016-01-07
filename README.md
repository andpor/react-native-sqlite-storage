# react-native-sqlite-storage
SQLite3 Native Plugin for React Native for both Android (Classic and Native) and iOS

Inspired by fantastic work done by Chris Brody I did not want to re-invent the wheel. The original Cordova plugin was written so well and adhered to latest WebSQL API that there was no need to come up with anything much different. So the Cordova plugin was ported to React Native.

Features:
  1. iOS and Android supported via identical JavaScript API.
  2. Android in pure Java and Native modes
  3. SQL transactions 
  4. JavaScript interface via plain callbacks or Promises.
  5. Pre-populated SQLite database import from application sandbox

Please let me know your projects that use these SQLite React Native modules. I will list them in the reference section. If there are any features that you think would benefit this library please post them.

#Version History

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

#### Step 3. XCode SQLite library dependency set up

Add libSQLite.a (from Workspace location) to the required Libraries and Frameworks.

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
      var len = results.rows.length;
      for (let i = 0; i < len; i++) {
        let row = results.rows.item(i);
        console.log(`Employee name: ${row.name}, Dept Name: ${row.deptName}`);
      }
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

#### Step 4 - Register React Package

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
                .setBundleAssetName("index.android.bundle")
                .setJSMainModuleName("index.android")
                .addPackage(new MainReactPackage())
                .addPackage(new SQLitePluginPackage(this)) // register SQLite Plugin here
                .setUseDeveloperSupport(BuildConfig.DEBUG)
                .setInitialLifecycleState(LifecycleState.RESUMED)
                .build();
        mReactRootView.startReactApplication(mReactInstanceManager, "AwesomeProject", null);
        setContentView(mReactRootView);
    }
...

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

Create a folder called 'www' (yes must be called precisely that else things won't work) in the app folder via Finder

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
  SQLite.openDatabase({name : "testDB", createFromLocation : 1}, okCallback,errorCallback);
  ...

```

Enjoy!

#Original Cordova SQLite Bindings from Chris Brody
https://github.com/litehelpers/Cordova-sqlite-storage

The issues and limitations for the actual SQLite can be found on this site.

##Issues

1. Android binds all numeric SQL input values to double. This is due to the underlying React Native limitation where only a Numeric type is available on the interface point making it ambiguous to distinguish intgeres from doubles. Once I figure out the proper way to do this I will update the codebase [(Issue #4141)] (https://github.com/facebook/react-native/issues/4141).
3. Automatic close for the database when main activity is destroyed is implemented in the pure Java Android version which is most likely to be obsoleted in the near future leaving native impl only.
