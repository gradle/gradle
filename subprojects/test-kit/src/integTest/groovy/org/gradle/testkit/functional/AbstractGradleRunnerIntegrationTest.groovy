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

package org.gradle.testkit.functional

import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testkit.functional.internal.DefaultGradleRunner
import org.gradle.testkit.functional.internal.dist.InstalledGradleDistribution
import org.junit.Rule
import spock.lang.Shared
import spock.lang.Specification

abstract class AbstractGradleRunnerIntegrationTest extends Specification {
    @Shared IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()
    @Rule TestNameTestDirectoryProvider testProjectDir = new TestNameTestDirectoryProvider()
    File buildFile

    def setup() {
        buildFile = testProjectDir.file('build.gradle')
    }

    protected DefaultGradleRunner prepareGradleRunner(String... tasks) {
        DefaultGradleRunner gradleRunner = (DefaultGradleRunner)GradleRunner.create(new InstalledGradleDistribution(buildContext.gradleHomeDir))
        gradleRunner.withGradleUserHomeDir(buildContext.gradleUserHomeDir).withWorkingDir(testProjectDir.testDirectory).withArguments(tasks)
        assert gradleRunner.gradleUserHomeDir == buildContext.gradleUserHomeDir
        assert gradleRunner.workingDir == testProjectDir.testDirectory
        gradleRunner
    }

    protected String helloWorldTask() {
        """
        task helloWorld {
            doLast {
                println 'Hello world!'
            }
        }
        """
    }
}
