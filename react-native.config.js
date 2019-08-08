module.exports = {
  project: {
    ios: {
      project: 'src/ios/SQLite.xcodeproj'
    },
    android: {
      sourceDir: 'src/android',
      settingsGradlePath: 'src/android/settings.gradle',
      buildGradlePath: 'src/android/build.gradle',
      manifestPath: 'src/android/src/main/AndroidManifest.xml'
    }
  }
}
