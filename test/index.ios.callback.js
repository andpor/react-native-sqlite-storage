/**
 * sqlite.ios.callback.js
 *
 * Created by Andrzej Porebski on 10/29/15.
 * Copyright (c) 2015 Andrzej Porebski.
 *
 * Test App using JS Callbacks for react-naive-sqlite-storage
 *
 * This library is available under the terms of the MIT License (2008).
 * See http://opensource.org/licenses/alphabetical for full text.
 */
'use strict';

import React, { Component } from 'react';
import {
  AppRegistry,
  StyleSheet,
  Text,
  View,
  ListView
} from 'react-native';


import SQLite from 'react-native-sqlite-storage';
SQLite.DEBUG(true);
SQLite.enablePromise(false);

const database_name = "Test.db";
const database_version = "1.0";
const database_displayname = "SQLite Test Database";
const database_size = 200000;
let db;

class SQLiteDemo extends Component {
  constructor() {
    super();
    this.progress = [];
    this.state = {
      progress: [],
      ds: new ListView.DataSource({
        rowHasChanged: (r1, r2) => r1 !== r2}
      )
    };
  }

  updateProgress = (text, resetState) => {
    let progress = [];
    if (!resetState) {
      progress = [...this.progress];
    }
    progress.push(text);
    this.progress = progress;
    this.setState({
      progress
    });
  }

  componentWillUnmount = () => {
    this.closeDatabase();
  }

  errorCB = (err) => {
    console.log("error: ",err);
    this.updateProgress("Error: "+ (err.message || err));
    return false;
  }

  successCB = () => {
    console.log("SQL executed ...");
  }

  openCB = () => {
    this.updateProgress("Database OPEN");
  }

  closeCB = () => {
    this.updateProgress("Database CLOSED");
  }

  deleteCB = () => {
    console.log("Database DELETED");
    this.updateProgress("Database DELETED");
  }

  populateDatabase = (db) => {
    this.updateProgress("Database integrity check");
    db.executeSql('SELECT 1 FROM Version LIMIT 1', [],
      () => {
        this.updateProgress("Database is ready ... executing query ...");
        db.transaction(this.queryEmployees,this.errorCB,() => {
          this.updateProgress("Processing completed");
        });
      },
      (error) => {
        console.log("received version error:", error);
        this.updateProgress("Database not yet ready ... populating data");
        db.transaction(this.populateDB, this.errorCB, () => {
          this.updateProgress("Database populated ... executing query ...");
          db.transaction(this.queryEmployees,this.errorCB, () => {
            console.log("Transaction is now finished");
            this.updateProgress("Processing completed");
            this.closeDatabase();
          });
        });
      });
  }

  populateDB = (tx) => {
    this.updateProgress("Executing DROP stmts");

    tx.executeSql('DROP TABLE IF EXISTS Employees;');
    tx.executeSql('DROP TABLE IF EXISTS Offices;');
    tx.executeSql('DROP TABLE IF EXISTS Departments;');

    this.updateProgress("Executing CREATE stmts");

    tx.executeSql('CREATE TABLE IF NOT EXISTS Version( '
      + 'version_id INTEGER PRIMARY KEY NOT NULL); ', [], this.successCB, this.errorCB);

    tx.executeSql('CREATE TABLE IF NOT EXISTS Departments( '
      + 'department_id INTEGER PRIMARY KEY NOT NULL, '
      + 'name VARCHAR(30) ); ', [], this.successCB, this.errorCB);

    tx.executeSql('CREATE TABLE IF NOT EXISTS Offices( '
      + 'office_id INTEGER PRIMARY KEY NOT NULL, '
      + 'name VARCHAR(20), '
      + 'longtitude FLOAT, '
      + 'latitude FLOAT ) ; ', [], this.successCB, this.errorCB);

    tx.executeSql('CREATE TABLE IF NOT EXISTS Employees( '
      + 'employe_id INTEGER PRIMARY KEY NOT NULL, '
      + 'name VARCHAR(55), '
      + 'office INTEGER, '
      + 'department INTEGER, '
      + 'FOREIGN KEY ( office ) REFERENCES Offices ( office_id ) '
      + 'FOREIGN KEY ( department ) REFERENCES Departments ( department_id ));', []);

    this.updateProgress("Executing INSERT stmts");

    tx.executeSql('INSERT INTO Departments (name) VALUES ("Client Services");', []);
    tx.executeSql('INSERT INTO Departments (name) VALUES ("Investor Services");', []);
    tx.executeSql('INSERT INTO Departments (name) VALUES ("Shipping");', []);
    tx.executeSql('INSERT INTO Departments (name) VALUES ("Direct Sales");', []);

    tx.executeSql('INSERT INTO Offices (name, longtitude, latitude) VALUES ("Denver", 59.8,  34.);', []);
    tx.executeSql('INSERT INTO Offices (name, longtitude, latitude) VALUES ("Warsaw", 15.7, 54.);', []);
    tx.executeSql('INSERT INTO Offices (name, longtitude, latitude) VALUES ("Berlin", 35.3, 12.);', []);
    tx.executeSql('INSERT INTO Offices (name, longtitude, latitude) VALUES ("Paris", 10.7, 14.);', []);

    tx.executeSql('INSERT INTO Employees (name, office, department) VALUES ("Sylvester Stallone", 2,  4);', []);
    tx.executeSql('INSERT INTO Employees (name, office, department) VALUES ("Elvis Presley", 2, 4);', []);
    tx.executeSql('INSERT INTO Employees (name, office, department) VALUES ("Leslie Nelson", 3,  4);', []);
    tx.executeSql('INSERT INTO Employees (name, office, department) VALUES ("Fidel Castro", 3, 3);', []);
    tx.executeSql('INSERT INTO Employees (name, office, department) VALUES ("Bill Clinton", 1, 3);', []);
    tx.executeSql('INSERT INTO Employees (name, office, department) VALUES ("Margaret Thatcher", 1, 3);', []);
    tx.executeSql('INSERT INTO Employees (name, office, department) VALUES ("Donald Trump", 1, 3);', []);
    tx.executeSql('INSERT INTO Employees (name, office, department) VALUES ("Dr DRE", 2, 2);', []);
    tx.executeSql('INSERT INTO Employees (name, office, department) VALUES ("Samantha Fox", 2, 1);', []);
    console.log("all config SQL done");
  }

