/*
 * sqlite.ios.promise.js
 *
 * Created by Andrzej Porebski on 10/29/15.
 * Copyright (c) 2015 Andrzej Porebski.
 *
 * Test App using Promise for react-naive-sqlite-storage
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
SQLite.enablePromise(true);


const database_name = "Test.db";
const database_version = "1.0";
const database_displayname = "SQLite Test Database";
const database_size = 200000;
let db;

const SQLiteDemo = React.createClass({
    getInitialState(){
        return {
            progress: [],
            dataSource: new ListView.DataSource({
                rowHasChanged: (row1, row2) => { row1 !== row2; },
            })
        };
    },

    componentWillUnmount(){
        this.closeDatabase();
    },

    errorCB(err) {
        console.log("error: ",err);
        this.state.progress.push("Error " + (err.message || err));
        this.setState(this.state);
    },

    populateDatabase(db){
        var that = this;
        that.state.progress.push("Database integrity check");
        that.setState(that.state);
        db.executeSql('SELECT 1 FROM Version LIMIT 1').then(() =>{
            that.state.progress.push("Database is ready ... executing query ...");
            that.setState(that.state);
            db.transaction(that.queryEmployees).then(() => {
                that.state.progress.push("Processing completed");
                that.setState(that.state);
            });
        }).catch((error) =>{
            console.log("Received error: ", error)
            that.state.progress.push("Database not yet ready ... populating data");
            that.setState(that.state);
            db.transaction(that.populateDB).then(() =>{
                that.state.progress.push("Database populated ... executing query ...");
                that.setState(that.state);
                db.transaction(that.queryEmployees).then((result) => { 
                    console.log("Transaction is now finished"); 
                    that.state.progress.push("Processing completed");
                    that.setState(that.state);
                    that.closeDatabase()});
            });
        });
    },

    populateDB(tx) {
        var that = this;
        this.state.progress.push("Executing DROP stmts");
        this.setState(this.state);

        tx.executeSql('DROP TABLE IF EXISTS Employees;');
        tx.executeSql('DROP TABLE IF EXISTS Offices;');
        tx.executeSql('DROP TABLE IF EXISTS Departments;');

        this.state.progress.push("Executing CREATE stmts");
        this.setState(this.state);

        tx.executeSql('CREATE TABLE IF NOT EXISTS Version( '
            + 'version_id INTEGER PRIMARY KEY NOT NULL); ').catch((error) => {  
            that.errorCB(error) 
        });

        tx.executeSql('CREATE TABLE IF NOT EXISTS Departments( '
            + 'department_id INTEGER PRIMARY KEY NOT NULL, '
            + 'name VARCHAR(30) ); ').catch((error) => {  
            that.errorCB(error) 
        });

        tx.executeSql('CREATE TABLE IF NOT EXISTS Offices( '
            + 'office_id INTEGER PRIMARY KEY NOT NULL, '
            + 'name VARCHAR(20), '
            + 'longtitude FLOAT, '
            + 'latitude FLOAT ) ; ').catch((error) => {  
            that.errorCB(error) 
        });

        tx.executeSql('CREATE TABLE IF NOT EXISTS Employees( '
            + 'employe_id INTEGER PRIMARY KEY NOT NULL, '
            + 'name VARCHAR(55), '
            + 'office INTEGER, '
            + 'department INTEGER, '
            + 'FOREIGN KEY ( office ) REFERENCES Offices ( office_id ) '
            + 'FOREIGN KEY ( department ) REFERENCES Departments ( department_id ));').catch((error) => {  
            that.errorCB(error) 
        });

        this.state.progress.push("Executing INSERT stmts");
        this.setState(this.state);


        tx.executeSql('INSERT INTO Departments (name) VALUES ("Client Services");');
        tx.executeSql('INSERT INTO Departments (name) VALUES ("Investor Services");');
        tx.executeSql('INSERT INTO Departments (name) VALUES ("Shipping");');
        tx.executeSql('INSERT INTO Departments (name) VALUES ("Direct Sales");');

        tx.executeSql('INSERT INTO Offices (name, longtitude, latitude) VALUES ("Denver", 59.8,  34.1);');
        tx.executeSql('INSERT INTO Offices (name, longtitude, latitude) VALUES ("Warsaw", 15.7, 54.1);');
        tx.executeSql('INSERT INTO Offices (name, longtitude, latitude) VALUES ("Berlin", 35.3, 12.1);');
        tx.executeSql('INSERT INTO Offices (name, longtitude, latitude) VALUES ("Paris", 10.7, 14.1);');

        tx.executeSql('INSERT INTO Employees (name, office, department) VALUES ("Sylvester Stallone", 2,  4);');
        tx.executeSql('INSERT INTO Employees (name, office, department) VALUES ("Elvis Presley", 2, 4);');
        tx.executeSql('INSERT INTO Employees (name, office, department) VALUES ("Leslie Nelson", 3,  4);');
        tx.executeSql('INSERT INTO Employees (name, office, department) VALUES ("Fidel Castro", 3, 3);');
        tx.executeSql('INSERT INTO Employees (name, office, department) VALUES ("Bill Clinton", 1, 3);');
        tx.executeSql('INSERT INTO Employees (name, office, department) VALUES ("Margaret Thatcher", 1, 3);');
        tx.executeSql('INSERT INTO Employees (name, office, department) VALUES ("Donald Trump", 1, 3);');
        tx.executeSql('INSERT INTO Employees (name, office, department) VALUES ("Dr DRE", 2, 2);');
        tx.executeSql('INSERT INTO Employees (name, office, department) VALUES ("Samantha Fox", 2, 1);');
        console.log("all config SQL done");
    },

    queryEmployees(tx) {
        var that = this;
        console.log("Executing employee query");
        tx.executeSql('SELECT a.name, b.name as deptName FROM Employees a, Departments b WHERE a.department = b.department_id').then(([tx,results]) => {
            that.state.progress.push("Query completed");
            that.setState(that.state);
            var len = results.rows.length;
            for (let i = 0; i < len; i++) {
                let row = results.rows.item(i);
                that.state.progress.push(`Empl Name: ${row.name}, Dept Name: ${row.deptName}`);
            }
            that.setState(that.state);
        }).catch((error) => { 
            console.log(error);
        });
    },

    loadAndQueryDB(){
        var that = this;
        that.state.progress.push("Plugin integrity check ...");
        that.setState(that.state);
        SQLite.echoTest().then(() => {
            that.state.progress.push("Integrity check passed ...");
            that.setState(that.state);
            that.state.progress.push("Opening database ...");
            that.setState(that.state);
            SQLite.openDatabase({name : "test5.db", createFromLocation : "~/db/andrew.db"}).then((DB) => {
                db = DB;
                that.state.progress.push("Database OPEN");
                that.setState(that.state);
                that.populateDatabase(DB);
            }).catch((error) => {
                console.log(error);
            });
        }).catch(error => {
            that.state.progress.push("echoTest failed - plugin not functional");
            that.setState(that.state);
        });
    },

    closeDatabase(){
        var that = this;
        if (db) {
            console.log("Closing database ...");
            that.state.progress.push("Closing DB");
            that.setState(that.state);
            db.close().then((status) => {
                that.state.progress.push("Database CLOSED");
                that.setState(that.state);
            }).catch((error) => {
                that.errorCB(error);
            });
        } else {
            that.state.progress.push("Database was not OPENED");
            that.setState(that.state);
        }
    },

    deleteDatabase(){
        var that = this;
        that.state.progress = ["Deleting database"];
        that.setState(that.state);
        SQLite.deleteDatabase(database_name).then(() => {
            console.log("Database DELETED");
            that.state.progress.push("Database DELETED");
            that.setState(that.state);
        }).catch((error) => {
            that.errorCB(error);
        });
    },

    runDemo(){
        this.state.progress = ["Starting SQLite Promise Demo"];
        this.setState(this.state);
        this.loadAndQueryDB();
    },

    renderProgressEntry(entry){
        return (<View style={listStyles.li}>
            <View>
                <Text style={listStyles.liText}>{entry}</Text>
            </View>
        </View>)
    },

    render(){
        var ds = new ListView.DataSource({rowHasChanged: (row1, row2) => { row1 !== row2;}});
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
                dataSource={ds.cloneWithRows(this.state.progress)}
                renderRow={this.renderProgressEntry}
                style={listStyles.liContainer}/>
        </View>);
    }
});

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
