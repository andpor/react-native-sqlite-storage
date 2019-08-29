module.exports = {
	dependency: {
		platforms: {
			ios: {
				project: './platforms/ios/SQLite.xcodeproj'
			},
			android: {
				sourceDir: `./platforms/android${process.env.USE_ANDROID_SQLITE_NATIVE ? "-native" : ""}`
			}
		}
	}
}
