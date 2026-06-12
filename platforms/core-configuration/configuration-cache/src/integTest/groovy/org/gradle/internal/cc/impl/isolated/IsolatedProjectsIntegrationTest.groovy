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

class IsolatedProjectsIntegrationTest extends AbstractIsolatedProjectsIntegrationTest implements TasksWithInputsAndOutputs {

    def "disabled by default"() {
        given:
        withIpStatusTask()

        when:
        run "ipStatus"

        then:
        outputContains("isolatedProjects.requested=null")
        outputContains("isolatedProjects.active=false")
    }

    def "can enable via property"() {
        given:
        withIpStatusTask()

        when:
        run "ipStatus", "-Dorg.gradle.isolated-projects=true"

        then:
        outputContains("isolatedProjects.requested=true")
        outputContains("isolatedProjects.active=true")
    }

    def "can enable via deprecated property"() {
        given:
        withIpStatusTask()

        when:
        run "ipStatus", "-Dorg.gradle.unsafe.isolated-projects=true"

        then:
        outputContains("isolatedProjects.requested=true")
        outputContains("isolatedProjects.active=true")
    }

    def "can enable via gradle.properties"() {
        given:
        withIpStatusTask()
        file("gradle.properties") << "org.gradle.isolated-projects=true"

        when:
        run "ipStatus"

        then:
        outputContains("isolatedProjects.requested=true")
        outputContains("isolatedProjects.active=true")
    }

    def "can enable via --isolated-projects"() {
        given:
        withIpStatusTask()

        when:
        run "ipStatus", "--isolated-projects"

        then:
        outputContains("isolatedProjects.requested=true")
        outputContains("isolatedProjects.active=true")
    }

    def "can disable via --no-isolated-projects"() {
        given:
        withIpStatusTask()

        when:
        run "ipStatus", "--no-isolated-projects"

        then:
        outputContains("isolatedProjects.requested=false")
        outputContains("isolatedProjects.active=false")
    }

    def "build option takes precedence over property when disabling"() {
        given:
        withIpStatusTask()

        when:
        run "ipStatus", "--no-isolated-projects", "-Dorg.gradle.isolated-projects=true"

        then:
        outputContains("isolatedProjects.requested=false")
        outputContains("isolatedProjects.active=false")
    }

    def "build option takes precedence over property when enabling"() {
        given:
        withIpStatusTask()

        when:
        run "ipStatus", "--isolated-projects", "-Dorg.gradle.isolated-projects=false"

        then:
        outputContains("isolatedProjects.requested=true")
        outputContains("isolatedProjects.active=true")
    }

    def "diagnostics option is accepted under both property names"() {
        when:
        run "help", "-q",
            "-Dorg.gradle.internal.operations.verbose.parameters=true",
            "-Dorg.gradle.isolated-projects=true",
            "-D${propertyName}=true"

        then:
        result.getOutputLineThatContains("Operational build model parameters:").contains("isolatedProjectsDiagnostics=true")

        where:
        propertyName << ["org.gradle.isolated-projects.diagnostics", "org.gradle.unsafe.isolated-projects.diagnostics"]
    }

    def "dangerously-ignore-problems option is accepted under both property names"() {
        when:
        run "help", "-q",
            "-Dorg.gradle.internal.operations.verbose.parameters=true",
            "-Dorg.gradle.isolated-projects=true",
            "-D${propertyName}=true"

        then:
        result.getOutputLineThatContains("Operational build model parameters:").contains("isolatedProjectsDangerouslyIgnoreProblems=true")

        where:
        propertyName << ["org.gradle.isolated-projects.dangerously-ignore-problems", "org.gradle.unsafe.isolated-projects.dangerously-ignore-problems"]
    }

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
        failure.assertHasDescription("Configuration Cache cannot be disabled when Isolated Projects is enabled.")
    }

    def "diagnostics mode continues execution upon encountering violations"() {
        settingsFile """
            include(":sub")
        """

        buildFile "sub/build.gradle", """
            rootProject.tasks

            rootProject.configurations
        """

        when:
        isolatedProjectsDiagnosticsFails("help")

        then:
        problems.assertFailureHasProblems(failure) {
            withProblem("Build file '${relativePath('sub/build.gradle')}': line 2: Project ':sub' cannot access 'Project.tasks' functionality on another project ':'")
            withProblem("Build file '${relativePath('sub/build.gradle')}': line 4: Project ':sub' cannot access 'Project.configurations' functionality on another project ':'")
            totalProblemsCount = 2
            problemsWithStackTraceCount = 2
        }
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
        result.assertTasksScheduled(":a:producer", ":b:producer")
        fixture.assertStateStored {
            // TODO:isolated desired behavior
//            projectsConfigured(":", ":b", ":a")
            projectsConfigured(":", ":b", ":a", ":c")
        }

        when:
        isolatedProjectsRun(":b:producer")

        then:
        result.assertTasksScheduled(":a:producer", ":b:producer")
        fixture.assertStateLoaded()

        when:
        isolatedProjectsRun("producer")

        then:
        result.assertTasksScheduled(":a:producer", ":b:producer", ":c:producer")
        fixture.assertStateStored {
            projectsConfigured(":", ":b", ":a", ":c")
        }

        when:
        isolatedProjectsRun("producer")

        then:
        result.assertTasksScheduled(":a:producer", ":b:producer", ":c:producer")
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

    void withIpStatusTask() {
        //noinspection UnnecessaryQualifiedReference
        buildFile """
            def buildFeatures = gradle.services.get(org.gradle.api.configuration.BuildFeatures)
            tasks.register("ipStatus") {
                doLast {
                    println "isolatedProjects.requested=" + buildFeatures.isolatedProjects.requested.getOrNull()
                    println "isolatedProjects.active=" + buildFeatures.isolatedProjects.active.get()
                }
            }
        """
    }
}
