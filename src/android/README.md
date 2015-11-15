# react-native-date
React Native date and time pickers for Android

## Installation and How to use

#### Step 1 - NPM Install 

```shell
npm install --save react-native-sqlite-storage
```
#### Step 2 - Update Gradle Settings

```gradle
// file: android/settings.gradle
...

include ':react-native-sqlite-storage', ':app'
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
import org.pgsqlite.SQLitePlugin
import android.support.v4.app.FragmentActivity;

public class MainActivity extends FragmentActivity implements DefaultHardwareBackBtnHandler { // ! extends from FragmentActivity

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
                .addPackage(new SQLitePlugin(this)) // register react date package here
                .setUseDeveloperSupport(BuildConfig.DEBUG)
                .setInitialLifecycleState(LifecycleState.RESUMED)
                .build();
        mReactRootView.startReactApplication(mReactInstanceManager, "AwesomeProject", null);
        setContentView(mReactRootView);
    }
...

```

#### Step 5 - Require and use in Javascript - see full exapmples in test directory.

```js
// file: index.android.js

var React = require('react-native');
var SQLite = require('react-native-sqlite-storage')
...
```


## Notes
- Please report any issues or send patches to get fixes in

## Issues
- React Native creates a limitation around passing in doubles to sqlite calls. All numeric values will be for now interpreted as int
- Long SQLite types will be bound to int

