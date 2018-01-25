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
import org.gradle.vcs.fixtures.GitFileRepository

class SwiftPackageManagerExportIntegrationTest extends AbstractIntegrationSpec {
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
    }

    def "produces manifest for single project Swift library"() {
        given:
        buildFile << """
            plugins { 
                id 'swiftpm-export' 
                id 'swift-library'
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
        .library(name: "Test", targets: ["Test"]),
    ],
    targets: [
        .target(name: "Test"),
    ]
)
"""
    }

    def "produces manifest for single project C++ library"() {
        given:
        buildFile << """
            plugins { 
                id 'swiftpm-export' 
                id 'cpp-library'
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
        .library(name: "test", targets: ["test"]),
    ],
    targets: [
        .target(name: "test"),
    ]
)
"""
    }

    def "produces manifest for multi-project Swift build"() {
        given:
        settingsFile << "include 'lib1', 'lib2'"
        buildFile << """
            plugins { 
                id 'swiftpm-export' 
                id 'swift-application' 
            }
            subprojects {
                apply plugin: 'swift-library'
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
        .executable(name: "Test", targets: ["Test"]),
        .library(name: "Lib1", targets: ["Lib1"]),
        .library(name: "Lib2", targets: ["Lib2"]),
    ],
    targets: [
        .target(name: "Test"),
        .target(name: "Lib1"),
        .target(name: "Lib2"),
    ]
)
"""
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
        .executable(name: "test", targets: ["test"]),
        .library(name: "lib1", targets: ["lib1"]),
        .library(name: "lib2", targets: ["lib2"]),
    ],
    targets: [
        .target(name: "test"),
        .target(name: "lib1"),
        .target(name: "lib2"),
    ]
)
"""
    }

    def "produces manifest for Swift component with source dependencies"() {
        given:
        def lib1Repo = GitFileRepository.init(testDirectory.file("repos/lib1"))
        def lib2Repo = GitFileRepository.init(testDirectory.file("repos/lib2"))

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
                api "test:lib1:1.0"
                implementation "test:lib2:1.0"
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
        .library(name: "Test", targets: ["Test"]),
    ],
    targets: [
        .target(name: "Test"),
    ]
)
"""
    }
}
