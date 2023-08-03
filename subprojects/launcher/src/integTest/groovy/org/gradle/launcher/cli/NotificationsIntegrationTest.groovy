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

import org.apache.commons.io.IOUtils
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.GradleVersion

class NotificationsIntegrationTest extends AbstractIntegrationSpec {

    private static final DocumentationRegistry DOCUMENTATION_REGISTRY = new DocumentationRegistry()

    def customGradleUserHomeDir = testDirectoryProvider.getTestDirectory().file('user-home')
    def markerFile
    def welcomeMessage

    def setup() {
        executer.requireDaemon().requireIsolatedDaemons()
        executer.withGradleUserHomeDir(customGradleUserHomeDir)
        executer.withWelcomeMessageEnabled()
        markerFile = new File(executer.gradleUserHomeDir, "notifications/$distribution.version.version/release-features.rendered")

        welcomeMessage = "Welcome to Gradle $distribution.version.version!"
        def features = readReleaseFeatures()
        if (!features.isAllWhitespace()) {
            welcomeMessage += """

Here are the highlights of this release:
$features"""
        }
        if (!distribution.version.isSnapshot()) {
            welcomeMessage += """
${getReleaseNotesDetailsMessage(distribution.version)}
"""
        }
    }

    def "renders welcome message only once when executed with Gradle executable"() {
        expect:
        !markerFile.exists()

        when:
        succeeds()

        then:
        outputContains(welcomeMessage)
        markerFile.exists()

        when:
        succeeds()

        then:
        outputDoesNotContain(welcomeMessage)
        markerFile.exists()
    }

    def "show reasonable error message for invalid configuration property"() {
        when:
        propertiesFile << "org.gradle.welcome=foo"
        fails()

        then:
        errorOutput.contains("Option org.gradle.welcome doesn't accept value 'foo'. Possible values are [ONCE, NEVER]")
    }

    def "abort rendering welcome message using configuration property"() {
        when:
        propertiesFile << "org.gradle.welcome=never"
        succeeds()

        then:
        outputDoesNotContain(welcomeMessage)
    }

    def "when debug logging is enabled, debug warning is logged first"() {
        given:
        def expectedWarning = """
#############################################################################
   WARNING WARNING WARNING WARNING WARNING WARNING WARNING WARNING WARNING

   Debug level logging will leak security sensitive information!

   ${new DocumentationRegistry().getDocumentationRecommendationFor("details", "logging", "sec:debug_security")}
#############################################################################
"""
        withDebugLogging()

        when:
        succeeds()

        then:
        outputContains(expectedWarning)
    }

    static String readReleaseFeatures() {
        InputStream inputStream = NotificationsIntegrationTest.class.getClassLoader().getResourceAsStream('release-features.txt')
        StringWriter writer = new StringWriter()
        IOUtils.copy(inputStream, writer, 'UTF-8')
        writer.toString()
    }

    static String getReleaseNotesDetailsMessage(GradleVersion gradleVersion) {
        "For more details see https://docs.gradle.org/$gradleVersion.version/release-notes.html"
    }
}
