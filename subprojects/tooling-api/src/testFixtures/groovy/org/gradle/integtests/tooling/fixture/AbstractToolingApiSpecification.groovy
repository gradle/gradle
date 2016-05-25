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

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestDistributionDirectoryProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testing.internal.util.RetryRule
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.internal.consumer.ConnectorServices
import org.gradle.util.GradleVersion
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import org.junit.rules.RuleChain
import spock.lang.Specification

import static org.gradle.testing.internal.util.RetryRule.retryIf
/**
 * A spec that executes tests against all compatible versions of tooling API consumer and testDirectoryProvider, including the current Gradle version under test.
 *
 * <p>A test class or test method can be annotated with the following annotations to specify which versions the test is compatible with:
 * </p>
 *
 * <ul>
 *     <li>{@link ToolingApiVersion} - specifies the tooling API consumer versions that the test is compatible with.
 *     <li>{@link TargetGradleVersion} - specifies the tooling API testDirectoryProvider versions that the test is compatible with.
 * </ul>
 */
@CleanupTestDirectory
abstract class AbstractToolingApiSpecification extends Specification {

    @Rule
    public final SetSystemProperties sysProperties = new SetSystemProperties()

    @Rule
    RetryRule retryRule = retryIf(
        // known issue with pre 1.3 daemon versions: https://github.com/gradle/gradle/commit/29d895bc086bc2bfcf1c96a6efad22c602441e26
        { t ->
           GradleVersion.version(targetDist.version.baseVersion.version) < GradleVersion.version("1.3") && t.cause != null &&
                (t.cause.message ==~ /Timeout waiting to connect to (the )?Gradle daemon\./
                || t.cause.message.contains("Gradle build daemon disappeared unexpectedly (it may have been stopped, killed or may have crashed)")) }
    );

    public final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    final GradleDistribution dist = new UnderDevelopmentGradleDistribution()
    final IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()
    private static final ThreadLocal<GradleDistribution> VERSION = new ThreadLocal<GradleDistribution>()

    TestDistributionDirectoryProvider temporaryDistributionFolder = new TestDistributionDirectoryProvider();
    final ToolingApi toolingApi = new ToolingApi(targetDist, temporaryFolder)

    @Rule
    public RuleChain chain = RuleChain.outerRule(temporaryFolder).around(temporaryDistributionFolder).around(toolingApi);

    static void selectTargetDist(GradleDistribution version) {
        VERSION.set(version)
    }

    static GradleDistribution getTargetDist() {
        VERSION.get()
    }

    void reset() {
        // This method wasn't static in older tooling API versions
        new ConnectorServices().reset()
    }

    public void withConnector(@DelegatesTo(GradleConnector) @ClosureParams(value = SimpleType, options = ["org.gradle.tooling.GradleConnector"]) Closure cl) {
        toolingApi.withConnector(cl)
    }

    public <T> T withConnection(GradleConnector connector, @DelegatesTo(ProjectConnection) @ClosureParams(value = SimpleType, options = ["org.gradle.tooling.ProjectConnection"]) Closure<T> cl) {
        toolingApi.withConnection(connector, cl)
    }

    def connector() {
        toolingApi.connector()
    }
}
