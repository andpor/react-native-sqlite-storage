require 'json'

package = JSON.parse(File.read(File.join(__dir__, 'package.json')))

Pod::Spec.new do |s|
  s.name     = "react-native-sqlite-storage"
  s.version  = package['version']
  s.summary  = package['description']
  s.homepage = "https://github.com/andpor/react-native-sqlite-storage"
  s.license  = package['license']
  s.author   = package['author']
  s.source   = { :git => "https://github.com/andpor/react-native-sqlite-storage.git", :tag => "#{s.version}" }

  s.ios.deployment_target = '15.1'

  s.preserve_paths = 'README.md', 'LICENSE', 'package.json', 'sqlite.js'
  s.source_files   = "platforms/ios/*.{h,m,mm}"

  s.library = 'sqlite3'

  # RN 0.71+ helper: wires React-Codegen, header search paths, and
  # C++/FOLLY flags on new arch; no-ops on old arch. Fallback keeps the
  # pod installable outside a RN app Podfile context.
  if respond_to?(:install_modules_dependencies, true)
    install_modules_dependencies(s)
  else
    s.dependency 'React-Core'
  end
end
