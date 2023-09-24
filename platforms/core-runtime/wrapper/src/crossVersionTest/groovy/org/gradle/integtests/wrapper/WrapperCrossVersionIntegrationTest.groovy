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
package org.gradle.integtests.wrapper


import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.test.preconditions.UnitTestPreconditions

@SuppressWarnings("IntegrationTestFixtures")
class WrapperCrossVersionIntegrationTest extends AbstractWrapperCrossVersionIntegrationTest {
    void canUseWrapperFromPreviousVersionToRunCurrentVersion() {
        when:
        GradleExecuter executer = prepareWrapperExecuter(previous, current)

        then:
        checkWrapperWorksWith(executer, current)

        cleanup:
        cleanupDaemons(executer, current)
    }

    @Requires(value = [
        IntegTestPreconditions.NotEmbeddedExecutor,
        UnitTestPreconditions.NotWindowsJavaBefore11
    ], reason = "wrapperExecuter requires a real distribution, https://github.com/gradle/gradle-private/issues/3758")
    void canUseWrapperFromCurrentVersionToRunPreviousVersion() {
        when:
        GradleExecuter executer = prepareWrapperExecuter(current, previous).withWarningMode(null)

        then:
        checkWrapperWorksWith(executer, previous)

        cleanup:
        cleanupDaemons(executer, previous)
    }

    void checkWrapperWorksWith(GradleExecuter executer, GradleDistribution executionVersion) {
        def result = executer.usingExecutable('gradlew').withTasks('hello').run()

        assert result.output.contains("hello from $executionVersion.version.version")
        assert result.output.contains("using distribution at ${executer.gradleUserHomeDir.file("wrapper/dists")}")
        assert result.output.contains("using Gradle user home at $executer.gradleUserHomeDir")
    }

    static void cleanupDaemons(GradleExecuter executer, GradleDistribution executionVersion) {
        new DaemonLogsAnalyzer(executer.daemonBaseDir, executionVersion.version.version).killAll()
    }
}
