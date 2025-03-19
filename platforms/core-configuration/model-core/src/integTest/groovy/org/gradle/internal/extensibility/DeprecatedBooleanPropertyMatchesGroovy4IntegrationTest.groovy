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

package org.gradle.internal.extensibility


import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.fixture.GroovyCoverage
import org.gradle.util.internal.GroovyDependencyUtil

@TargetCoverage({ GroovyCoverage.SINCE_4_0 })
class DeprecatedBooleanPropertyMatchesGroovy4IntegrationTest extends MultiVersionIntegrationSpec {
    def "Groovy 4 does not recognize Boolean isProperty() as property"() {
        buildFile << """
            plugins {
                id 'groovy'
                id 'application'
            }

            ${mavenCentralRepository()}

            dependencies {
                implementation '${GroovyDependencyUtil.groovyModuleDependency("groovy", version.toString())}'
            }

            application {
                mainClass = 'Main'
            }
        """

        file("src/main/groovy/Main.groovy") << """
            @groovy.transform.CompileStatic
            class Main {
                static void main(String[] args) {
                    assert new Main().property
                }

                Boolean isProperty() { return Boolean.TRUE }
            }
        """

        expect:
        fails("compileGroovy")
    }
}
