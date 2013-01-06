/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.integtests.tooling.fixture

import org.gradle.integtests.fixtures.executer.BasicGradleDistribution
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.tooling.GradleConnector
import org.gradle.util.GradleVersion
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import org.junit.internal.AssumptionViolatedException
import org.junit.runner.RunWith
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Specification

@RunWith(ToolingApiCompatibilitySuiteRunner)
abstract class ToolingApiSpecification extends Specification {
    static final Logger LOGGER = LoggerFactory.getLogger(ToolingApiSpecification)
    @Rule public final SetSystemProperties sysProperties = new SetSystemProperties()
    @Rule public final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    final BasicGradleDistribution dist = new GradleDistribution(temporaryFolder)
    final IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()
    final ToolingApi toolingApi = new ToolingApi(dist, temporaryFolder)
    private static final ThreadLocal<BasicGradleDistribution> VERSION = new ThreadLocal<BasicGradleDistribution>()

    static void selectTargetDist(BasicGradleDistribution version) {
        VERSION.set(version)
    }

    static BasicGradleDistribution getTargetDist() {
        VERSION.get()
    }

    void setup() {
        def consumerGradle = GradleVersion.current()
        def target = GradleVersion.version(VERSION.get().version)
        LOGGER.info(" Using Tooling API consumer ${consumerGradle}, provider ${target}")
        boolean accept = accept(consumerGradle, target)
        if (!accept) {
            throw new AssumptionViolatedException("Test class ${getClass().name} does not work with tooling API ${consumerGradle} and Gradle ${target}.")
        }
        this.toolingApi.withConnector {
            if (consumerGradle.version != target.version) {
                LOGGER.info("Overriding daemon tooling API provider to use installation: " + target);
                def targetGradle = buildContext.getReleasedDistribution(target.version, temporaryFolder)
                it.useInstallation(new File(targetGradle.gradleHomeDir.absolutePath))
                it.embedded(false)
            }
        }
    }

    /**
     * Returns true if this test class works with the given combination of tooling API consumer and provider.
     */
    protected boolean accept(GradleVersion toolingApi, GradleVersion targetGradle) {
        return true
    }

    public <T> T withConnection(Closure<T> cl) {
        toolingApi.withConnection(cl)
    }

    public <T> T withConnection(GradleConnector connector, Closure<T> cl) {
        toolingApi.withConnection(connector, cl)
    }

    def connector() {
        toolingApi.connector()
    }

    void maybeFailWithConnection(Closure cl) {
        toolingApi.maybeFailWithConnection(cl)
    }

    TestFile getProjectDir() {
        temporaryFolder.testDirectory
    }

    TestFile getBuildFile() {
        file("build.gradle")
    }

    TestFile file(Object... path) {
        projectDir.file(path)
    }

}
