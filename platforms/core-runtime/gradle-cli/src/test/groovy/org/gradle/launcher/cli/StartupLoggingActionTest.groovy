/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.launcher.cli

import org.gradle.api.Action
import org.gradle.api.launcher.cli.WelcomeMessageConfiguration
import org.gradle.api.launcher.cli.WelcomeMessageDisplayMode
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.configuration.LoggingConfiguration
import org.gradle.internal.logging.DefaultLoggingConfiguration
import org.gradle.internal.logging.ToStringLogger
import org.gradle.launcher.bootstrap.ExecutionListener
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion
import org.gradle.util.SetSystemProperties
import org.gradle.util.internal.TextUtil
import org.jspecify.annotations.Nullable
import org.junit.Rule
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.charset.StandardCharsets
import java.util.function.Supplier

class StartupLoggingActionTest extends Specification {

    @Rule
    public final SetSystemProperties sysProperties = new SetSystemProperties()

    @TempDir
    public File temporaryFolder

    ToStringLogger log  = new ToStringLogger()
    Action<ExecutionListener> delegateAction  = Mock()
    ExecutionListener listener = Mock()
    WelcomeMessageConfiguration welcomeMessageConfiguration = Stub()
    LoggingConfiguration loggingConfiguration = new DefaultLoggingConfiguration()

    File gradleUserHomeDir

    def setup() {
        gradleUserHomeDir = temporaryFolder
    }

    private StartupLoggingAction createStartupLogging(GradleVersion gradleVersion = GradleVersion.current(), @Nullable String welcomeMessage) {
        def releaseFeaturesSupplier = () -> new ByteArrayInputStream((welcomeMessage ?: "").getBytes(StandardCharsets.UTF_8))
        return new StartupLoggingAction(log, gradleUserHomeDir, welcomeMessageConfiguration, loggingConfiguration, gradleVersion, releaseFeaturesSupplier, delegateAction)
    }

    def "prints highlights when file exists and contains visible content"() {
        given:
        def action = createStartupLogging(GradleVersion.version("42.0"), """ - foo
 - bar
 """)

        when:
        action.execute(listener)

        then:
        def output = TextUtil.normaliseLineSeparators(log.toString())
        output.contains('''Welcome to Gradle 42.0!

Here are the highlights of this release:
 - foo
 - bar

For more details see https://docs.gradle.org/42.0/release-notes.html''')
        1 * delegateAction.execute(_)
    }

    def "omits highlights when file contains only whitespace"() {
        given:
        def action = createStartupLogging("""

""")

        when:
        action.execute(listener)

        then:
        !log.toString().contains("Here are the highlights of this release:")
        1 * delegateAction.execute(_)
    }

    def "omits highlights when file does not exist"() {
        given:
        def action = createStartupLogging(null)

        when:
        action.execute(listener)

        then:
        !log.toString().contains("Here are the highlights of this release:")
        1 * delegateAction.execute(_)
    }

    def "closes InputStream after reading features"() {
        given:
        def inputStreamWasClosed = false
        Supplier<InputStream> inputStreamProvider = () -> new ByteArrayInputStream(Byte.parseByte('0')) {
            @Override
            void close() throws IOException {
                inputStreamWasClosed = true
            }
        }
        def action = new StartupLoggingAction(log, gradleUserHomeDir, welcomeMessageConfiguration, loggingConfiguration, GradleVersion.version("42.0"), inputStreamProvider, delegateAction)

        when:
        action.execute(listener)

        then:
        inputStreamWasClosed
        1 * delegateAction.execute(_)
    }

    def "does not print links to release notes for snapshot versions"() {
        given:
        def action = createStartupLogging(GradleVersion.version("1.0-SNAPSHOT"), null)

        when:
        action.execute(listener)

        then:
        !log.toString().contains("For more details see https://docs.gradle.org/")
        1 * delegateAction.execute(_)
    }

    def "prints links to release notes for non-snapshot versions"() {
        given:
        def action = createStartupLogging(GradleVersion.version("1.0"), null)

        when:
        action.execute(listener)

        then:
        log.toString().contains("For more details see https://docs.gradle.org/1.0/release-notes.html")
        1 * delegateAction.execute(_)
    }

    def "writes marker file after printing welcome message"() {
        given:
        def version = "42.0"
        def action = createStartupLogging(GradleVersion.version(version), null)

        when:
        action.execute(listener)

        then:
        welcomeMarkerFile(version).exists()
        1 * delegateAction.execute(_)
    }

    def "does not print welcome if marker file exists"() {
        given:
        def version = "42.0"
        def markerFile = welcomeMarkerFile(version)
        markerFile.getParentFile().mkdirs()
        markerFile.touch()
        def action = createStartupLogging(GradleVersion.version(version), null)

        when:
        action.execute(listener)

        then:
        log.toString().isEmpty()
        1 * delegateAction.execute(_)
    }

    def "does not print anything if system property is set to false"() {
        given:
        System.setProperty(StartupLoggingAction.WELCOME_MESSAGE_ENABLED_SYSTEM_PROPERTY, "false")
        def action = createStartupLogging(null)

        when:
        action.execute(listener)

        then:
        log.toString().isEmpty()
        1 * delegateAction.execute(_)
    }

    def "does not print anything if gradle property is set to hide welcome message"() {
        given:
        welcomeMessageConfiguration = new WelcomeMessageConfiguration(WelcomeMessageDisplayMode.NEVER)
        def action = createStartupLogging(null)

        when:
        action.execute(listener)

        then:
        log.toString().isEmpty()
        1 * delegateAction.execute(_)
    }

    def "prints twice when debugging is enabled"() {
        given:
        loggingConfiguration.setLogLevel(LogLevel.DEBUG)
        def action = createStartupLogging(null)
        when:
        action.execute(listener)
        then:
        def output = TextUtil.normaliseFileSeparators(log.toString())
        assertDoublePrintInOutput(output)
        1 * delegateAction.execute(_)
    }

    def "prints twice when debugging is enabled and an exception is thrown"() {
        delegateAction.execute(listener) >> { throw new RuntimeException("Boom!") }

        given:
        loggingConfiguration.setLogLevel(LogLevel.DEBUG)
        def action = createStartupLogging(null)

        when:
        action.execute(listener)

        then:
        thrown(RuntimeException)
        def output = TextUtil.normaliseFileSeparators(log.toString())
        assertDoublePrintInOutput(output)
    }

    private void assertDoublePrintInOutput(String output) {
        // This should exist twice
        assert output.count(StartupLoggingAction.DEBUG_WARNING_MESSAGE_BODY) == 2
    }

    private TestFile welcomeMarkerFile(String version) {
        new TestFile(gradleUserHomeDir, "notifications", version, "release-features.rendered")
    }
}
