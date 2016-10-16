
var SQLite = require('react-native-sqlite-storage')

export function doTest() {
  const dbSecond = SQLite.openDatabase({name: 'second'}, openCB, errorCB);

  // SQLite.echoTest( () => console.log("SUCCESS"), () => console.log("ERROR") );

  const dbMaster = SQLite.openDatabase({
    name: 'master',
  }, openCB, errorCB);

  dbMaster.attach( "second", "second", () => console.log("Database Attached successfully"), () => console.log("ERROR"))

  dbMaster.executeSql('CREATE TABLE IF NOT EXISTS bar (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, data TEXT)');
  dbMaster.executeSql("INSERT INTO bar (data) VALUES ('bar-test')")

  dbSecond.executeSql('CREATE TABLE IF NOT EXISTS foo (id INTEGER, data TEXT)');
  dbSecond.executeSql("INSERT INTO foo (data) VALUES ('test-foo')")


  dbMaster.readTransaction( (tx) => {
    var sql = "SELECT data FROM bar LIMIT 1";
    tx.executeSql(sql, [], (tx, results) => {
        console.log( results.rows.item(0).data );
    });
  })

  dbMaster.readTransaction( (tx) => {
    var sql = "SELECT data FROM second.foo LIMIT 1";
    tx.executeSql(sql, [], (tx, results) => {
        console.log( results.rows.item(0).data );
    });
  })

}


function openCB() {
  console.log("openCB")
}

function errorCB() {
  console.log("errorCB")

}
