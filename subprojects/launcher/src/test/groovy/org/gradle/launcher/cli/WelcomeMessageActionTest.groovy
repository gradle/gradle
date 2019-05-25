/*
 * Copyright 2018 the original author or authors.
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

import com.google.common.base.Function
import org.gradle.initialization.BuildLayoutParameters
import org.gradle.internal.logging.slf4j.OutputEventListenerBackedLogger
import org.gradle.internal.logging.slf4j.OutputEventListenerBackedLoggerContext
import org.gradle.internal.time.MockClock
import org.gradle.launcher.cli.DefaultCommandLineActionFactory.WelcomeMessageAction
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion
import org.gradle.util.SetSystemProperties
import org.gradle.util.TextUtil
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import static org.gradle.launcher.cli.DefaultCommandLineActionFactory.WELCOME_MESSAGE_ENABLED_SYSTEM_PROPERTY

class WelcomeMessageActionTest extends Specification {

    @Rule
    public final SetSystemProperties sysProperties = new SetSystemProperties()

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder()

    BuildLayoutParameters buildLayoutParameters
    File gradleUserHomeDir
    ToStringLogger log

    def setup() {
        gradleUserHomeDir = temporaryFolder.root
        buildLayoutParameters = Mock(BuildLayoutParameters) {
            getGradleUserHomeDir() >> gradleUserHomeDir
        }
        log = new ToStringLogger()
    }

    def "prints highlights when file exists and contains visible content"() {
        given:
        def inputStreamProvider = Mock(Function) {
            apply(_) >> { new ByteArrayInputStream(""" - foo
 - bar
 """.bytes) }
        }

        when:
        def action = new WelcomeMessageAction(buildLayoutParameters, GradleVersion.version("42.0"), inputStreamProvider)
        action.execute(log)

        then:
        def output = TextUtil.normaliseLineSeparators(log.toString());
        output.contains('''Welcome to Gradle 42.0!

Here are the highlights of this release:
 - foo
 - bar

For more details see https://docs.gradle.org/42.0/release-notes.html''')
    }

    def "omits highlights when file contains only whitespace"() {
        given:
        def inputStreamProvider = Mock(Function) {
            apply(_) >> new ByteArrayInputStream("""

""".bytes)
        }

        when:
        def action = new WelcomeMessageAction(buildLayoutParameters, GradleVersion.current(), inputStreamProvider)
        action.execute(log)

        then:

        !log.toString().contains("Here are the highlights of this release:")
    }

    def "omits highlights when file does not exist"() {
        given:
        def inputStreamProvider = Mock(Function) {
            apply(_) >> null
        }

        when:
        def action = new WelcomeMessageAction(buildLayoutParameters, GradleVersion.current(), inputStreamProvider)
        action.execute(log)

        then:

        !log.toString().contains("Here are the highlights of this release:")
    }

    def "closes InputStream after reading features"() {
        given:
        def inputStreamWasClosed = false
        def inputStreamProvider = Mock(Function) {
            apply(_) >> new ByteArrayInputStream("".bytes) {
                @Override
                void close() throws IOException {
                    inputStreamWasClosed = true
                }
            }
        }

        when:
        def action = new WelcomeMessageAction(buildLayoutParameters, GradleVersion.version("42.0"), inputStreamProvider)
        action.execute(log)

        then:
        inputStreamWasClosed
    }

    def "prints links to release notes for non-snapshot versions"() {
        when:
        def action = new WelcomeMessageAction(buildLayoutParameters, GradleVersion.version(version), Mock(Function))
        action.execute(log)

        then:
        log.toString().contains("For more details see https://docs.gradle.org/${version}/release-notes.html") == shouldContainLink

        where:
        version        | shouldContainLink
        "1.0"          | true
        "1.0-SNAPSHOT" | false
    }

    def "writes marker file after printing welcome message"() {
        given:
        def version = "42.0"

        when:
        def action = new WelcomeMessageAction(buildLayoutParameters, GradleVersion.version(version), Mock(Function))
        action.execute(log)

        then:
        markerFile(version).exists()
    }

    def "does not print anything if marker file exists"() {
        given:
        def version = "42.0"
        def markerFile = markerFile(version)
        markerFile.getParentFile().mkdirs()
        markerFile.touch()

        when:
        def action = new WelcomeMessageAction(buildLayoutParameters, GradleVersion.version(version), Mock(Function))
        action.execute(log)

        then:
        log.toString().isEmpty()
    }

    def "does not print anything if system property is set to false"() {
        given:
        System.setProperty(WELCOME_MESSAGE_ENABLED_SYSTEM_PROPERTY, "false");

        when:
        def action = new WelcomeMessageAction(buildLayoutParameters, GradleVersion.current(), Mock(Function))
        action.execute(log)

        then:
        log.toString().isEmpty()
    }

    private TestFile markerFile(String version) {
        new TestFile(gradleUserHomeDir, "notifications", version, "release-features.rendered")
    }

    private static class ToStringLogger extends OutputEventListenerBackedLogger {

        private final StringBuilder log = new StringBuilder()

        ToStringLogger() {
            super("ToStringLogger", new OutputEventListenerBackedLoggerContext(new MockClock()), new MockClock())
        }

        @Override
        void lifecycle(String message) {
            log.append(message)
            log.append('\n')
        }

        @Override
        String toString() {
            log.toString()
        }
    }
}
