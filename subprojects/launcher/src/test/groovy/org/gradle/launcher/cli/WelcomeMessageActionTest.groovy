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
import org.gradle.launcher.cli.CommandLineActionFactory.WelcomeMessageAction
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import static org.gradle.launcher.cli.CommandLineActionFactory.WELCOME_MESSAGE_ENABLED_SYSTEM_PROPERTY

class WelcomeMessageActionTest extends Specification {

    @Rule
    public final SetSystemProperties sysProperties = new SetSystemProperties()

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder()

    BuildLayoutParameters buildLayoutParameters
    File gradleUserHomeDir
    ByteArrayOutputStream out

    def setup() {
        gradleUserHomeDir = temporaryFolder.root
        buildLayoutParameters = Mock(BuildLayoutParameters) {
            getGradleUserHomeDir() >> gradleUserHomeDir
        }
        out = new ByteArrayOutputStream()
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
        action.execute(new PrintStream(out))

        then:
        def output = out.toString()
        output.contains("Welcome to Gradle 42.0!")
        output.contains("Here are the highlights of this release:")
        output.contains(" - foo")
        output.contains(" - bar")
    }

    def "omits highlights when file contains only whitespace"() {
        given:
        def inputStreamProvider = Mock(Function) {
            apply(_) >> new ByteArrayInputStream("""

""".bytes)
        }

        when:
        def action = new WelcomeMessageAction(buildLayoutParameters, GradleVersion.current(), inputStreamProvider)
        action.execute(new PrintStream(out))

        then:

        !out.toString().contains("Here are the highlights of this release:")
    }

    def "omits highlights when file does not exist"() {
        given:
        def inputStreamProvider = Mock(Function) {
            apply(_) >> null
        }

        when:
        def action = new WelcomeMessageAction(buildLayoutParameters, GradleVersion.current(), inputStreamProvider)
        action.execute(new PrintStream(out))

        then:

        !out.toString().contains("Here are the highlights of this release:")
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
        action.execute(new PrintStream(out))

        then:
        inputStreamWasClosed
    }

    def "prints links to release notes for non-snapshot versions"() {
        when:
        def action = new WelcomeMessageAction(buildLayoutParameters, GradleVersion.version(version), Mock(Function))
        action.execute(new PrintStream(out))

        then:
        out.toString().contains("For more details see https://docs.gradle.org/${version}/release-notes.html") == shouldContainLink

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
        action.execute(new PrintStream(out))

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
        action.execute(new PrintStream(out))

        then:
        out.toString().isEmpty()
    }

    def "does not print anything if system property is set to false"() {
        given:
        System.setProperty(WELCOME_MESSAGE_ENABLED_SYSTEM_PROPERTY, "false");

        when:
        def action = new WelcomeMessageAction(buildLayoutParameters, GradleVersion.current(), Mock(Function))
        action.execute(new PrintStream(out))

        then:
        out.toString().isEmpty()
    }

    private TestFile markerFile(String version) {
        new TestFile(gradleUserHomeDir, "notifications", version, "release-features.rendered")
    }
}
