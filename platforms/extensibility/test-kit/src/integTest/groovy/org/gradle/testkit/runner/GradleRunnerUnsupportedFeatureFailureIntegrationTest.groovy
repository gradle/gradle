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

package org.gradle.testkit.runner

import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.internal.logging.CollectingTestOutputEventListener
import org.gradle.internal.logging.ConfigureLogging
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.testkit.runner.fixtures.Debug
import org.gradle.testkit.runner.fixtures.NonCrossVersion
import org.gradle.testkit.runner.fixtures.PluginUnderTest
import org.gradle.testkit.runner.internal.feature.TestKitFeature
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Retry

import static org.gradle.integtests.fixtures.RetryConditions.cleanProjectDir

@NonCrossVersion(reason = "Runs against specific target Gradle version")
@Retry(condition = { failure.class != UnsupportedFeatureException && cleanProjectDir(instance) }, count = 2)
class GradleRunnerUnsupportedFeatureFailureIntegrationTest extends BaseGradleRunnerIntegrationTest {

    protected final CollectingTestOutputEventListener outputEventListener = new CollectingTestOutputEventListener()
    @Rule
    protected final ConfigureLogging logging = new ConfigureLogging(outputEventListener)

    private static final ReleasedVersionDistributions RELEASED_VERSION_DISTRIBUTIONS = new ReleasedVersionDistributions()
    private final PluginUnderTest plugin = new PluginUnderTest(file("pluginDir"))

    def iteration = 0

    def "retries for unexpected exceptions thrown by old Gradle version (meta test)"() {
        given:
        iteration++

        when:
        throwWhen(new IllegalStateException("Unexpected Exception"), iteration == 1)
        throwWhen(new UnsupportedFeatureException("Expected Exception"), iteration == 2)

        then:
        def e = thrown UnsupportedFeatureException
        e.message == "Expected Exception"
    }

    private static void throwWhen(Throwable throwable, boolean condition) {
        if (condition) {
            throw throwable
        }
    }

    @Requires(
        value = UnitTestPreconditions.Jdk8OrEarlier,
        reason = "tests against old Gradle version that can only work with Java versions up to 8"
    )
    def "fails informatively when trying run build below min supported version"() {
        def maxUnsupportedVersion = getMaxUnsupportedVersion(TestKitFeature.RUN_BUILDS)

        given:
        buildFile << helloWorldTask()

        when:
        def result = runner('helloWorld')
            .withGradleVersion(maxUnsupportedVersion.version)
            .build()

        and:
        result.output

        then:
        def e = thrown UnsupportedFeatureException
        e.message == "The version of Gradle you are using (${maxUnsupportedVersion.version}) is not supported by TestKit. TestKit supports all Gradle versions ${DefaultGradleConnector.MINIMUM_SUPPORTED_GRADLE_VERSION.version} and later."
    }

    @Requires(
        value = UnitTestPreconditions.Jdk8OrEarlier,
        reason = "tests against old Gradle version that can only work with Java versions up to 8"
    )
    def "emits warning when running build below officially supported version"() {
        def maxUnsupportedVersion = TestKitFeature.CAPTURE_BUILD_RESULT_OUTPUT_IN_DEBUG.since // A version that does not fail when debugging
        assert maxUnsupportedVersion < DefaultGradleConnector.MINIMUM_SUPPORTED_GRADLE_VERSION

        given:
        buildFile << helloWorldTask()

        when:
        runner('helloWorld')
            .withGradleVersion(maxUnsupportedVersion.version)
            .build()

        then:
        outputEventListener.events.find {
            it.message == "The version of Gradle you are using (${maxUnsupportedVersion.version}) is deprecated with TestKit. TestKit will only support the last 5 major versions in future. This will fail with an error in Gradle 10.0. For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/third_party_integration.html#sec:embedding_compatibility in the Gradle documentation."
        } != null
    }

    @Debug
    @Requires(
        value = UnitTestPreconditions.Jdk8OrEarlier,
        reason = "tests against old Gradle version that can only work with Java versions up to 8"
    )
    def "fails informatively when trying to inspect build output in debug mode with unsupported gradle version"() {
        def maxUnsupportedVersion = getMaxUnsupportedVersion(TestKitFeature.CAPTURE_BUILD_RESULT_OUTPUT_IN_DEBUG)
        def minSupportedVersion = TestKitFeature.CAPTURE_BUILD_RESULT_OUTPUT_IN_DEBUG.since

        given:
        buildFile << helloWorldTask()

        when:
        def result = runner('helloWorld')
            .withGradleVersion(maxUnsupportedVersion.version)
            .build()

        and:
        result.output

        then:
        def e = thrown UnsupportedFeatureException
        e.message == "The version of Gradle you are using ($maxUnsupportedVersion.version) does not capture build output in debug mode with the GradleRunner. Support for this is available in Gradle $minSupportedVersion.version and all later versions."
    }

    @Requires(
        value = UnitTestPreconditions.Jdk8OrEarlier,
        reason = "tests against old Gradle version that can only work with Java versions up to 8"
    )
    def "fails informatively when trying to inject plugin classpath with unsupported gradle version"() {
        def maxUnsupportedVersion = getMaxUnsupportedVersion(TestKitFeature.PLUGIN_CLASSPATH_INJECTION)
        def minSupportedVersion = TestKitFeature.PLUGIN_CLASSPATH_INJECTION.since

        given:
        buildFile plugin.useDeclaration

        when:
        runner('helloWorld')
            .withGradleVersion(maxUnsupportedVersion.version)
            .withPluginClasspath([file("foo")])
            .build()

        then:
        def e = thrown UnsupportedFeatureException
        e.message == "The version of Gradle you are using ($maxUnsupportedVersion.version) does not support plugin classpath injection. Support for this is available in Gradle $minSupportedVersion.version and all later versions."
    }

    @Requires(
        value = UnitTestPreconditions.Jdk8OrEarlier,
        reason = "tests against old Gradle version that can only work with Java versions up to 8"
    )
    def "fails informatively if trying to use conventional plugin classpath on version that does not support injection"() {
        given:
        def maxUnsupportedVersion = getMaxUnsupportedVersion(TestKitFeature.PLUGIN_CLASSPATH_INJECTION)
        def minSupportedVersion = TestKitFeature.PLUGIN_CLASSPATH_INJECTION.since

        buildFile plugin.useDeclaration

        when:
        plugin.build().exposeMetadata {
            runner('helloWorld')
                .withGradleVersion(maxUnsupportedVersion.version)
                .withPluginClasspath()
                .buildAndFail()
        }

        then:
        def e = thrown UnsupportedFeatureException
        e.message == "The version of Gradle you are using ($maxUnsupportedVersion.version) does not support plugin classpath injection. Support for this is available in Gradle $minSupportedVersion.version and all later versions."
    }

    static GradleVersion getMaxUnsupportedVersion(TestKitFeature feature) {
        RELEASED_VERSION_DISTRIBUTIONS.getPrevious(feature.since).version
    }

}
