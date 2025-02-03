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

package org.gradle.launcher.daemon

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.executer.DocumentationUtils
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.internal.buildconfiguration.fixture.DaemonJvmPropertiesFixture
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.jvm.toolchain.JdkRepository
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

import static org.gradle.integtests.fixtures.SuggestionsMessages.GET_HELP
import static org.gradle.integtests.fixtures.SuggestionsMessages.INFO_DEBUG
import static org.gradle.integtests.fixtures.SuggestionsMessages.SCAN
import static org.gradle.integtests.fixtures.SuggestionsMessages.STACKTRACE_MESSAGE

class DaemonToolchainDownloadIntegrationTest extends AbstractIntegrationSpec implements DaemonJvmPropertiesFixture, JavaToolchainFixture {

    // Run the test by specifying a different version than the one used to execute, using two LTS alternatives
    def javaVersion = Jvm.current().javaVersion == JavaVersion.VERSION_21 ? JavaVersion.VERSION_17 : JavaVersion.VERSION_21

    def "toolchain selection that requires downloading fails when it is disabled"() {
        given:
        writeJvmCriteria(javaVersion)

        when:
        failure = executer
            .withTasks("help")
            .runWithFailure()

        then:
        failure.assertHasDescription("Cannot find a Java installation on your machine (${OperatingSystem.current()}) matching: {languageVersion=${javaVersion.majorVersion}, vendor=any vendor, implementation=vendor-specific}. " +
                "Toolchain auto-provisioning is not enabled.")
            .assertHasResolutions(
                DocumentationUtils.normalizeDocumentationLink("Learn more about toolchain auto-detection and auto-provisioning at https://docs.gradle.org/current/userguide/toolchains.html#sec:auto_detection."),
                STACKTRACE_MESSAGE, INFO_DEBUG, SCAN, GET_HELP
            )
    }

    def "toolchain download on http fails"() {
        given:
        writeJvmCriteria(javaVersion)
        writeToolchainDownloadUrls("http://insecure-server.com")

        when:
        failure = executer
            .withTasks("help")
            .withToolchainDownloadEnabled()
            .runWithFailure()

        then:
        failure.assertHasDescription("Unable to download toolchain matching the requirements ({languageVersion=${javaVersion.majorVersion}, vendor=any vendor, implementation=vendor-specific}) " +
            "from 'http://insecure-server.com', due to: Attempting to download java toolchain from an insecure URI http://insecure-server.com. This is not supported, use a secure URI instead")
    }

    def "toolchain download on syntax exception url fails"() {
        given:
        writeJvmCriteria(javaVersion)
        writeToolchainDownloadUrls("https://server.com/v=^10")

        when:
        failure = executer
            .withTasks("help")
            .withToolchainDownloadEnabled()
            .runWithFailure()

        then:
        failure.assertHasDescription("Unable to download toolchain matching the requirements ({languageVersion=${javaVersion.majorVersion}, vendor=any vendor, implementation=vendor-specific}) from 'https://server.com/v=^10'")
            .assertHasResolutions(
                DocumentationUtils.normalizeDocumentationLink("Learn more about toolchain auto-detection and auto-provisioning at https://docs.gradle.org/current/userguide/toolchains.html#sec:auto_detection."),
                DocumentationUtils.normalizeDocumentationLink("Learn more about toolchain repositories at https://docs.gradle.org/current/userguide/toolchains.html#sub:download_repositories."),
                STACKTRACE_MESSAGE, INFO_DEBUG, SCAN, GET_HELP
            )
    }

    def "toolchain download on invalid url fails"() {
        given:
        writeJvmCriteria(javaVersion)
        writeToolchainDownloadUrls("invalid-url")

        when:
        failure = executer
            .withTasks("help")
            .withToolchainDownloadEnabled()
            .runWithFailure()

        then:
        failure.assertHasDescription("Unable to download toolchain matching the requirements ({languageVersion=${javaVersion.majorVersion}, vendor=any vendor, implementation=vendor-specific}) from 'invalid-url'")
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "toolchain downloaded is checked against the spec"() {
        def otherJavaVersion = AvailableJavaHomes.getDifferentVersion(javaVersion).javaVersion
        given:
        def jdkRepository = new JdkRepository(otherJavaVersion)
        def uri = jdkRepository.start()
        jdkRepository.reset()

        writeJvmCriteria(javaVersion)
        writeToolchainDownloadUrls(uri.toString())

        when:
        failure = executer
            .withTasks("help", "-s")
            .requireOwnGradleUserHomeDir("Needs to download a JDK")
            .withToolchainDownloadEnabled()
            .runWithFailure()

        and:
        jdkRepository.stop()

        then:
        failure.assertHasDescription("Unable to download toolchain matching the requirements ({languageVersion=${javaVersion.majorVersion}, vendor=any vendor, implementation=vendor-specific}) from '$uri', " +
            "due to: Toolchain provisioned from '$uri' doesn't satisfy the specification: {languageVersion=${javaVersion.majorVersion}, vendor=any vendor, implementation=vendor-specific}")
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "toolchain downloaded is used by daemon when spec matches"() {
        def differentJdk = AvailableJavaHomes.differentVersion
        given:
        def jdkRepository = new JdkRepository(differentJdk, "jdk.zip")
        def uri = jdkRepository.start()
        jdkRepository.reset()

        writeJvmCriteria(differentJdk.javaVersion)
        writeToolchainDownloadUrls(uri.toString())
        captureJavaHome()

        when:
        executer.withTasks("help")
            .requireOwnGradleUserHomeDir("Needs to download a JDK")
            .requireIsolatedDaemons()
            .withToolchainDownloadEnabled()
            .start().waitForExit()

        and:
        jdkRepository.stop()

        then:
        def installedToolchains = executer.gradleUserHomeDir.file("jdks").listFiles().findAll { it.isDirectory() }
        assert installedToolchains.size() == 1
        assertDaemonUsedJvm(findJavaHome(installedToolchains[0]))
    }
}
