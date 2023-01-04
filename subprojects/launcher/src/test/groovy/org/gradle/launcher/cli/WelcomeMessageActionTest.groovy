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
import org.gradle.api.Action
import org.gradle.api.launcher.cli.WelcomeMessageConfiguration
import org.gradle.api.launcher.cli.WelcomeMessageDisplayMode
import org.gradle.internal.logging.ToStringLogger
import org.gradle.launcher.bootstrap.ExecutionListener
import org.gradle.launcher.configuration.BuildLayoutResult
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion
import org.gradle.util.SetSystemProperties
import org.gradle.util.internal.TextUtil
import org.junit.Rule
import spock.lang.Specification
import spock.lang.TempDir

import static org.gradle.launcher.cli.DefaultCommandLineActionFactory.WELCOME_MESSAGE_ENABLED_SYSTEM_PROPERTY

class WelcomeMessageActionTest extends Specification {

    @Rule
    public final SetSystemProperties sysProperties = new SetSystemProperties()

    @TempDir
    public File temporaryFolder

    BuildLayoutResult buildLayout
    File gradleUserHomeDir
    ToStringLogger log
    Action<ExecutionListener> delegateAction
    ExecutionListener listener
    WelcomeMessageConfiguration welcomeMessageConfiguration

    def setup() {
        gradleUserHomeDir = temporaryFolder
        buildLayout = Mock(BuildLayoutResult) {
            getGradleUserHomeDir() >> gradleUserHomeDir
        }
        log = new ToStringLogger()
        delegateAction = Mock()
        listener = Mock()
        welcomeMessageConfiguration = Stub()
    }

    private WelcomeMessageAction createWelcomeMessage(GradleVersion gradleVersion = GradleVersion.current(), String welcomeMessage) {
        return new WelcomeMessageAction(log, buildLayout, welcomeMessageConfiguration, gradleVersion, { welcomeMessage == null ? null : new ByteArrayInputStream(welcomeMessage.bytes) } as Function, delegateAction)
    }

    def "prints highlights when file exists and contains visible content"() {
        given:
        def action = createWelcomeMessage(GradleVersion.version("42.0"), """ - foo
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
        def action = createWelcomeMessage("""

""")

        when:
        action.execute(listener)

        then:
        !log.toString().contains("Here are the highlights of this release:")
        1 * delegateAction.execute(_)
    }

    def "omits highlights when file does not exist"() {
        given:
        def action = createWelcomeMessage(null)

        when:
        action.execute(listener)

        then:
        !log.toString().contains("Here are the highlights of this release:")
        1 * delegateAction.execute(_)
    }

    def "closes InputStream after reading features"() {
        given:
        def inputStreamWasClosed = false
        def inputStreamProvider = Mock(Function) {
            apply(_) >> new ByteArrayInputStream(Byte.parseByte('0')) {
                @Override
                void close() throws IOException {
                    inputStreamWasClosed = true
                }
            }
        }
        def action = new WelcomeMessageAction(log, buildLayout, welcomeMessageConfiguration, GradleVersion.version("42.0"), inputStreamProvider, delegateAction)

        when:
        action.execute(listener)

        then:
        inputStreamWasClosed
        1 * delegateAction.execute(_)
    }

    def "does not print links to release notes for snapshot versions"() {
        given:
        def action = createWelcomeMessage(GradleVersion.version("1.0-SNAPSHOT"), null)

        when:
        action.execute(listener)

        then:
        !log.toString().contains("For more details see https://docs.gradle.org/")
        1 * delegateAction.execute(_)
    }

    def "prints links to release notes for non-snapshot versions"() {
        given:
        def action = createWelcomeMessage(GradleVersion.version("1.0"), null)

        when:
        action.execute(listener)

        then:
        log.toString().contains("For more details see https://docs.gradle.org/1.0/release-notes.html")
        1 * delegateAction.execute(_)
    }

    def "writes marker file after printing welcome message"() {
        given:
        def version = "42.0"
        def action = createWelcomeMessage(GradleVersion.version(version), null)

        when:
        action.execute(listener)

        then:
        markerFile(version).exists()
        1 * delegateAction.execute(_)
    }

    def "does not print anything if marker file exists"() {
        given:
        def version = "42.0"
        def markerFile = markerFile(version)
        markerFile.getParentFile().mkdirs()
        markerFile.touch()
        def action = createWelcomeMessage(GradleVersion.version(version), null)

        when:
        action.execute(listener)

        then:
        log.toString().isEmpty()
        1 * delegateAction.execute(_)
    }

    def "does not print anything if system property is set to false"() {
        given:
        System.setProperty(WELCOME_MESSAGE_ENABLED_SYSTEM_PROPERTY, "false")
        def action = createWelcomeMessage(null)

        when:
        action.execute(listener)

        then:
        log.toString().isEmpty()
        1 * delegateAction.execute(_)
    }

    def "does not print anything if gradle property is set to hide welcome message"() {
        given:
        welcomeMessageConfiguration = new WelcomeMessageConfiguration(WelcomeMessageDisplayMode.NEVER)
        def action = createWelcomeMessage(null)

        when:
        action.execute(listener)

        then:
        log.toString().isEmpty()
        1 * delegateAction.execute(_)
    }

    private TestFile markerFile(String version) {
        new TestFile(gradleUserHomeDir, "notifications", version, "release-features.rendered")
    }
}
