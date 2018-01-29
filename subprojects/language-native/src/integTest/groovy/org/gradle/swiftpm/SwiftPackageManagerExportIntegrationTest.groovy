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

class SwiftPackageManagerExportIntegrationTest extends AbstractSwiftPackageManagerExportIntegrationTest {

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
}
