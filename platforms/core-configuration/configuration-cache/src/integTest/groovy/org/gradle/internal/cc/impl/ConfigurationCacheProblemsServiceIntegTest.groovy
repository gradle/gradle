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

package org.gradle.configurationcache

import org.gradle.api.problems.Severity

class ConfigurationCacheProblemsServiceIntegTest extends AbstractConfigurationCacheIntegrationTest {

    public static final String REGISTRATION_UNSUPPORTED = 'validation:configuration-cache-registration-of-listener-on-gradle-buildfinished-is-unsupported'

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
        configurationCacheFails 'run'

        then:
        verifyAll(receivedProblem(0)) {
            fqid == REGISTRATION_UNSUPPORTED
            contextualLabel == "registration of listener on 'Gradle.buildFinished' is unsupported"
            definition.severity == Severity.ERROR
            definition.documentationLink != null
            locations.size() == 2
            locations[0].path == "build file 'build.gradle'"
            locations[0].line == 2
            locations[1].path == "build file '${buildFile.absolutePath}'"
            locations[1].line == 2
        }

        when:
        configurationCacheRunLenient 'run'

        then:
        verifyAll(receivedProblem(0)) {
            fqid == REGISTRATION_UNSUPPORTED
            contextualLabel == "registration of listener on 'Gradle.buildFinished' is unsupported"
            definition.severity == Severity.WARNING
            definition.documentationLink != null
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

        then:
        verifyAll(receivedProblem(0)) {
            fqid == REGISTRATION_UNSUPPORTED
            contextualLabel == "registration of listener on 'Gradle.buildFinished' is unsupported"
            definition.severity == Severity.WARNING
        }
    }

    def "notCompatibleWithConfigurationCache task problems are reported as Advice"() {
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
        verifyAll(receivedProblem(0)) {
            fqid == 'validation:configuration-cache-invocation-of-task-project-at-execution-time-is-unsupported'
            contextualLabel == "invocation of 'Task.project' at execution time is unsupported."
            definition.severity == Severity.ADVICE
        }
    }
}
