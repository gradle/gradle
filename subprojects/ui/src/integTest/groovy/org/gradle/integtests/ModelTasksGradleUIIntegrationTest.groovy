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

package org.gradle.integtests

import org.gradle.foundation.ProjectView
import org.gradle.foundation.TestUtility
import org.gradle.gradleplugin.foundation.GradlePluginLord
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.testfixtures.internal.NativeServicesTestFixture

import java.util.concurrent.TimeUnit

class ModelTasksGradleUIIntegrationTest extends AbstractIntegrationSpec {

    GradlePluginLord gradlePluginLord = new GradlePluginLord()

    def setup() {
        NativeServicesTestFixture.initialize()
        gradlePluginLord.setCurrentDirectory(temporaryFolder.testDirectory);
        gradlePluginLord.setGradleHomeDirectory(distribution.gradleHomeDir);
        gradlePluginLord.addCommandLineArgumentAlteringListener(new ExtraTestCommandLineOptionsListener(executer.gradleUserHomeDir))

        file('build.gradle') << '''
            model {
                tasks {
                    create("fromModel") {
                        doLast {
                            buildDir.mkdirs()
                            new File(buildDir, "output.txt") << "from model rule defined task"
                        }
                    }
                }
            }
        '''
        TestUtility.refreshProjectsBlocking(gradlePluginLord, 40, TimeUnit.SECONDS);

    }

    def "tasks added using model rule are visible"() {
        when:
        List<ProjectView> projects = gradlePluginLord.getProjects();

        then:
        projects.first().getTask("fromModel")
    }

    def "tasks added using model rule can be executed"() {
        TestExecutionInteraction executionInteraction = new TestExecutionInteraction();

        when:
        TestUtility.executeBlocking(gradlePluginLord, "fromModel", "Test Execution", executionInteraction, 40)

        then:
        file('build/output.txt').text == "from model rule defined task"
    }
}
