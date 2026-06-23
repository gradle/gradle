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

package org.gradle.testkit.runner

import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.testkit.runner.fixtures.NonCrossVersion
import org.junit.Assume

import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger

import static org.gradle.tooling.internal.consumer.DefaultGradleConnector.MINIMUM_SUPPORTED_GRADLE_VERSION

@NonCrossVersion
class GradleRunnerUnsupportedGradleVersionFailureIntegrationTest extends BaseGradleRunnerIntegrationTest {

    // Pick a Gradle version above RUN_BUILDS.since (2.6) but below MINIMUM_SUPPORTED_GRADLE_VERSION,
    // so it triggers the deprecation warning path in ToolingApiGradleExecutor/BuildResultOutputFeatureCheck.
    // Regression test for https://github.com/gradle/gradle/issues/36267
    def "succeeds running build with deprecated Gradle version below minimum supported version"() {
        // The old Gradle versions fail to run on the modern JDKs, so this test is embedded-only
        Assume.assumeTrue(embedded)

        given:
        def oldGradleVersion = new ReleasedVersionDistributions().all
            .collect { it.version }
            .findAll { it < MINIMUM_SUPPORTED_GRADLE_VERSION }
            .max()
            .version
        buildFile << helloWorldTask()
        List<LogRecord> logRecords = []
        def handler = new Handler() {
            @Override
            void publish(LogRecord record) { logRecords << record }

            @Override
            void flush() {}

            @Override
            void close() {}
        }
        def logger = Logger.getLogger("org.gradle.testkit.runner.internal.feature.BuildResultOutputFeatureCheck")
        logger.addHandler(handler)

        when:
        def result = runner('helloWorld')
            .withGradleVersion(oldGradleVersion)
            .build()

        and:
        def output = result.output

        then:
        output.contains("Hello world!")
        logRecords.any {
            it.message.contains("The version of Gradle you are using ($oldGradleVersion) is deprecated with TestKit. " +
                "TestKit will only support the last 5 major versions in future. " +
                "This will fail with an error starting with Gradle 10. " +
                "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/tooling_api.html#sec:embedding_compatibility"
            )
        }

        cleanup:
        logger.removeHandler(handler)
    }

    def "fails informatively when trying to use unsupported gradle version"() {
        given:
        buildFile << helloWorldTask()

        when:
        def result = runner('helloWorld')
            .withGradleVersion("1.1")
            .build()

        and:
        result.output

        then:
        def e = thrown UnsupportedFeatureException
        e.message == "The version of Gradle you are using (1.1) is not supported by TestKit. TestKit supports all Gradle versions ${MINIMUM_SUPPORTED_GRADLE_VERSION.version} and later."
    }
}
