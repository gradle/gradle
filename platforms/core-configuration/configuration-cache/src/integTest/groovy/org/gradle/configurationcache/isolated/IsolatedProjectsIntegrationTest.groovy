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

package org.gradle.configurationcache.isolated

import org.gradle.api.tasks.TasksWithInputsAndOutputs
import org.gradle.test.fixtures.file.TestFile

class IsolatedProjectsIntegrationTest extends AbstractIsolatedProjectsIntegrationTest implements TasksWithInputsAndOutputs {
    def "option also enables configuration cache"() {
        settingsFile << """
            println "configuring settings"
        """
        buildFile """
            println "configuring root project"
            def startParameter = gradle.startParameter
            tasks.register("thing") {
                doLast {
                    println "isConfigurationCacheRequested=" + startParameter.isConfigurationCacheRequested()
                }
            }
        """

        when:
        isolatedProjectsRun("thing")
        then:
        fixture.assertStateStored {
            projectConfigured(":")
        }
        outputContains("isConfigurationCacheRequested=true")

        when:
        isolatedProjectsRun("thing")
        then:
        fixture.assertStateLoaded()
        outputContains("isConfigurationCacheRequested=true")
    }

    def "cannot disable configuration cache when option is enabled"() {
        buildFile """
            println "configuring project"
            task thing { }
        """

        when:
        isolatedProjectsFails("thing", "--no-configuration-cache")

        then:
        failure.assertHasDescription("The configuration cache cannot be disabled when isolated projects is enabled.")
    }

    def "projects are configured on demand"() {
        settingsFile << """
            println "configuring settings"
            include "a", "b", "c"
        """
        buildFile("""
            println "configuring root project"
        """)
        customType(file("a"))
        customType(file("b")) << """
            dependencies {
                implementation project(':a')
            }
        """
        customType(file("c")) << """
            dependencies {
                implementation project(':b')
            }
        """

        when:
        isolatedProjectsRun(":b:producer")

        then:
        result.assertTasksExecuted(":a:producer", ":b:producer")
        fixture.assertStateStored {
            projectsConfigured(":", ":b", ":a")
        }

        when:
        isolatedProjectsRun(":b:producer")

        then:
        result.assertTasksExecuted(":a:producer", ":b:producer")
        fixture.assertStateLoaded()

        when:
        isolatedProjectsRun("producer")

        then:
        result.assertTasksExecuted(":a:producer", ":b:producer", ":c:producer")
        fixture.assertStateStored {
            projectsConfigured(":", ":b", ":a", ":c")
        }

        when:
        isolatedProjectsRun("producer")

        then:
        result.assertTasksExecuted(":a:producer", ":b:producer", ":c:producer")
        fixture.assertStateLoaded()
    }

    TestFile customType(TestFile dir) {
        def buildFile = dir.file("build.gradle")
        taskTypeWithInputFileCollection(buildFile)
        buildFile << """
            println "configuring project \$project.path"
            configurations {
                implementation {
                    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, "custom"))
                }
            }
            task producer(type: InputFilesTask) {
                outFile = layout.buildDirectory.file("out.txt")
                inFiles.from(configurations.implementation)
            }
            artifacts {
                implementation producer.outFile
            }
        """
        return buildFile
    }
}
