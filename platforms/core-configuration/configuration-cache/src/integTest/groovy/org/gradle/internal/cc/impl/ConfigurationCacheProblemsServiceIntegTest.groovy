/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.cc.impl


import org.gradle.api.problems.Severity
import org.gradle.internal.code.Locality
import org.gradle.initialization.StartParameterBuildOptions

class ConfigurationCacheProblemsServiceIntegTest extends AbstractConfigurationCacheIntegrationTest {

    public static final String UNSUPPORTED_TYPE = "Gradle:Configuration Cache:Type unsupported for serialization"
    public static final String REGISTRATION_UNSUPPORTED = "Gradle:Configuration Cache:Unsupported build listener registration"

    @Override
    def setup() {
        enableProblemsApiCheck()
    }

    def "problems are reported through the Problems API"() {
        given:
        buildFile """
            gradle.buildFinished { }

            task run
        """

        when:
        configurationCacheRunLenient 'run'

        then:
        problems.assertResultHasProblems(result) {
            totalProblemsCount = 1
            withUniqueProblems(
                "Build file 'build.gradle': line 2: registration of listener on 'Gradle.buildFinished' is unsupported")
            problemsWithStackTraceCount = 1
        }

        and:
        verifyAll(findReceivedProblem { it.fqid == REGISTRATION_UNSUPPORTED }) {
            contextualLabel == "Registration of listener on 'Gradle.buildFinished' is unsupported"
            definition.severity == Severity.WARNING
            definition.documentationLink.url.endsWith("/userguide/configuration_cache_requirements.html#config_cache:requirements:build_listeners")
            originLocations.size() == 1
            originLocations[0].path == "build file 'build.gradle'"
            originLocations[0].line == 2
            contextualLocations.empty
            additionalData.asMap.trace == "build file 'build.gradle': line 2"
        }

        when:
        configurationCacheRunLenient 'run', "-D${StartParameterBuildOptions.ConfigurationCacheRecreateOption.PROPERTY_NAME}=true"

        then:
        verifyAll(findReceivedProblem { it.fqid == REGISTRATION_UNSUPPORTED }) {
            fqid == REGISTRATION_UNSUPPORTED
            contextualLabel == "Registration of listener on 'Gradle.buildFinished' is unsupported"
            definition.severity == Severity.WARNING
            definition.documentationLink.url.endsWith("/userguide/configuration_cache_requirements.html#config_cache:requirements:build_listeners")
            originLocations[0].path == "build file 'build.gradle'"
            originLocations[0].line == 2
            contextualLocations.empty
            additionalData.asMap.trace == "build file 'build.gradle': line 2"

        }
    }

    def "max problems are still reported as warnings"() {
        given:
        buildFile """
            gradle.buildFinished { }

            task run
        """

        when:
        configurationCacheFails WARN_PROBLEMS_CLI_OPT, "-D$MAX_PROBLEMS_GRADLE_PROP=0", 'run'
        consumeWarnModeProblem()

        then:
        verifyAll(findReceivedProblem { it.fqid == REGISTRATION_UNSUPPORTED }) {
            contextualLabel == "Registration of listener on 'Gradle.buildFinished' is unsupported"
            definition.severity == Severity.WARNING
        }
    }

    def "notCompatibleWithConfigurationCache task problems are reported as Warning"() {
        given:
        buildFile """
            task run {
                notCompatibleWithConfigurationCache("because")
                doLast {
                    println(project.name)
                }
            }
        """

        when:
        configurationCacheRun("run")

        then:
        verifyAll(receivedProblem) {
            fqid == "Gradle:Configuration Cache:Project accessed at execution time"
            contextualLabel == "Invocation of 'Task.project' at execution time is unsupported with the configuration cache."
            definition.severity == Severity.WARNING
        }
    }

