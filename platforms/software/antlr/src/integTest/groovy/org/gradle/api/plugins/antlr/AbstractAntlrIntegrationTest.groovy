/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.plugins.antlr

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

abstract class AbstractAntlrIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        // So we can assert on which version of ANTLR is used at runtime
        executer.withArgument("-i")
        buildFile << """
            allprojects {
                apply plugin: 'java'
                ${mavenCentralRepository()}
                tasks.withType(JavaCompile) {
                    options.compilerArgs << "-proc:none"
                }
            }
            project(":grammar-builder") {
                apply plugin: "antlr"

                dependencies {
                    antlr '$antlrDependency'
                }
            }
            project(":grammar-user") {

                dependencies {
                    implementation project(":grammar-builder")
                }
            }
"""
        createDirs("grammar-builder", "grammar-user")
        settingsFile << """
            include 'grammar-builder'
            include 'grammar-user'
"""
    }

    abstract String getAntlrDependency()

    void assertAntlrVersion(int version) {
        assert output.contains("Processing with ANTLR $version")
    }
}
