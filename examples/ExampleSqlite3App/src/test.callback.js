
var SQLite = require('react-native-sqlite-storage');

export function doTest() {

  let dbMaster, dbSecond;

  dbSecond = SQLite.openDatabase({name: 'second'},
    (db) => {
      console.log("Database 'second' successfully opened.");
      dbMaster = SQLite.openDatabase({name: 'master'},
        (db) => {
          console.log("Database 'master' successfully opened.");

          dbMaster.attach( "second", "second", () => console.log("Database attached successfully"), () => console.log("ERROR"));

          dbMaster.executeSql('CREATE TABLE IF NOT EXISTS bar (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, data TEXT)');
          dbMaster.executeSql("INSERT INTO bar (data) VALUES ('bar-test')");

          dbSecond.executeSql('CREATE TABLE IF NOT EXISTS foo (id INTEGER, data TEXT)');
          dbSecond.executeSql("INSERT INTO foo (data) VALUES ('test-foo')");


          dbMaster.readTransaction( (tx) => {
            var sql = "SELECT data FROM bar LIMIT 1";
            tx.executeSql(sql, [], (tx, results) => {
                console.log( '+', results.rows.item(0).data );
            });
          });

          dbMaster.readTransaction( (tx) => {
            var sql = "SELECT data FROM second.foo LIMIT 1";
            tx.executeSql(sql, [], (tx, results) => {
                console.log('+', results.rows.item(0).data );
            });
          })

        },
        (err) => console.log("Error on opening database 'master'", err)
      );
    },
    (err) => console.log("Error on opening database 'second'", err)
  );

  window.setTimeout( () => {
    dbMaster.detach("second", () => console.log("Database detached successfully"), () => console.log("ERROR on detach database"));

    dbSecond.close( ()=>{}, (err) => console.log("Error or close:", err ) );
    dbMaster.close( ()=>{}, (err) => console.log("Error or close:", err ) );

  }, 3000)
}
