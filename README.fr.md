# react-native-sqlite-storage

Plugin SQLite3 natif pour React Native pour Android (Classic et Native), iOS et Windows

La création de cette librairie est basée sur le plugin Cordova SQLite de Chris Brody.

Fonctionnalités :

1. iOS et Android pris en charge via une API JavaScript identique.
2. Android en Java et en natif pur.
3. Transactions SQL.
4. Interface JavaScript via des callbacks ou Promesses.
5. Importation d'une base de données SQLite pré-remplie à partir d'un bundle et d'une sandbox.
6. Windows prend en charge une API de callbacks identique à iOS et Android.

Des applications d'exemple sont fournies dans le répertoire de test qui peuvent être utilisées avec AwesomeProject généré par React Native. Tout ce que vous avez à faire est de copier l'un de ces fichiers dans votre AwesomeProject en remplaçant index.ios.js.

Veuillez me faire savoir quels sont vos projets qui utilisent ces modules SQLite React Native. Je les énumérerai dans la section de référence. Si vous pensez que des fonctionnalités pourraient être ajoutées à cette librairie, veuillez les publier.

La librairie a été testée avec React 16.2 (et antérieur) et XCode 7,8,9 - elle fonctionne parfaitement sans aucun ajustement ni changement de code. Pour XCode 7,8 vs XCode 6, la seule différence est que le suffixe du nom de librairie ios sqlite est tbd au lieu de dylib.

La version 3.2 est la première version compatible avec RN 0.40.

# Installation

```
  npm install --save react-native-sqlite-storage
```

Ensuite, suivez les instructions pour votre plateforme pour lier react-native-sqlite-storage dans votre projet.

## Promesses

Pour activer les promesses, exécutez

```javascript
SQLite.enablePromise(true);
```

## iOS

#### Méthode standard

** React Native 0.60 et supérieur **
Exécutez `cd ios && pod install && cd ..`. Le lien n'est pas nécessaire dans React Native 0.60 et supérieur.

** React Native 0.59 et inférieur **

#### Étape 1. Installation des dépendances

##### Avec CocoaPods:

Ajoutez ceci à votre Podfile qui doit être situé à l'intérieur du sous-répertoire du projet ios

```ruby
pod 'React', :path => '../node_modules/react-native'
pod 'react-native-sqlite-storage', :path => '../node_modules/react-native-sqlite-storage'
```

Ou utilisez le Podfile d'exemple inclus dans le package en le copiant dans le sous-répertoire ios et en remplaçant AwesomeProject à l'intérieur par le nom de votre projet RN.

Actualisez l'installation des pods

```ruby
pod install
```

OU

```ruby
pod update
```

Terminé, passez à l'étape 2.

##### Sans CocoaPods:

Cette commande doit être exécutée dans le répertoire racine de votre projet RN

```shell
react-native link
```

rnpm et xcode sont des dépendances de ce projet et devraient être installés avec le module, mais en cas de problème d'exécution de rnpm link et que rnpm/xcode ne sont pas encore installées, vous pouvez essayer de les installer globalement comme suit :

```shell
npm -g install rnpm xcode
```

Après la liaison, le projet devrait ressembler à ceci :

![alt tag](instructions/after-rnpm.png)

#### Étape 1a. Si la liaison rnpm ne fonctionne pas pour vous, vous pouvez essayer de la lier manuellement en suivant les instructions ci-dessous :

##### Faites glisser le projet SQLite Xcode en tant que projet de dépendance dans votre projet React Native XCode