    def "a problem in a build script has the script line as its origin trace"() {
        given:
        buildFile """
            gradle.buildFinished { }
            task run
        """

        when:
        configurationCacheRunLenient 'run'

        then:
        def problem = findReceivedProblem { it.fqid == REGISTRATION_UNSUPPORTED }
        problem.originTraces*.frames*.displayName == [[buildFile.absolutePath + ":2"]]
        problem.originTraces[0].frames[0].locality == Locality.LOCAL

        and: "the flat location still carries the display name, as before"
        problem.originLocations[0].path == "build file 'build.gradle'"
    }

    def "a serialization problem has the path through the object graph as its origin trace (UC5)"() {
        given:
        buildFile """
            class AcmeService {
                Thread client = Thread.currentThread()
            }

            abstract class AcmeTask extends DefaultTask {
                private final AcmeService service = new AcmeService()

                @TaskAction
                void run() {}
            }

            tasks.register("acme", AcmeTask)
        """

        when:
        configurationCacheRunLenient 'acme'

        then: "the problem is found once when storing and once when loading, each with the same path"
        def stored = findReceivedProblem { it.contextualLabel.startsWith("Cannot serialize") }
        def loaded = findReceivedProblem { it.contextualLabel.startsWith("Cannot deserialize") }
        [stored, loaded].each { problem ->
            assert problem.fqid == "Gradle:Configuration Cache:Type unsupported for serialization"
            assert problem.originTraces*.frames*.displayName == [["field client", "bean AcmeService", "field service", "class AcmeTask", "task :acme"]]
            // Today's text form keeps only the user code that contains the state, not the path to it
            assert problem.additionalData.asMap.trace == "task `:acme` of type `AcmeTask`"
            // Spec Q7: Configuration Cache notices the problem outside any task, so detection is the build alone
            assert problem.detectionTrace.frames*.displayName == ["build :"]
        }
    }

    def "each field that can't be serialized is a problem of its own"() {
        given:
        def fields = (1..5).collect { "private final Thread thread$it = Thread.currentThread()" }.join("\n")
        buildFile """
            abstract class AcmeTask extends DefaultTask {
                $fields

                @TaskAction
                void run() {}
            }

            tasks.register("acme", AcmeTask)
        """

        when:
        configurationCacheRunLenient 'acme'

        then: "one per field, when storing and when loading"
        outputContains("10 problems were found storing the configuration cache, 2 of which seem unique.")
        def received = collectedProblems.findAll { it.fqid == UNSUPPORTED_TYPE }
        received.size() == 10
        received.collect { it.originTraces[0].frames[0].displayName }.toSet() == (1..5).collect { "field thread$it".toString() }.toSet()
        ignoreCleanupAssertions()
    }

    def "the same field in many tasks of one type is one problem"() {
        given:
        buildFile """
            abstract class AcmeTask extends DefaultTask {
                private final Thread thread = Thread.currentThread()

                @TaskAction
                void run() {}
            }

            (1..5).each { tasks.register("acme\$it", AcmeTask) }
            tasks.register("all") { dependsOn tasks.withType(AcmeTask) }
        """

        when:
        configurationCacheRunLenient 'all'

        then: "Configuration Cache counts every task; the field is one thing to fix, reported once when storing and once when loading"
        outputContains("10 problems were found storing the configuration cache")
        def received = collectedProblems.findAll { it.fqid == UNSUPPORTED_TYPE }
        received.size() == 2
        received.every { it.originTraces[0].frames*.displayName.take(2) == ["field thread", "class AcmeTask"] }
        ignoreCleanupAssertions()
    }

    def "more distinct problems of one kind than the Problems API limit are cut off"() {
        given:
        def fields = (1..20).collect { "private final Thread thread$it = Thread.currentThread()" }.join("\n")
        buildFile """
            abstract class AcmeTask extends DefaultTask {
                $fields

                @TaskAction
                void run() {}
            }

            tasks.register("acme", AcmeTask)
        """

        when:
        configurationCacheRunLenient 'acme'

        then: "Configuration Cache reports all 40; the Problems API stops at its limit per problem id"
        outputContains("40 problems were found storing the configuration cache, 2 of which seem unique.")
        collectedProblems.count { it.fqid == UNSUPPORTED_TYPE } == 15
        ignoreCleanupAssertions()
    }
}
