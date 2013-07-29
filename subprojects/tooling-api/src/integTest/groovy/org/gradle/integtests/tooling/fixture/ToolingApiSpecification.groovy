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

import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.tooling.GradleConnector
import org.gradle.util.GradleVersion
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import org.junit.runner.RunWith
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Specification

/**
 * A spec that executes tests against all compatible versions of tooling API consumer and provider, including the current Gradle version under test.
 *
 * <p>A test class or test method can be annotated with the following annotations to specify which versions the test is compatible with:
 * </p>
 *
 * <ul>
 *     <li>{@link ToolingApiVersion} - specifies the minimum tooling API consumer version that the test is compatible with.
 *     <li>{@link MinTargetGradleVersion} - specifies the minimum tooling API provider version that the test is compatible with.
 *     <li>{@link MaxTargetGradleVersion} - specifies the maximum tooling API provider version that the test is compatible with.
 * </ul>
 */
@RunWith(ToolingApiCompatibilitySuiteRunner)
abstract class ToolingApiSpecification extends Specification {
    static final Logger LOGGER = LoggerFactory.getLogger(ToolingApiSpecification)
    @Rule public final SetSystemProperties sysProperties = new SetSystemProperties()
    @Rule public final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    final GradleDistribution dist = new UnderDevelopmentGradleDistribution()
    final IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()
    final ToolingApi toolingApi = new ToolingApi(dist, temporaryFolder)
    private static final ThreadLocal<GradleDistribution> VERSION = new ThreadLocal<GradleDistribution>()

    static void selectTargetDist(GradleDistribution version) {
        VERSION.set(version)
    }

    static GradleDistribution getTargetDist() {
        VERSION.get()
    }

    void setup() {
        def consumerGradle = GradleVersion.current()
        def target = GradleVersion.version(VERSION.get().version.version)
        LOGGER.info(" Using Tooling API consumer ${consumerGradle}, provider ${target}")
        this.toolingApi.withConnector {
            if (consumerGradle.version != target.version) {
                LOGGER.info("Overriding daemon tooling API provider to use installation: " + target);
                it.useInstallation(new File(getTargetDist().gradleHomeDir.absolutePath))
                it.embedded(false)
            }
        }
    }

    public <T> T withConnection(Closure<T> cl) {
        toolingApi.withConnection(cl)
    }

    public <T> T withConnection(GradleConnector connector, Closure<T> cl) {
        toolingApi.withConnection(connector, cl)
    }

    public ConfigurableOperation withModel(Class modelType, Closure cl = {}) {
        withConnection {
            def model = it.model(modelType)
            cl(model)
            new ConfigurableOperation(model).buildModel()
        }
    }

    public ConfigurableOperation withBuild(Closure cl = {}) {
        withConnection {
            def build = it.newBuild()
            cl(build)
            def out = new ConfigurableOperation(build)
            build.run()
            out
        }
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
