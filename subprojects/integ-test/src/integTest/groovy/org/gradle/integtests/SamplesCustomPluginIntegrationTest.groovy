/*
 * Copyright 2010 the original author or authors.
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

import org.junit.Rule
import org.junit.Test
import org.gradle.util.TestFile
import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.integtests.fixtures.Sample

class SamplesCustomPluginIntegrationTest {
    @Rule public final GradleDistribution dist = new GradleDistribution()
    @Rule public final GradleDistributionExecuter executer = new GradleDistributionExecuter()
    @Rule public final Sample sample = new Sample('customPlugin')

    @Test
    public void canTestPluginAndTaskImplementation() {
        TestFile projectDir = sample.dir

        executer.inDirectory(projectDir).withTasks('check').run()

        def result = new JUnitTestExecutionResult(projectDir)
        result.assertTestClassesExecuted('org.gradle.GreetingTaskTest', 'org.gradle.GreetingPluginTest')
    }

    @Test
    public void canPublishAndUsePluginAndTestImplementations() {
        TestFile projectDir = sample.dir

        executer.inDirectory(projectDir).withTasks('uploadArchives').run()

        def result = executer.usingBuildScript(projectDir.file('usesCustomTask.gradle')).withTasks('greeting').run()
        assert result.output.contains('howdy!')

        result = executer.usingBuildScript(projectDir.file('usesCustomPlugin.gradle')).withTasks('hello').run()
        assert result.output.contains('hello from GreetingTask')
    }
}