![alt tag](https://raw.github.com/andpor/react-native-sqlite-storage/master/instructions/libs.png)

##### Configuration de la dépendance des librairies SQLite XCode

Ajoutez libSQLite.a (à partir de l'emplacement du Workspace) aux librairies et frameworks requis. Ajoutez également sqlite3.0.tbd (XCode 7) ou libsqlite3.0.dylib (XCode 6 et antérieurs) de la même manière en utilisant la vue des librairies requises (ne les ajoutez pas manuellement car les chemins de construction ne seront pas correctement définis).

![alt tag](https://raw.github.com/andpor/react-native-sqlite-storage/master/instructions/addlibs.png)

#### Étape 2. Application JavaScript requise

Ajoutez var SQLite = require('react-native-sqlite-storage') à votre index.ios.js

![alt tag](instructions/require.png)

#### Étape 3. Écrire du code JavaScript d'application en utilisant le plugin SQLite

Ajoutez du code JS pour utiliser l'API SQLite dans votre index.ios.js, etc. Voici un exemple de code. Pour un exemple complet fonctionnel, voir test/index.ios.callback.js. Veuillez noter que l'API basée sur les promesses est maintenant également prise en charge avec des exemples complets dans l'application React Native fonctionnelle sous test/index.ios.promise.js.

```javascript
errorCB(err) {
  console.log("SQL Error: " + err);
},

successCB() {
  console.log("SQL s'éxécute correctement");
},

openCB() {
  console.log("Base de données CONNECTÉE");
},

var db = SQLite.openDatabase("test.db", "1.0", "Test Database", 200000, openCB, errorCB);
db.transaction((tx) => {
  tx.executeSql('SELECT * FROM Employees a, Departments b WHERE a.department = b.department_id', [], (tx, results) => {
      console.log("Query éxécutée");

      // Obtenir des lignes conformes à la spécification de la base de données Web SQL.

      var len = results.rows.length;
      for (let i = 0; i < len; i++) {
        let row = results.rows.item(i);
        console.log(`Nom de l'employé : ${row.name}, Nom de l'équipe : ${row.deptName}`);
      }

      // Autrement, vous pouvez utiliser la méthode non-standard "raw".

      /*
        let rows = results.rows.raw(); // copie superficielle (ou partielle) du tableau 'rows'

        rows.map(row => console.log(`Nom de l'employé : ${row.name}, Nom de l'équipe : ${row.deptName}`));
      */
    });
});
```

# Utilisation (Android):

** React Native 0.60 et supérieur **
Si vous souhaitez utiliser SQLite du dispositif, il n'y a pas d'étapes supplémentaires à suivre.
Cependant, si vous souhaitez utiliser SQLite inclus dans cette librairie (qui inclut le support de FTS5), ajoutez le code suivant à votre `react-native.config.js`

```js
module.exports = {
  ...,
  dependencies: {
    ...,
    "react-native-sqlite-storage": {
      platforms: {
        android: {
          sourceDir:
            "../node_modules/react-native-sqlite-storage/platforms/android-native",
          packageImportPath: "import io.liteglue.SQLitePluginPackage;",
          packageInstance: "new SQLitePluginPackage()"
        }
      }
    }
    ...
  }
  ...
};
```

** React Native 0.59 et inférieur **

#### Étape 1 - Mettez à jour les paramètres de Gradle (situés sous Paramètres de Gradle dans le panneau de projet)

```gradle
// file: android/settings.gradle
...

include ':react-native-sqlite-storage'
project(':react-native-sqlite-storage').projectDir = new File(rootProject.projectDir, '../node_modules/react-native-sqlite-storage/platforms/android') // react-native-sqlite-storage >= 4.0.0
// IMPORTANT : si vous travaillez avec une version antérieure à 4.0.0, le répertoire de projet est '../node_modules/react-native-sqlite-storage/src/android'
```

#### Étape 2 - Mettez à jour le script de génération Gradle du module de l'application (situé sous Paramètres Gradle dans le panneau de projet)

```gradle
// file: android/app/build.gradle
...

dependencies {
    ...
    implementation project(':react-native-sqlite-storage')
}
```

#### Étape 3 - Enregistrez le package React (cela devrait fonctionner sur toutes les versions de React, mais si ce n'est pas le cas, essayez l'approche basée sur ReactActivity. Remarque : pour la version 3.0.0 et inférieure, vous devrez passer l'instance de votre Activity au constructeur de SQLitePluginPackage).

```java
...
import org.pgsqlite.SQLitePluginPackage;

public class MainActivity extends Activity implements DefaultHardwareBackBtnHandler {

    private ReactInstanceManager mReactInstanceManager;
    private ReactRootView mReactRootView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mReactRootView = new ReactRootView(this);
        mReactInstanceManager = ReactInstanceManager.builder()
                .setApplication(getApplication())
                .setBundleAssetName("index.android.bundle")  // cela dépend de la façon dont vous nommez vos fichiers JS, l'exemple suppose index.android.js
                .setJSMainModuleName("index.android")        // Ceci dépend de la façon dont vous nommez vos fichiers JS, l'exemple suppose index.android.js
                .addPackage(new MainReactPackage())
                .addPackage(new SQLitePluginPackage())       // enregistrez le plugin SQLite ici
                .setUseDeveloperSupport(BuildConfig.DEBUG)
                .setInitialLifecycleState(LifecycleState.RESUMED)
                .build();
        mReactRootView.startReactApplication(mReactInstanceManager, "AwesomeProject", null); // changez "AwesomeProject" par le nom de votre application
        setContentView(mReactRootView);
    }
...

```

Approche alternative sur les versions plus récentes de React Native (0.18+). Note : pour la version 3.0.0 et inférieure, vous devrez passer l'instance de votre activité au constructeur SQLitePluginPackage.

```java
import org.pgsqlite.SQLitePluginPackage;

public class MainApplication extends Application implements ReactApplication {
  ......

  /**
   * Une liste de packages utilisés par l'application.
   * Si l'application utilise des vues ou des modules supplémentaires
   * en plus de ceux par défaut, ajoutez d'autres packages ici.
   */
    @Override
    protected List<ReactPackage> getPackages() {
      return Arrays.<ReactPackage>asList(
        new SQLitePluginPackage(),   // enregistrez le plugin SQLite ici
        new MainReactPackage());
    }
}
```

#### Étape 4 - Exigence et utilisation en JavaScript - voir des exemples complets (callbacks et Promesses) dans le répertoire de test.

```js
// fichier : index.android.js

var React = require('react-native');
var SQLite = require('react-native-sqlite-storage')
...
```

## Windows

** RNW 0.63 avec l'auto-lien et versions supérieures. **

Aucune étape manuelle requise

** React Native 0.62 **

### Étape 1 : Mettre à jour le fichier solution

Ajoutez le projet `SQLitePlugin` à votre solution.

1. Ouvrez la solution dans Visual Studio 2019
2. Cliquez avec le bouton droit de la souris sur l'icône Solution dans l'Explorateur de solutions > Ajouter > Projet existant
3. Sélectionnez `node_modules\react-native-sqlite-storage\platforms\windows\SQLitePlugin\SQLitePlugin.vcxproj`

### Étape 2 : Mettre à jour le fichier .vcxproj

Ajoutez une référence à `SQLitePlugin` à votre projet d'application principal. Depuis Visual Studio 2019 :

1. Cliquez avec le bouton droit sur le projet principal de l'application > Ajouter > Référence...
2. Cochez `SQLitePlugin` dans les projets de la solution.

### Étape 3: Mettre à jour le fichier `pch.h`

Ajoutez `#include "winrt/SQLitePlugin.h"`.

### Étape 4: Enregistrez le paquet dans `App.cpp`

Ajouter `PackageProviders().Append(winrt::SQLitePlugin::ReactPackageProvider());` avant `InitializeComponent();`.

Référez-vous à ce guide pour plus de détails : https://microsoft.github.io/react-native-windows/docs/next/native-modules-using

## Configuration de votre projet pour importer une base de données SQLite pré-remplie à partir de l'application pour iOS.

#### Étape 1 - Créer le dossier 'www'.

Créer un dossier appelé 'www' (oui vous devez l'appeler ainsi précisément sinon le reste ne fonctionnera pas) dans le dossier du projet avec Finder

#### Étape 2 - Créer le fichier de base de donnée

Copiez-collez votre fichier de base de données pré-rempli dans le dossier 'www'. Donnez-lui le même nom que celui que vous allez utiliser dans l'appel openDatabase de votre application.

#### Étape 3 - Ajouter un fichier au projet

Dans XCode, faites un clic droit sur le dossier principal et sélectionnez Ajouter des fichiers à 'nom de votre projet'.

![alt tag](https://raw.github.com/andpor/react-native-sqlite-storage/master/instructions/addFilesToProject.png)

#### Étape 4 - Choisissez les fichiers à ajouter

Dans la boîte de dialogue Ajouter des fichiers, accédez au répertoire 'www' que vous avez créé à l'étape 1, sélectionnez-le, assurez-vous de cocher l'option Créer une référence de dossier.

![alt tag](https://raw.github.com/andpor/react-native-sqlite-storage/master/instructions/addFilesToProjectSelect.png)

#### Étape 5 - Vérifiez la structure du projet

Assurez-vous que la structure de votre projet, après l'exécution des étapes précédentes, ressemble à cela

![alt tag](https://raw.github.com/andpor/react-native-sqlite-storage/master/instructions/projectStructureAfter.png)

### Étape 6 - Modifier l'appel openDatabase

Modifiez l'appel openDatabase de votre application en ajoutant le paramètre createFromLocation. Si vous avez nommé votre fichier de base de données dans l'étape 2 'testDB', l'appel openDatabase devrait ressembler à quelque chose comme ceci :

```js

  ...
  1.SQLite.openDatabase({name : "testDB", createFromLocation : 1}, okCallback,errorCallback);
  // par défaut - si votre dossier est appelé www et que le fichier de données porte le même nom que dbName - testDB dans cet exemple
  2.SQLite.openDatabase({name : "testDB", createFromLocation : "~data/mydbfile.sqlite"}, okCallback,errorCallback);
  // si votre dossier s'appelle data plutôt que www ou si votre nom de fichier ne correspond pas au nom de la base de données
  3.SQLite.openDatabase({name : "testDB", createFromLocation : "/data/mydbfile.sqlite"}, okCallback,errorCallback);
  // si votre dossier n'est pas dans le bundle de l'application mais dans le sandbox de l'application, c'est-à-dire téléchargé à partir d'un emplacement distant.
  ...

```

Pour Android, le répertoire www est toujours relatif au répertoire assets de l'application : src/main/assets

Amusez-vous bien !

## Ouvrir une base de données

L'ouverture d'une base de données est légèrement différente entre iOS et Android. Alors que sur Android, l'emplacement du fichier de base de données est fixe, il y a trois choix d'emplacement possibles pour le fichier de base de données sur iOS. Le paramètre 'location' que vous fournissez à l'appel openDatabase indique où vous souhaitez que le fichier soit créé. Ce paramètre est ignoré sur Android.

AVERTISSEMENT : l'emplacement par défaut sur iOS a changé dans la version 3.0.0 - c'est maintenant un emplacement sans synchronisation tel que prescrit par Apple, de sorte que la version n'est pas rétrocompatible.

Pour ouvrir une base de données dans l'emplacement par défaut sans synchronisation (affecte _uniquement_ iOS) :

```js
SQLite.openDatabase({ name: "my.db", location: "default" }, successcb, errorcb);
```

Pour spécifier un emplacement différent (affecte _uniquement_ iOS) :

```js
SQLite.openDatabase({ name: "my.db", location: "Library" }, successcb, errorcb);
```

où l'option `location` peut être définie sur l'un des choix suivants:

- `default`: Sous-répertoire `Library/LocalDatabase` - _NON_ visible dans iTunes et _NON_ sauvegardé par iCloud
- `Library`: Sous-répertoire `Library` - sauvegardé par iCloud, _NON_ visible dans iTunes
- `Documents`: Sous-répertoire `Documents` - visible dans iTunes et sauvegardé par iCloud
- `Shared`: Conteneur partagé du groupe d'applications - _voir la section suivante_

L'ancienne méthode webSql openDatabase fonctionne toujours et l'emplacement sera implicitement défini sur l'option «default» :

```js
SQLite.openDatabase("myDatabase.db", "1.0", "Demo", -1);
```

## Ouverture d'une base de données dans le conteneur partagé d'un groupe d'applications (iOS)

Si vous avez une extension d'application iOS qui doit partager l'accès à la même instance de base de données que votre application principale, vous devez utiliser le conteneur partagé d'un groupe d'applications enregistré.

En supposant que vous ayez déjà configuré un groupe d'applications et activé le droit "Groupes d'applications" à la fois pour l'application principale et l'extension d'application, en les réglant sur le même nom de groupe d'applications, les étapes supplémentaires suivantes doivent être effectuées :

#### Étape 1 - fournir le nom de votre groupe d'applications dans tous les fichiers `Info.plist` nécessaires

Dans les fichiers `ios/MY_APP_NAME/Info.plist` et `ios/MY_APP_EXT_NAME/Info.plist` (ainsi que pour toutes les autres extensions d'application que vous pourriez avoir), vous devez simplement ajouter la clé `AppGroupName` au dictionnaire principal avec le nom de votre groupe d'applications en tant que valeur de chaîne :

```xml
<plist version="1.0">
<dict>
  <!-- ... -->
  <key>AppGroupName</key>
  <string>MON_NOM_DE_GROUPE_APPLICATION</string>
  <!-- ... -->
</dict>
</plist>
```

#### Étape 2 - définir l'emplacement de la base de données partagée

Lors de l'appel de `SQLite.openDatabase` dans votre code React Native, vous devez définir le paramètre `location` sur `'Shared'` :

```js
SQLite.openDatabase({ name: "my.db", location: "Shared" }, successcb, errorcb);
```

## Importation d'une base de données pré-remplie.

Vous pouvez importer un fichier de base de données pré-rempli existant dans votre application. Selon vos instructions dans l'appel openDatabase, sqlite-storage examinera différents endroits pour localiser votre fichier de base de données pré-rempli.

Utilisez cette variante de l'appel openDatabase si votre dossier s'appelle www et que le fichier de données porte le même nom que le nom de la base de données - testDB dans cet exemple :

```js
SQLite.openDatabase(
  { name: "testDB", createFromLocation: 1 },
  okCallback,
  errorCallback
);
```

Utilisez cette version de l'appel openDatabase si votre dossier s'appelle "data" au lieu de "www" ou si le nom de votre fichier ne correspond pas au nom de la base de données. Dans ce cas, la base de données s'appelle "testDB" mais le fichier s'appelle "mydbfile.sqlite" et se trouve dans un sous-répertoire "data" de "www".

```js
SQLite.openDatabase(
  { name: "testDB", createFromLocation: "~data/mydbfile.sqlite" },
  okCallback,
  errorCallback
);
```

Utilisez cette version de l'appel openDatabase si votre dossier n'est pas dans le bundle d'application, mais dans l'application sandbox, c'est-à-dire téléchargé depuis un emplacement distant. Dans ce cas, le fichier source se trouve dans le sous-répertoire "data" de l'emplacement "Documents" (iOS) ou "FilesDir" (Android).

```js
SQLite.openDatabase(
  { name: "testDB", createFromLocation: "/data/mydbfile.sqlite" },
  okCallback,
  errorCallback
);
```

## Options supplémentaires pour les fichiers de base de données préremplis

Vous pouvez fournir des instructions supplémentaires à sqlite-storage pour lui indiquer comment gérer votre fichier de base de données prérempli. Par défaut, le fichier source est copié vers l'emplacement interne, ce qui fonctionne dans la plupart des cas, mais parfois ce n'est pas vraiment une option, surtout lorsque le fichier de base de données source est volumineux. Dans de telles situations, vous pouvez indiquer à sqlite-storage que vous ne voulez pas copier le fichier, mais plutôt l'utiliser en lecture seule via un accès direct. Vous y parvenez en fournissant un paramètre facultatif supplémentaire "readOnly" à l'appel openDatabase.

```js
SQLite.openDatabase(
  {
    name: "testDB",
    readOnly: true,
    createFromLocation: "/data/mydbfile.sqlite",
  },
  okCallback,
  errorCallback
);
```

Notez que dans ce cas, le fichier de base de données source sera ouvert en mode lecture seule et aucune mise à jour ne sera autorisée. Vous ne pouvez pas supprimer une base de données qui a été ouverte avec l'option "readOnly". Pour Android, l'option "read-only" fonctionne avec les fichiers de base de données préremplis situés dans le répertoire "FilesDir" car tous les autres actifs ne sont jamais physiquement situés sur le système de fichiers, mais plutôt lus directement à partir du bundle de l'application.

## Attachement d'une autre base de données

Sqlite3 offre la possibilité d'attacher une autre base de données à une instance de base de données existante, c'est-à-dire de rendre les JOINS de base de données croisées disponibles.
Cette fonctionnalité permet de SELECT et de JOIN des tables sur plusieurs bases de données avec une seule instruction et une seule connexion de base de données.
Pour cela, vous devez ouvrir les deux bases de données et appeler la méthode "attach()" de la base de données de destination (ou maître) aux autres.

```js
let dbMaster, dbSecond;

dbSecond = SQLite.openDatabase(
  { name: "second" },
  (db) => {
    dbMaster = SQLite.openDatabase(
      { name: "master" },
      (db) => {
        dbMaster.attach(
          "second",
          "second",
          () => console.log("Base de données attachée avec succès"),
          () => console.log("ERREUR")
        );
      },
      (err) =>
        console.log("Erreur d'ouverture de la base de données 'master'", err)
    );
  },
  (err) => console.log("Erreur d'ouverture de la base de données 'second'", err)
);
```

Le premier argument de attach() est le nom de la base de données, qui est utilisé dans SQLite.openDatabase(). Le deuxième argument est l'alias, qui est utilisé pour interroger les tables de la base de données attachée.

La déclaration suivante sélectionnerait des données de la base de données principale et inclurait la base de données "second" dans une instruction SELECT/JOIN simple :

```sql
SELECT * FROM user INNER JOIN second.subscriptions s ON s.user_id = user.id
```

Pour détacher une base de données, il suffit d'utiliser la méthode detach() :

```js
dbMaster.detach("second", successCallback, errorCallback);
```

Bien sûr, il y a également une prise en charge des promesses disponible pour attach() et detach(), comme le montre l'exemple d'application dans le répertoire "examples".

# Original Cordova SQLite Bindings de Chris Brody et Davide Bertola

https://github.com/litehelpers/Cordova-sqlite-storage

Les problèmes et limitations pour SQLite actuel peuvent être trouvés sur ce site.

## Problèmes

1. Android lie toutes les valeurs d'entrée SQL numériques à double. Cela est dû à la limitation sous-jacente de React Native où seul un type numérique est disponible sur l'interface, ce qui rend ambigû la distinction entre les entiers et les doubles. Une fois que j'aurai trouvé la bonne façon de faire cela, je mettrai à jour le code [(Issue #4141)] (https://github.com/facebook/react-native/issues/4141).
