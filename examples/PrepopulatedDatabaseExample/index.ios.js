/**
 * Sample React Native App
 * https://github.com/facebook/react-native
 * @flow
 */

import React, { Component } from 'react';
import {
  AppRegistry,
  StyleSheet,
  Text,
  View
} from 'react-native';

let SQLite = require('react-native-sqlite-storage')

export default class PrepopulatedDatabaseExample extends Component {

  constructor(props) {
    super(props)

    this.state = {
      record: null
    }

    let db = SQLite.openDatabase({name: 'test.db', createFromLocation : "~example.db", location: 'Library'}, this.openCB, this.errorCB);
    db.transaction((tx) => {
      tx.executeSql('SELECT * FROM test', [], (tx, results) => {
          console.log("Query completed");

          // Get rows with Web SQL Database spec compliance.

          var len = results.rows.length;
          for (let i = 0; i < len; i++) {
            let row = results.rows.item(i);
            console.log(`Record: ${row.name}`);
            this.setState({record: row});
          }
        });
    });

  }

  errorCB(err) {
    console.log("SQL Error: " + err);
  }

  successCB() {
    console.log("SQL executed fine");
  }

  openCB() {
    console.log("Database OPENED");
  }

  render() {
    return (
      <View style={styles.container}>
        <Text style={styles.welcome}>
          This is an example with sqlite3 and a prepopulated database. Enjoy!
        </Text>
        <Text style={styles.instructions}>
          {this.state.record !== null ? 'Success: ' + this.state.record.name : 'Waiting...'}
        </Text>
      </View>
    );
  }
}

const styles = StyleSheet.create({
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
});

AppRegistry.registerComponent('PrepopulatedDatabaseExample', () => PrepopulatedDatabaseExample);
