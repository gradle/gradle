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


import spock.lang.Ignore

class SwiftPackageManagerExportIntegrationTest extends AbstractSwiftPackageManagerExportIntegrationTest {

    // Swift Package Manager returns an error code when no target are available:
    // See https://github.com/gradle/gradle-native/issues/1006
    @Ignore
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

    def "can configure the location of the generated manifest file"() {
        given:
        buildFile << """
            plugins { 
                id 'swiftpm-export' 
                id 'swift-library'
            }
            tasks.generateSwiftPmManifest.manifestFile = file('generated/thing.swift')
"""

        when:
        run("generateSwiftPmManifest")

        then:
        file("generated/thing.swift").assertIsFile()
        file("Package.swift").assertDoesNotExist()
    }

    def "can exclude certain products from the generated file"() {
        given:
        settingsFile << "include 'lib1', 'lib2', 'app'"
        buildFile << """
            plugins { 
                id 'swiftpm-export' 
            }
            
            afterEvaluate {
                generateSwiftPmManifest.package.get().products.removeAll { p -> p.name == 'app' }
            }
            
            project(':lib1') { apply plugin: 'swift-library' }
            project(':lib2') { apply plugin: 'swift-library' }
            project(':app') { 
                apply plugin: 'swift-application' 
                dependencies {
                    implementation project(':lib1')
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
        .library(name: "lib1", type: .dynamic, targets: ["Lib1"]),
        .library(name: "lib2", type: .dynamic, targets: ["Lib2"]),
    ],
    targets: [
        .target(
            name: "App",
            dependencies: [
                .target(name: "Lib1"),
            ],
            path: "app",
            sources: [
            ]
        ),
        .target(
            name: "Lib1",
            path: "lib1",
            sources: [
            ]
        ),
        .target(
            name: "Lib2",
            path: "lib2",
            sources: [
            ]
        ),
    ]
)
"""
    }
}
