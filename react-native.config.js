module.exports = {
	dependency: {
		platforms: {
			ios: {},
			android: {
				sourceDir: './platforms/android'
			},
			windows: {
				sourceDir: './platforms/windows',
				solutionFile: 'SQLitePlugin.sln',
				projects: [
				  {
					projectFile: 'SQLitePlugin/SQLitePlugin.vcxproj',
					directDependency: true,
				  }
				],
			}
		}
	}
}
