/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.swiftpm

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

class SwiftPackageManagerIncrementalExportIntegrationTest extends AbstractSwiftPackageManagerExportIntegrationTest {
    def swiftBuild() {
        createDirs("lib", "app")
        settingsFile << """
            include 'lib', 'app'
        """
        buildFile << """
            apply plugin: 'swiftpm-export'
            project(':lib') {
                apply plugin: 'swift-library'
            }
            project(':app') {
                apply plugin: 'swift-application'
            }
        """

        file("app/src/main/swift/main.swift") << "// main"
        file("lib/src/main/swift/lib.swift") << "// lib"

        run("generateSwiftPmManifest")

        assert file("Package.swift").text.contains('"src/main/swift/main.swift"')
        assert file("Package.swift").text.contains('"src/main/swift/lib.swift"')
    }

    private cppBuild() {
        createDirs("lib", "app")
        settingsFile << """
            include 'lib', 'app'
        """
        buildFile << """
            apply plugin: 'swiftpm-export'
            project(':lib') {
                apply plugin: 'cpp-library'
            }
            project(':app') {
                apply plugin: 'cpp-application'
            }
        """

        file("app/src/main/cpp/main.cpp") << "// main"
        file("lib/src/main/cpp/lib.cpp") << "// lib"

        run("generateSwiftPmManifest")

        assert file("Package.swift").text.contains('"src/main/cpp/main.cpp"')
        assert file("Package.swift").text.contains('"src/main/cpp/lib.cpp"')
    }

    @ToBeFixedForConfigurationCache
    def "regenerates manifest when Swift source files added or removed"() {
        given:
        swiftBuild()

        when:
        run("generateSwiftPmManifest")

        then:
        result.assertTaskSkipped(":generateSwiftPmManifest")

        when:
        file("app/src/main/swift/dir/app.swift") << "// app"
        run("generateSwiftPmManifest")

        then:
        result.assertTaskNotSkipped(":generateSwiftPmManifest")
        file("Package.swift").text.contains('"src/main/swift/dir/app.swift"')

        when:
        run("generateSwiftPmManifest")

        then:
        result.assertTaskSkipped(":generateSwiftPmManifest")

        when:
        file("app/src/main/swift/main.swift").delete()
        run("generateSwiftPmManifest")

        then:
        result.assertTaskNotSkipped(":generateSwiftPmManifest")
        !file("Package.swift").text.contains('main.swift')

        when:
        run("generateSwiftPmManifest")

        then:
        result.assertTaskSkipped(":generateSwiftPmManifest")
    }

    def "regenerates manifest when Swift components added or removed"() {
        given:
        swiftBuild()

        when:
        run("generateSwiftPmManifest")

        then:
        result.assertTaskSkipped(":generateSwiftPmManifest")

        when:
        createDirs("lib2")
        settingsFile << """
            include 'lib2'
        """
        file('lib2/build.gradle') << """
            project(':lib2') {
                apply plugin: 'swift-library'
            }
        """
        file("lib2/src/main/swift/dir/lib.swift") << "// lib"
        run("generateSwiftPmManifest")

        then:
        result.assertTaskNotSkipped(":generateSwiftPmManifest")
        file("Package.swift").text.contains('lib2')

        when:
        run("generateSwiftPmManifest")

        then:
        result.assertTaskSkipped(":generateSwiftPmManifest")

        when:
        file('lib2/build.gradle').text = ""
        run("generateSwiftPmManifest")

        then:
        result.assertTaskNotSkipped(":generateSwiftPmManifest")
        !file("Package.swift").text.contains('lib2')

        when:
        run("generateSwiftPmManifest")

        then:
        result.assertTaskSkipped(":generateSwiftPmManifest")
    }

