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
import spock.lang.Unroll

@IgnoreIf({ GradleContextualExecuter.parallel })
class ParallelExecutionWarningIntegrationTest extends AbstractIntegrationSpec {

    private static final String WARNING_MESSAGE = "Using the 'clean' task in combination with parallel execution may lead to unexpected runtime behavior."

    @Rule
    ProjectLifecycleFixture fixture = new ProjectLifecycleFixture(executer, temporaryFolder)

    @Unroll
    def "does not present warning message for single project build and tasks #tasks"() {
        given:
        buildFile << basePluginAndBasicTask()

        for (int i = 0; i < expectedIncubationWarnings; i++) {
            executer.expectIncubationWarning()
        }

        when:
        run(*tasks)

        then:
        fixture.assertProjectsConfigured(":")
        output.count(WARNING_MESSAGE) == 0

        where:
        tasks                                                   | expectedIncubationWarnings
        ['foo']                                                 | 0
        ['clean', 'foo']                                        | 0
        ['foo']                                                 | 0
        ['clean', 'foo']                                        | 0
        ['foo', '--parallel']                                   | 1
        ['clean', 'foo', '--parallel']                          | 1
        ['clean', 'foo', '--parallel', '--configure-on-demand'] | 1
    }

    @Unroll
    def "may present warning message for multi-project build and tasks #tasks"() {
        given:
        settingsFile << "include 'a', 'b'"
        buildFile << """
            subprojects {
                ${basePluginAndBasicTask()}
            }
        """

        for (int i = 0; i < expectedIncubationWarnings; i++) {
            executer.expectIncubationWarning()
        }

        when:
        run(*tasks)

        then:
        fixture.assertProjectsConfigured(":", ":a", ":b")
        (output.count(WARNING_MESSAGE) == 1) == warningEmittedOnce

        where:
        tasks                                                   | warningEmittedOnce | expectedIncubationWarnings
        ['foo']                                                 | false              | 0
        ['clean', 'foo']                                        | false              | 0
        ['foo', '--parallel']                                   | false              | 1
        ['foo', '--configure-on-demand']                        | false              | 1
        ['clean', 'foo']                                        | false              | 0
        ['clean', 'foo', '--configure-on-demand']               | false              | 1
        ['foo', 'clean', '--configure-on-demand']               | false              | 1
        ['clean', 'foo', '--parallel']                          | true               | 1
        ['foo', 'clean', '--parallel']                          | true               | 1
        ['clean', 'foo', '--parallel', '--configure-on-demand'] | true               | 1
        ['foo', 'clean', '--parallel', '--configure-on-demand'] | true               | 1
    }

    static String basePluginAndBasicTask() {
        """
            apply plugin: 'base'

            task foo
        """
    }
}
