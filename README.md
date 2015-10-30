# react-native-sqlite-storage
SQLite Native Plugin for React Native

Inspired by fantastic work done by Chris Brody I did not want to re-invent the wheel. The original Cordova plugin was written so well and adhered to latest WebSQL API that there was no need to come up with anything much different. So the Cordova plugin was ported to React Native.

This is iOS binding only for now. Initial release - fully working. Tested so far with Simulators.

How to use:

1. npm install --save react-native-sqlite-storage
2. Drag the SQLite Xcode project as a dependency project into your React Native XCode project
3. Add libSQL.a (from Workspace location) to the required Libraries and Frameworks.
4. Add var SQLite = require('react-native-sqlite-storage') to your index.ios.js
5. Add JS application code to use SQLite API in your index.ios.js etc.

Enjoy!
