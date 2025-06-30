/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.java.dependencies

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class JavaFeatureRegressionIntegrationTest extends AbstractIntegrationSpec {
    @Issue("https://github.com/gradle/gradle/issues/34058")
    def "afterEvaluate called in configurations.all triggered from a create called from a lazy container works"() {
        buildFile << """
            plugins {
                id("java-library")
            }
            // stand in for Nebula resolution rules plugin
            configurations.all { conf ->
                project.afterEvaluate {
                    println("hello " + conf.name)
                }
            }
            java.withSourcesJar()
        """
        expect:
        succeeds("help")
        output.contains("hello sourcesElements")
    }
}