    @ToBeFixedForConfigurationCache
    def "regenerates manifest when Swift dependencies added or removed"() {
        given:
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("test:test") { from(GitVersionControlSpec) { url = uri('repo') } }
                }
            }
        """
        swiftBuild()

        when:
        run("generateSwiftPmManifest")

        then:
        result.assertTaskSkipped(":generateSwiftPmManifest")

        when:
        file("app/build.gradle") << """
            dependencies { implementation project(':lib') }
        """
        run("generateSwiftPmManifest")

        then:
        result.assertTaskNotSkipped(":generateSwiftPmManifest")

        when:
        run("generateSwiftPmManifest")

        then:
        result.assertTaskSkipped(":generateSwiftPmManifest")

        when:
        file("app/build.gradle") << """
            dependencies { implementation "test:test:1.2" }
        """
        run("generateSwiftPmManifest")

        then:
        result.assertTaskNotSkipped(":generateSwiftPmManifest")

        when:
        run("generateSwiftPmManifest")

        then:
        result.assertTaskSkipped(":generateSwiftPmManifest")

        when:
        file("app/build.gradle").text = """"""
        run("generateSwiftPmManifest")

        then:
        result.assertTaskNotSkipped(":generateSwiftPmManifest")

        when:
        run("generateSwiftPmManifest")

        then:
        result.assertTaskSkipped(":generateSwiftPmManifest")
    }

    def "ignores irrelevant changes to Swift source"() {
        given:
        swiftBuild()

        when:
        file("app/src/main/swift/main.swift") << "// changed"
        file("lib/src/main/swift/lib.swift") << "// changed"
        run("generateSwiftPmManifest")

        then:
        result.assertTaskSkipped(":generateSwiftPmManifest")

        when:
        run("generateSwiftPmManifest")

        then:
        result.assertTaskSkipped(":generateSwiftPmManifest")
    }

    def "ignores irrelevant changes to Swift build"() {
        given:
        swiftBuild()

        when:
        buildFile << """
            allprojects {
                apply plugin: 'xcode'
                apply plugin: 'xctest'
            }
        """
        file("app/src/test/swift/test.swift") << "// test"
        file("lib/src/test/swift/test.swift") << "// test"
        run("generateSwiftPmManifest")

        then:
        result.assertTaskSkipped(":generateSwiftPmManifest")

        when:
        createDirs("other")
        settingsFile << """
            include 'other'
        """
        run("generateSwiftPmManifest")

        then:
        result.assertTaskSkipped(":generateSwiftPmManifest")
    }

    @ToBeFixedForConfigurationCache
    def "regenerates manifest when C++ source files added or removed"() {
        given:
        cppBuild()

        when:
        run("generateSwiftPmManifest")

        then:
        result.assertTaskSkipped(":generateSwiftPmManifest")

        when:
        file("app/src/main/cpp/dir/app.cpp") << "// app"
        run("generateSwiftPmManifest")

        then:
        result.assertTaskNotSkipped(":generateSwiftPmManifest")
        file("Package.swift").text.contains('"src/main/cpp/dir/app.cpp"')

        when:
        run("generateSwiftPmManifest")

        then:
        result.assertTaskSkipped(":generateSwiftPmManifest")

        when:
        file("app/src/main/cpp/main.cpp").delete()
        run("generateSwiftPmManifest")

        then:
        result.assertTaskNotSkipped(":generateSwiftPmManifest")
        !file("Package.swift").text.contains('main.cpp')
    }

    def "regenerates manifest when C++ components added or removed"() {
        given:
        swiftBuild()

        when:
        run("generateSwiftPmManifest")

        then:
        result.assertTaskSkipped(":generateSwiftPmManifest")

        when:
        createDirs("lib2")
        settingsFile << """
            include 'lib2'
        """
        file('lib2/build.gradle') << """
            project(':lib2') {
                apply plugin: 'cpp-library'
            }
        """
        file("lib2/src/main/cpp/lib.cpp") << "// lib"
        run("generateSwiftPmManifest")

        then:
        result.assertTaskNotSkipped(":generateSwiftPmManifest")
        file("Package.swift").text.contains('lib2')

        when:
        run("generateSwiftPmManifest")

        then:
        result.assertTaskSkipped(":generateSwiftPmManifest")

        when:
        file('lib2/build.gradle').text = ""
        run("generateSwiftPmManifest")

        then:
        result.assertTaskNotSkipped(":generateSwiftPmManifest")
        !file("Package.swift").text.contains('lib2')

        when:
        run("generateSwiftPmManifest")

        then:
        result.assertTaskSkipped(":generateSwiftPmManifest")
    }

    def "ignores irrelevant changes to C++ source"() {
        given:
        cppBuild()

        when:
        file("app/src/main/cpp/main.cpp") << "// changed"
        file("lib/src/main/cpp/lib.cpp") << "// changed"
        run("generateSwiftPmManifest")

        then:
        result.assertTaskSkipped(":generateSwiftPmManifest")

        when:
        file("app/src/main/cpp/app.h") << "// new"
        file("app/src/main/headers/app.h") << "// new"
        file("app/src/main/public/app.h") << "// new"
        run("generateSwiftPmManifest")

        then:
        result.assertTaskSkipped(":generateSwiftPmManifest")
    }

    def "ignores irrelevant changes to C++ build"() {
        given:
        cppBuild()

        when:
        buildFile << """
            allprojects {
                apply plugin: 'xcode'
                apply plugin: 'cpp-unit-test'
            }
        """
        file("app/src/test/cpp/test.cpp") << "// test"
        file("lib/src/test/cpp/test.cpp") << "// test"
        run("generateSwiftPmManifest")

        then:
        result.assertTaskSkipped(":generateSwiftPmManifest")

        when:
        createDirs("other")
        settingsFile << """
            include 'other'
        """
        run("generateSwiftPmManifest")

        then:
        result.assertTaskSkipped(":generateSwiftPmManifest")
    }
}
