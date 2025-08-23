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

package org.gradle.api.plugins.instrumentation

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

@Requires(IntegTestPreconditions.IsEmbeddedExecutor)
class InstrumentClassesSoftwareFeatureIntegrationTest extends AbstractIntegrationSpec {
    def "can apply the InstrumentClassesSoftwareFeaturePlugin"() {
        given:
        settingsFile << """
            plugins {
                id 'java-ecosystem'
                id 'instrumentation-ecosystem'
            }
        """
        file('build.gradle.dcl') << """
            javaLibrary {
                sources {
                    javaSources("main") {
                        instrument {
                            configFile = layout.projectDirectory.file("config/instrumentation/instrumentation.xml")
                        }
                    }
                }
            }
        """

        when:
        run "tasks"

        then:
        outputContains("instrumentMainClasses")
        outputDoesNotContain("instrumentTestClasses")
    }
}
