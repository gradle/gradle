/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.integtests


import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

class TaskActionIntegrationTest extends AbstractIntegrationSpec {
    @UnsupportedWithConfigurationCache(because = "tests unsupported behaviour")
    def "nags when task action uses Task.project and feature preview is enabled"() {
        settingsFile """
            enableFeaturePreview 'STABLE_CONFIGURATION_CACHE'
        """
        buildFile """
            task broken {
                doLast {
                    project
                }
            }
        """

        when:
        executer.expectDocumentedDeprecationWarning("Invocation of Task.project at execution time has been deprecated. This will fail with an error in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#task_project")
        succeeds("broken")

        then:
        noExceptionThrown()
    }

    @UnsupportedWithConfigurationCache(because = "tests unsupported behaviour")
    def "fails when task action uses Task.taskDependencies and feature preview is enabled"() {
        settingsFile """
            enableFeaturePreview 'STABLE_CONFIGURATION_CACHE'
        """
        buildFile """
            task broken {
                doLast {
                    taskDependencies
                }
            }
        """

        when:
        fails("broken")

        then:
        failureHasCause("Invocation of Task.taskDependencies at execution time is unsupported with the STABLE_CONFIGURATION_CACHE feature preview.")
    }
}
