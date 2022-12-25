/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.base.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class LifecycleBasePluginIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
        apply plugin:org.gradle.language.base.plugins.LifecycleBasePlugin
        """
    }

    def "fails when applied in build with #taskName"() {
        buildFile << """

        task $taskName {
            doLast {
                println "custom $taskName task"
            }
        }
        """

        when:
        fails(taskName)

        then:
        failure.assertHasCause("Cannot add task '$taskName' as a task with that name already exists.")
        where:
        taskName << ["check", "clean", "build", "assemble"]
    }

    def "can attach custom task as dependency to lifecycle task - #taskName"() {
        when:
        buildFile << """
            task myTask {}
            ${taskName}.dependsOn myTask
        """

        then:
        succeeds(taskName)
        executed(":myTask")

        where:
        taskName << ["check", "build"]
    }

    def "clean task honors changes to build dir location"() {
        buildFile << """
            buildDir = 'target'
        """
        def buildDir = file("build")
        buildDir.mkdirs()
        def targetDir = file("target")
        targetDir.mkdirs()

        when:
        succeeds("clean")

        then:
        buildDir.directory
        !targetDir.exists()
    }
}