  queryEmployees = (tx) => {
    console.log("Executing employee query...");

    tx.executeSql('SELECT a.name, b.name as deptName FROM Employees a, Departments b WHERE a.department = b.department_id and a.department=?', [3],
      this.queryEmployeesSuccess,this.errorCB);
    //tx.executeSql('SELECT a.name, from TEST', [],() => {},this.errorCB);
  }

  queryEmployeesSuccess = (tx,results) => {
    this.updateProgress("Query completed");
    var len = results.rows.length;
    for (let i = 0; i < len; i++) {
      let row = results.rows.item(i);
      this.updateProgress(`Empl Name: ${row.name}, Dept Name: ${row.deptName}`);
    }
  }

  loadAndQueryDB = () => {
    this.updateProgress("Opening database ...",true);
    db = SQLite.openDatabase(database_name, database_version, database_displayname, database_size, this.openCB, this.errorCB);
    this.populateDatabase(db);
  }

  deleteDatabase = () => {
    this.updateProgress("Deleting database");
    SQLite.deleteDatabase(database_name, this.deleteCB, this.errorCB);
  }

  closeDatabase = () => {
    if (db) {
      console.log("Closing database ...");
      this.updateProgress("Closing database");
      db.close(this.closeCB,this.errorCB);
    } else {
      this.updateProgress("Database was not OPENED");
    }
  }

  runDemo = () => {
    this.updateProgress("Starting SQLite Callback Demo",true);
    this.loadAndQueryDB();
  }

  renderProgressEntry = (entry) => {
    return (<View style={listStyles.li}>
      <View>
        <Text style={listStyles.liText}>{entry}</Text>
      </View>
    </View>)
  }

  render = () => {
    return (<View style={styles.mainContainer}>
      <View style={styles.toolbar}>
        <Text style={styles.toolbarButton} onPress={this.runDemo}>
          Run Demo
        </Text>
        <Text style={styles.toolbarButton} onPress={this.closeDatabase}>
          Close DB
        </Text>
        <Text style={styles.toolbarButton} onPress={this.deleteDatabase}>
          Delete DB
        </Text>
      </View>
      <ListView
        enableEmptySections={true}
        dataSource={this.state.ds.cloneWithRows(this.state.progress)}
        renderRow={this.renderProgressEntry}
        style={listStyles.liContainer}/>
    </View>);
  }
}

var listStyles = StyleSheet.create({
  li: {
    borderBottomColor: '#c8c7cc',
    borderBottomWidth: 0.5,
    paddingTop: 15,
    paddingRight: 15,
    paddingBottom: 15,
  },
  liContainer: {
    backgroundColor: '#fff',
    flex: 1,
    paddingLeft: 15,
  },
  liIndent: {
    flex: 1,
  },
  liText: {
    color: '#333',
    fontSize: 17,
    fontWeight: '400',
    marginBottom: -3.5,
    marginTop: -3.5,
  },
});

var styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F5FCFF',
  },
  welcome: {
    fontSize: 20,
    textAlign: 'center',
    margin: 10,
  },
  instructions: {
    textAlign: 'center',
    color: '#333333',
    marginBottom: 5,
  },
  toolbar: {
    backgroundColor: '#51c04d',
    paddingTop: 30,
    paddingBottom: 10,
    flexDirection: 'row'
  },
  toolbarButton: {
    color: 'blue',
    textAlign: 'center',
    flex: 1
  },
  mainContainer: {
    flex: 1
  }
});

AppRegistry.registerComponent('AwesomeProject', () => SQLiteDemo);
