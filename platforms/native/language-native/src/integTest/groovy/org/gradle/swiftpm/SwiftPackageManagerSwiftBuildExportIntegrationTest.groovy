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
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithLibraries
import org.gradle.nativeplatform.fixtures.app.SwiftLib

class SwiftPackageManagerSwiftBuildExportIntegrationTest extends AbstractSwiftPackageManagerExportIntegrationTest {

    @ToBeFixedForConfigurationCache(because = "Task.getProject() during execution")
    def "produces manifest for single project Swift library that defines only the production targets"() {
        given:
        buildFile << """
            plugins {
                id 'swiftpm-export'
                id 'swift-library'
                id 'xctest'
            }
"""
        def lib = new SwiftLib()
        lib.writeToProject(testDirectory)
        file("src/test/swift/test.swift") << "// test"

        when:
        run("generateSwiftPmManifest")

        then:
        file("Package.swift").text == """// swift-tools-version:4.0
//
// GENERATED FILE - do not edit
//
import PackageDescription

let package = Package(
    name: "test",
    products: [
        .library(name: "test", type: .dynamic, targets: ["Test"]),
    ],
    targets: [
        .target(
            name: "Test",
            path: ".",
            sources: [
                "src/main/swift/greeter.swift",
                "src/main/swift/multiply.swift",
                "src/main/swift/sum.swift",
            ]
        ),
    ]
)
"""
        swiftPmBuildSucceeds()
    }

    @ToBeFixedForConfigurationCache(because = "Task.getProject() during execution")
    def "produces manifest for multi-project Swift build"() {
        given:
        createDirs("hello", "log")
        settingsFile << "include 'hello', 'log'"
        buildFile << """
            plugins {
                id 'swiftpm-export'
                id 'swift-application'
            }
            subprojects {
                apply plugin: 'swift-library'
            }
            dependencies {
                implementation project(':hello')
            }
            project(':hello') {
                dependencies {
                    implementation project(':log')
                }
            }
"""
        def app = new SwiftAppWithLibraries()
        app.application.writeToProject(testDirectory)
        app.library.writeToProject(file("hello"))
        app.logLibrary.writeToProject(file("log"))

        when:
        run("generateSwiftPmManifest")

        then:
        file("Package.swift").text == """// swift-tools-version:4.0
//
// GENERATED FILE - do not edit
//
import PackageDescription

let package = Package(
    name: "test",
    products: [
        .executable(name: "test", targets: ["Test"]),
        .library(name: "hello", type: .dynamic, targets: ["Hello"]),
        .library(name: "log", type: .dynamic, targets: ["Log"]),
    ],
    targets: [
        .target(
            name: "Test",
            dependencies: [
                .target(name: "Hello"),
            ],
            path: ".",
            sources: [
                "src/main/swift/main.swift",
            ]
        ),
        .target(
            name: "Hello",
            dependencies: [
                .target(name: "Log"),
            ],
            path: "hello",
            sources: [
                "src/main/swift/greeter.swift",
            ]
        ),
        .target(
            name: "Log",
            path: "log",
            sources: [
                "src/main/swift/log.swift",
            ]
        ),
    ]
)
"""
        swiftPmBuildSucceeds()
    }

    @ToBeFixedForConfigurationCache(because = "Task.getProject() during execution")
    def "produces manifest for Swift library with shared and static linkage"() {
        given:
        buildFile << """
            plugins {
                id 'swiftpm-export'
                id 'swift-library'
            }
            library {
                linkage = [Linkage.SHARED, Linkage.STATIC]
            }
"""
        def lib = new SwiftLib()
        lib.writeToProject(testDirectory)

        when:
        run("generateSwiftPmManifest")

        then:
        file("Package.swift").text == """// swift-tools-version:4.0
//
// GENERATED FILE - do not edit
//
import PackageDescription

let package = Package(
    name: "test",
    products: [
        .library(name: "test", type: .dynamic, targets: ["Test"]),
        .library(name: "testStatic", type: .static, targets: ["Test"]),
    ],
    targets: [
        .target(
            name: "Test",
            path: ".",
            sources: [
                "src/main/swift/greeter.swift",
                "src/main/swift/multiply.swift",
                "src/main/swift/sum.swift",
            ]
        ),
    ]
)
"""
        swiftPmBuildSucceeds()
    }

    // See https://github.com/gradle/gradle-native/issues/1007
    @RequiresInstalledToolChain(ToolChainRequirement.SWIFTC_4_OR_OLDER)
    @ToBeFixedForConfigurationCache(because = "Task.getProject() during execution")
    def "produces manifest for Swift component with declared Swift language version"() {
        given:
        buildFile << """
            plugins {
                id 'swiftpm-export'
                id 'swift-library'
            }
            library.sourceCompatibility = SwiftVersion.SWIFT3
"""
        def lib = new SwiftLib()
        lib.writeToProject(testDirectory)

        when:
        run("generateSwiftPmManifest")

        then:
        file("Package.swift").text == """// swift-tools-version:4.0
//
// GENERATED FILE - do not edit
//
import PackageDescription

let package = Package(
    name: "test",
    products: [
        .library(name: "test", type: .dynamic, targets: ["Test"]),
    ],
    targets: [
        .target(
            name: "Test",
            path: ".",
            sources: [
                "src/main/swift/greeter.swift",
                "src/main/swift/multiply.swift",
                "src/main/swift/sum.swift",
            ]
        ),
    ],
    swiftLanguageVersions: [3]
)
"""
        swiftPmBuildSucceeds()
    }

    @ToBeFixedForConfigurationCache(because = "Task.getProject() during execution")
    def "honors customizations to Swift module name"() {
        given:
        createDirs("lib1", "lib2")
        settingsFile << "include 'lib1', 'lib2'"
        buildFile << """
            plugins {
                id 'swiftpm-export'
                id 'swift-application'
            }
            subprojects {
                apply plugin: 'swift-library'
            }
            dependencies {
                implementation project(':lib1')
            }
            project(':lib1') {
                library {
                    module = 'Hello'
                    dependencies {
                        implementation project(':lib2')
                    }
                }
            }
            project(':lib2') {
                library {
                    module = 'Log'
                }
            }
"""
        def app = new SwiftAppWithLibraries()
        app.application.writeToProject(testDirectory)
        app.library.writeToProject(file("lib1"))
        app.logLibrary.writeToProject(file("lib2"))

        when:
        run("generateSwiftPmManifest")

        then:
        file("Package.swift").text == """// swift-tools-version:4.0
//
// GENERATED FILE - do not edit
//
import PackageDescription

let package = Package(
    name: "test",
    products: [
        .executable(name: "test", targets: ["Test"]),
        .library(name: "lib1", type: .dynamic, targets: ["Hello"]),
        .library(name: "lib2", type: .dynamic, targets: ["Log"]),
    ],
    targets: [
        .target(
            name: "Test",
            dependencies: [
                .target(name: "Hello"),
            ],
            path: ".",
            sources: [
                "src/main/swift/main.swift",
            ]
        ),
        .target(
            name: "Hello",
            dependencies: [
                .target(name: "Log"),
            ],
            path: "lib1",
            sources: [
                "src/main/swift/greeter.swift",
            ]
        ),
        .target(
            name: "Log",
            path: "lib2",
            sources: [
                "src/main/swift/log.swift",
            ]
        ),
    ]
)
"""
        swiftPmBuildSucceeds()
    }
}
