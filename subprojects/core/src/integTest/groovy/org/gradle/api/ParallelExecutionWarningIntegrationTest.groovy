/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.ProjectLifecycleFixture
import org.junit.Rule
import spock.lang.IgnoreIf

@IgnoreIf({ GradleContextualExecuter.parallel })
class ParallelExecutionWarningIntegrationTest extends AbstractIntegrationSpec {

    private static final String WARNING_MESSAGE = "Using the 'clean' task in combination with parallel execution may lead to unexpected runtime behavior."

    @Rule
    ProjectLifecycleFixture fixture = new ProjectLifecycleFixture(executer, temporaryFolder)

    def "does not present warning message for single project build"() {
        given:
        buildFile << basePluginAndBasicTask()

        when:
        run(*tasks)

        then:
        fixture.assertProjectsConfigured(":")
        output.count(WARNING_MESSAGE) == 0

        where:
        tasks << [
            ['foo'],
            ['clean', 'foo'],
            ['foo'],
            ['clean', 'foo'],
            ['foo', '--parallel'],
            ['clean', 'foo', '--parallel'],
            ['clean', 'foo', '--parallel', '--configure-on-demand']
        ]
    }

    def "may present warning message for multi-project build"() {
        given:
        settingsFile << "include 'a', 'b'"
        buildFile << """
            subprojects {
                ${basePluginAndBasicTask()}
            }
        """

        when:
        run(*tasks)

        then:
        fixture.assertProjectsConfigured(":", ":a", ":b")
        (output.count(WARNING_MESSAGE) == 1) == warningEmittedOnce

        where:
        tasks                                                   | warningEmittedOnce
        ['foo']                                                 | false
        ['clean', 'foo']                                        | false
        ['foo', '--parallel']                                   | false
        ['foo', '--configure-on-demand']                        | false
        ['clean', 'foo']                                        | false
        ['clean', 'foo', '--configure-on-demand']               | false
        ['foo', 'clean', '--configure-on-demand']               | false
        ['clean', 'foo', '--parallel']                          | true
        ['foo', 'clean', '--parallel']                          | true
        ['clean', 'foo', '--parallel', '--configure-on-demand'] | true
        ['foo', 'clean', '--parallel', '--configure-on-demand'] | true
    }

    static String basePluginAndBasicTask() {
        """
            apply plugin: 'base'

            task foo
        """
    }
}
