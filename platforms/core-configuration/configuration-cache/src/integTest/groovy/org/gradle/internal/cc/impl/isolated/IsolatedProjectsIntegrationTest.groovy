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

package org.gradle.internal.cc.impl.isolated

import org.gradle.api.tasks.TasksWithInputsAndOutputs
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.ToBeImplemented

import static org.gradle.util.internal.TextUtil.escapeString

class IsolatedProjectsIntegrationTest extends AbstractIsolatedProjectsIntegrationTest implements TasksWithInputsAndOutputs {
    def "option also enables configuration cache"() {
        settingsFile << """
            println "configuring settings"
        """
        buildFile """
            println "configuring root project"
            task thing { }
        """

        when:
        isolatedProjectsRun("thing")

        then:
        fixture.assertStateStored {
            projectConfigured(":")
        }

        when:
        isolatedProjectsRun("thing")

        then:
        fixture.assertStateLoaded()
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

    def "can enable isolated projects via paths option #description"() {
        file("gradle.properties") << """
            org.gradle.unsafe.isolated-projects.enable.paths=${escapeString(optionFromTestDirectory(testDirectory))}
        """
        buildFile """
            def buildFeatures = gradle.services.get(org.gradle.api.configuration.BuildFeatures)
            println "isolatedProjects.active=" + buildFeatures.isolatedProjects.active.get()
        """

        when:
        run "help"

        then:
        outputContains("isolatedProjects.active=true")

        where:
        description           | optionFromTestDirectory
        "with root path"      | { td -> td.absolutePath }
        "with parent path"    | { td -> td.parentFile.absolutePath }
        "with multiple paths" | { td -> "${td.parentFile.file("some-other")} ; ${td.absolutePath}" }
    }

    def "isolated projects option takes precedence over enabling via paths option"() {
        file("gradle.properties") << """
            org.gradle.unsafe.isolated-projects.enable.paths=${escapeString(testDirectory.absolutePath)}
        """
        buildFile """
            def buildFeatures = gradle.services.get(org.gradle.api.configuration.BuildFeatures)
            println "isolatedProjects.active=" + buildFeatures.isolatedProjects.active.get()
        """

        when:
        run "help", "-Dorg.gradle.unsafe.isolated-projects=false"

        then:
        outputContains("isolatedProjects.active=false")
    }

    @ToBeImplemented("when Isolated Projects becomes incremental for task execution")
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
            // TODO:isolated desired behavior
//            projectsConfigured(":", ":b", ":a")
            projectsConfigured(":", ":b", ":a", ":c")
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
