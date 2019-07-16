module.exports = {
	dependency: {
		platforms: {
			ios: {},
			android: {}
		}
	},
	project: {
		ios: {
			project: 'src/ios/SQLite.xcodeproj'
		},
		android: {
			sourceDir: 'src/android'
		}
	}
}
