
var SQLite = require('react-native-sqlite-storage');
SQLite.enablePromise(true);

export function doTest() {

  let dbMaster, dbSecond;

  dbSecond = SQLite.openDatabase({name: 'second'}).then(
    (db) => {
      dbSecond = db;
      console.log("Database 'second' successfully opened.");
      dbMaster = SQLite.openDatabase({name: 'master'}).then( (db) => {
          dbMaster = db;
          console.log("Database 'master' successfully opened.");

          dbMaster.attach( "second", "second" ).then( () =>
            console.log("Database attached successfully")
          ).catch( (err) => console.log("ERROR", err) );

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
      });
    }
  );

  window.setTimeout( () => {
    dbMaster.detach("second")
      .then( () => {
        dbSecond.close();
        dbMaster.close();
    }).catch( () => console.log("ERROR on detach database") );


  }, 3000)
}
