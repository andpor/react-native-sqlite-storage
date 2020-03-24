/**
 * Test App using JSON1 functionality for react-native-sqlite-storage
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
} from 'react-native';
import ListView from 'deprecated-react-native-listview';

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
    };

    componentWillUnmount = () => {
        this.closeDatabase();
    };

    errorCB = (err) => {
        console.log("error: ",err);
        this.updateProgress("Error: "+ (err.message || err));
        return false;
    };

    successCB = () => {
        console.log("SQL executed ...");
    };

    openCB = () => {
        this.updateProgress("Database OPEN");
    };

    closeCB = () => {
        this.updateProgress("Database CLOSED");
    };

    deleteCB = () => {
        console.log("Database DELETED");
        this.updateProgress("Database DELETED");
    };

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
    };

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
            + 'custom_info TEXT DEFAULT "",'
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

        tx.executeSql(`INSERT INTO Employees (name, office, department, custom_info) VALUES ("Sylvester Stallone", 2, 4, '{"known": true}')`, []);
        tx.executeSql(`INSERT INTO Employees (name, office, department, custom_info) VALUES ("Elvis Presley", 2, 4, '{"known": true}')`, []);
        tx.executeSql(`INSERT INTO Employees (name, office, department, custom_info) VALUES ("Leslie Nelson", 3, 4, '{"known": true}')`, []);
        tx.executeSql(`INSERT INTO Employees (name, office, department, custom_info) VALUES ("Fidel Castro", 3, 3, '{"known": true}')`, []);
        tx.executeSql(`INSERT INTO Employees (name, office, department, custom_info) VALUES ("Bill Clinton", 1, 3, '{"known": false}')`, []);
        tx.executeSql(`INSERT INTO Employees (name, office, department, custom_info) VALUES ("Margaret Thatcher", 1, 3, '{"known": true}')`, []);
        tx.executeSql(`INSERT INTO Employees (name, office, department, custom_info) VALUES ("Donald Trump", 2, 4, '{"known": true, "impeached": true}')`, []);
        tx.executeSql(`INSERT INTO Employees (name, office, department, custom_info) VALUES ("Dr DRE", 2, 2, '{"known": true}')`, []);
        tx.executeSql(`INSERT INTO Employees (name, office, department, custom_info) VALUES ("Samantha Fox", 2, 1, '{"known": true}')`, []);

        console.log("all config SQL done");
    };

    queryEmployees = async (tx) => {
        console.log("Executing JSON1 queries...");

        // 1. JSON_OBJECT
        await tx.executeSql(`SELECT JSON_OBJECT('name', e.name, 'office_id', e.office, 'department_id', e.department) AS data FROM Employees e`, [],
            this.querySuccess, this.errorCB);

        // 2. JSON_ARRAY
        // Expected: [1,2,"3",4]
        await tx.executeSql(`SELECT JSON_ARRAY(1, 2, '3', 4) AS data `, [],
            this.querySuccess, this.errorCB);

        // 3. JSON_ARRAY_LENGTH
        // Expected: 4
        await tx.executeSql(`SELECT JSON_ARRAY_LENGTH('[1, 2, 3, 4]') AS data`, [],
            this.querySuccess, this.errorCB);

        // 4. JSON_EXTRACT
        await tx.executeSql(`SELECT JSON_EXTRACT(e.custom_info, '$.known')  AS data FROM Employees e`, [],
            this.querySuccess, this.errorCB);

        // 5. JSON_INSERT
        // Expected: {"a":1,"b":2,"c":3}
        await tx.executeSql(`SELECT JSON_INSERT('{"a": 1, "b": 2}', '$.c', 3)  AS data`, [],
            this.querySuccess, this.errorCB);

        // 6. JSON_REPLACE
        // Expected: {"a":1,"b":3}
        await tx.executeSql(`SELECT JSON_REPLACE('{"a": 1, "b": 2}', '$.b', 3)  AS data`, [],
            this.querySuccess, this.errorCB);

        // 7. JSON_SET
        // Expected: {"a":1,"b":123}
        await tx.executeSql(`SELECT JSON_SET('{"a": 1, "b": 2}', '$.b', 123)  AS data`, [],
            this.querySuccess, this.errorCB);

        // 8. JSON_REMOVE
        // Expected: {"a":1"}
        await tx.executeSql(`SELECT JSON_REMOVE('{"a": 1, "b": 2}', '$.b')  AS data`, [],
            this.querySuccess, this.errorCB);

        // 9. JSON_TYPE
        // Expected: integer
        await tx.executeSql(`SELECT JSON_TYPE('{"a": 1, "b": 2}', '$.a')  AS data`, [],
            this.querySuccess, this.errorCB);

        // 10. JSON_VALID
        // Expected: 0
        await tx.executeSql(`SELECT JSON_VALID('{"a": 1, "b": 2')  AS data`, [],
            this.querySuccess, this.errorCB);

        // 11. JSON_QUOTE
        // Expected: "value"
        await tx.executeSql(`SELECT JSON_QUOTE('value')  AS data`, [],
            this.querySuccess, this.errorCB);
    };

    querySuccess = (tx,results) => {
        this.updateProgress("Query completed");
        var len = results.rows.length;
        for (let i = 0; i < len; i++) {
            let row = results.rows.item(i);
            this.updateProgress(`${row.data}`);
        }
    };

    loadAndQueryDB = () => {
        this.updateProgress("Opening database ...",true);
        db = SQLite.openDatabase(database_name, database_version, database_displayname, database_size, this.openCB, this.errorCB);
        this.populateDatabase(db);
    };

    deleteDatabase = () => {
        this.updateProgress("Deleting database");
        SQLite.deleteDatabase(database_name, this.deleteCB, this.errorCB);
    };

    closeDatabase = () => {
        if (db) {
            console.log("Closing database ...");
            this.updateProgress("Closing database");
            db.close(this.closeCB,this.errorCB);
        } else {
            this.updateProgress("Database was not OPENED");
        }
    };

    runDemo = () => {
        this.updateProgress("Starting SQLite Callback Demo",true);
        this.loadAndQueryDB();
    };

    renderProgressEntry = (entry) => {
        return (<View style={listStyles.li}>
            <View>
                <Text style={listStyles.liText}>{entry}</Text>
            </View>
        </View>)
    };

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
