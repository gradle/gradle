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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.language.swift.SwiftPmRunner
import org.gradle.nativeplatform.fixtures.AvailableToolChains
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibraries
import org.gradle.nativeplatform.fixtures.app.CppLib
import org.gradle.nativeplatform.fixtures.app.SwiftAppWithLibraries
import org.gradle.nativeplatform.fixtures.app.SwiftLib
import org.gradle.vcs.fixtures.GitFileRepository

@RequiresInstalledToolChain(ToolChainRequirement.SWIFTC_4)
class SwiftPackageManagerExportIntegrationTest extends AbstractIntegrationSpec {
    def swiftc = AvailableToolChains.getToolChain(ToolChainRequirement.SWIFTC_4)

    def setup() {
        settingsFile << """rootProject.name = 'test'
"""
    }

    def "produces manifest for build with no native components"() {
        given:
        settingsFile << "include 'lib1', 'lib2'"
        buildFile << """
            plugins { 
                id 'swiftpm-export' 
            }
"""

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
    ],
    targets: [
    ]
)
"""
        swiftPmBuildSucceeds()
    }

    def "produces manifest for single project Swift library"() {
        given:
        buildFile << """
            plugins { 
                id 'swiftpm-export' 
                id 'swift-library'
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
        .library(name: "test", targets: ["Test"]),
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

    def "produces manifest for single project C++ library"() {
        given:
        buildFile << """
            plugins { 
                id 'swiftpm-export' 
                id 'cpp-library'
            }
"""
        def lib = new CppLib()
        lib.sources.writeToProject(testDirectory)
        lib.privateHeaders.writeToSourceDir(testDirectory.file("src/main/cpp"))
        lib.publicHeaders.writeToProject(testDirectory)

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
        .library(name: "test", targets: ["test"]),
    ],
    targets: [
        .target(
            name: "test",
            path: ".",
            sources: [
                "src/main/cpp/greeter.cpp",
                "src/main/cpp/sum.cpp",
            ],
            publicHeadersPath: "src/main/public"
        ),
    ]
)
"""
        swiftPmBuildSucceeds()
    }

    def "produces manifest for multi-project Swift build"() {
        given:
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
        .library(name: "hello", targets: ["Hello"]),
        .library(name: "log", targets: ["Log"]),
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

    def "produces manifest for multi project C++ build"() {
        given:
        settingsFile << "include 'lib1', 'lib2'"
        buildFile << """
            plugins { 
                id 'swiftpm-export' 
                id 'cpp-application' 
            }
            subprojects {
                apply plugin: 'cpp-library'
            }
            dependencies {
                implementation project(':lib1')
            }
            project(':lib1') {
                dependencies {
                    implementation project(':lib2')
                }
            }
"""
        def app = new CppAppWithLibraries()
        app.main.writeToProject(testDirectory)
        app.greeterLib.sources.writeToProject(file("lib1"))
        app.greeterLib.publicHeaders.writeToProject(file("lib1"))
        app.greeterLib.privateHeaders.writeToSourceDir(file("lib1/src/main/cpp"))
        app.loggerLib.writeToProject(file("lib2"))

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
        .executable(name: "test", targets: ["test"]),
        .library(name: "lib1", targets: ["lib1"]),
        .library(name: "lib2", targets: ["lib2"]),
    ],
    targets: [
        .target(
            name: "test",
            dependencies: [
                .target(name: "lib1"),
            ],
            path: ".",
            sources: [
                "src/main/cpp/main.cpp",
            ]
        ),
        .target(
            name: "lib1",
            dependencies: [
                .target(name: "lib2"),
            ],
            path: "lib1",
            sources: [
                "src/main/cpp/greeter.cpp",
            ],
            publicHeadersPath: "src/main/public"
        ),
        .target(
            name: "lib2",
            path: "lib2",
            sources: [
                "src/main/cpp/logger.cpp",
            ],
            publicHeadersPath: "src/main/public"
        ),
    ]
)
"""
        swiftPmBuildSucceeds()
    }

    def "produces manifest for Swift component with source dependencies"() {
        given:
        def lib1Repo = GitFileRepository.init(testDirectory.file("repos/lib1"))
        lib1Repo.file("build.gradle") << """
            plugins {
                id 'swift-library'
                id 'swiftpm-export'
            }
        """
        lib1Repo.file("settings.gradle") << "rootProject.name = 'lib1'"
        lib1Repo.file("src/main/swift/Lib1.swift") << """
            public class Lib1 {
                public class func thing() { }
            }
"""
        executer.inDirectory(lib1Repo.workTree).withTasks("generateSwiftPmManifest").run()
        lib1Repo.commit("v1")
        lib1Repo.createLightWeightTag("1.0.0")

        and:
        def lib2Repo = GitFileRepository.init(testDirectory.file("repos/lib2"))
        lib2Repo.file("build.gradle") << """
            plugins {
                id 'swift-library'
                id 'swiftpm-export'
            }
        """
        lib2Repo.file("settings.gradle") << "rootProject.name = 'lib2'"
        lib2Repo.file("src/main/swift/Lib2.swift") << """
            public class Lib2 {
                public class func thing() { }
            }
        """
        executer.inDirectory(lib2Repo.workTree).withTasks("generateSwiftPmManifest").run()
        lib2Repo.commit("v2")
        lib2Repo.createLightWeightTag("2.0.0")

        and:
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("test:lib1") {
                        from(GitVersionControlSpec) {
                            url = uri('${lib1Repo.url}')
                        }
                    }
                    withModule("test:lib2") {
                        from(GitVersionControlSpec) {
                            url = uri('${lib2Repo.url}')
                        }
                    }
                }
            }
"""
        buildFile << """
            plugins {
                id 'swift-library'
                id 'swiftpm-export'
            }
            dependencies {
                api "test:lib1:1.0.0"
                implementation "test:lib2:2.0.0"
            }
"""
        file("src/main/swift/Lib.swift") << """
            import Lib1
            import Lib2
            class Lib {
                init() {
                    Lib1.thing()
                    Lib2.thing()
                }
            }
        """

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
        .library(name: "test", targets: ["Test"]),
    ],
    dependencies: [
        .package(url: "repos/lib2", from: "2.0.0"),
        .package(url: "repos/lib1", from: "1.0.0"),
    ],
    targets: [
        .target(
            name: "Test",
            dependencies: [
                .product(name: "lib2"),
                .product(name: "lib1"),
            ],
            path: ".",
            sources: [
                "src/main/swift/Lib.swift",
            ]
        ),
    ]
)
"""
        swiftPmBuildSucceeds()

        cleanup:
        lib1Repo?.close()
        lib2Repo?.close()
    }

    void swiftPmBuildSucceeds() {
        SwiftPmRunner.create(swiftc)
            .withProjectDir(testDirectory)
            .withArguments("build")
            .build()
    }

}
