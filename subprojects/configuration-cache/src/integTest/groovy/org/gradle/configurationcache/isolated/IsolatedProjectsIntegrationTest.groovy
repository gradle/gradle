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
            task thing { }
        """

        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("thing")

        then:
        configurationCache.assertStateStored()
        outputContains(ISOLATED_PROJECTS_MESSAGE)
        outputDoesNotContain(CONFIGURATION_CACHE_MESSAGE)
        outputDoesNotContain("Configuration on demand is an incubating feature.")
        configured("settings", "root project")

        when:
        configurationCacheRun("thing")

        then:
        configurationCache.assertStateLoaded()
        outputContains(ISOLATED_PROJECTS_MESSAGE)
        outputDoesNotContain(CONFIGURATION_CACHE_MESSAGE)
        outputDoesNotContain("Configuration on demand is an incubating feature.")
        configured()
    }

    def "cannot disable configuration cache when option is enabled"() {
        buildFile """
            println "configuring project"
            task thing { }
        """

        when:
        configurationCacheFails("thing", "--no-configuration-cache")

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
        configurationCacheRun(":b:producer")

        then:
        result.assertTasksExecuted(":a:producer", ":b:producer")
        configured("settings", "root project", "project :b", "project :a")

        when:
        configurationCacheRun(":b:producer")

        then:
        result.assertTasksExecuted(":a:producer", ":b:producer")
        configured()

        when:
        configurationCacheRun("producer")

        then:
        result.assertTasksExecuted(":a:producer", ":b:producer", ":c:producer")
        configured("settings", "root project", "project :a", "project :b", "project :c")

        when:
        configurationCacheRun("producer")

        then:
        result.assertTasksExecuted(":a:producer", ":b:producer", ":c:producer")
        configured()
    }

    void configured(String... items) {
        def actual = output.readLines()
            .findAll { it.contains("configuring") }
            .collect { it.replace("configuring ", "") }
        assert actual == items.toList()
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
