# What?

This is an example application for sqlite3-usage with a prepoluated database

# Installation

Change directory into this example-project

```
npm install
react-native link
```

and then run the application. In case of success a record with a value called "Test" will be read from the database that
was created by prepopulation.

# Good to know

The origin database file, which should be used for prepopulation, exists twice:

iOS: ./www/example.db
Android: ./android/app/src/main/assets/example.db
