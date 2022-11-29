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
    def "nags when task action uses #expression and feature preview is enabled"() {
        settingsFile """
            enableFeaturePreview 'STABLE_CONFIGURATION_CACHE'
        """
        buildFile """
            task broken {
                doLast {
                    $expression
                }
            }
        """

        when:
        fails("broken")

        then:
        failureHasCause("Invocation of ${invocation} at execution time is unsupported with the STABLE_CONFIGURATION_CACHE feature preview.")

        where:
        expression         | invocation              | docSection
        "project"          | "Task.project"          | "task_project"
        "taskDependencies" | "Task.taskDependencies" | "task_dependencies"
    }
}
