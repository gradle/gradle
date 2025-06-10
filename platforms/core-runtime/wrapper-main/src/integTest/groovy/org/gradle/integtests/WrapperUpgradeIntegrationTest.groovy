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

package org.gradle.integtests

import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.test.fixtures.keystore.TestKeyStore
import org.gradle.test.fixtures.server.http.BlockingHttpsServer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.junit.Rule


@Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = NOT_EMBEDDED_REASON)
class WrapperUpgradeIntegrationTest extends AbstractWrapperIntegrationSpec {

    @Rule
    BlockingHttpsServer server = new BlockingHttpsServer()
    TestKeyStore keyStore

    def setup() {
        keyStore = TestKeyStore.init(file("keys").createDir())
        server.configure(keyStore)
        server.start()

        prepareWrapper()
        wrapperExecuter
            .withArguments(keyStore.getTrustStoreArguments())
            .withCommandLineGradleOpts("-Dorg.gradle.internal.services.base.url=https://localhost:${server.port}")
    }

    def "can run the wrapper task when the build was started with the wrapper"() {
        expect:
        wrapperExecuter.withTasks('wrapper').run()
    }

    def "prints helpful error message on invalid version argument format: #badVersion"() {
        expect:
        def failure = updateWrapperTo(badVersion).runWithFailure()

        and:
        failure.assertHasDescription("Invalid version specified for argument '--gradle-version'")
        failure.assertHasCause("'$badVersion' is not a valid Gradle version string (examples: '9.0.0', '9.1.0-rc-1')")
        assertHasResolution(failure)

        where:
        badVersion << [
            "bad-version",
            "next",
            "new",
            "5.x",
            "x.3",
            "x+1",
            "8.5.x",
            "8.5.latest",
            "later",
            "prerelease",
            "nightly-release",
            "latest-release",
            "rc",
            "current",
            "8",
            "9-rc-1",
        ]
    }

    def "can update wrapper to a new version via dynamic version #dynamicVersion"() {
        given:
        def version = "9.1.0"
        server.expect(server.get("/versions/$endpoint").send(versionJson(version)))

        when:
        updateWrapperTo(dynamicVersion).run()

        then:
        validateVersionIsUpdated(version)

        where:
        dynamicVersion      | endpoint
        "latest"            | "current"
        "release-candidate" | "release-candidate"
        "release-milestone" | "milestone"
        "release-nightly"   | "release-nightly"
        "nightly"           | "nightly"
    }

    def "can resolve semantic version"() {
        given:
        server.expect(server.get("/versions/$endpoint").send(versionsJson(versionsList)))

        when:
        updateWrapperTo(versionRequest).run()

        then:
        validateVersionIsUpdated(selectedVersion)

        where:
        versionRequest | endpoint | versionsList                                                                        | selectedVersion
        "9"            | "9"      | ["9.0.0", "9.1.0"]                                                                  | "9.1.0"
        "9"            | "9"      | ["9.0.0"]                                                                           | "9.0.0"
        "9"            | "9"      | ["9.1.1", "9.0.0", "9.1.0"]                                                         | "9.1.1"
        "9"            | "9"      | ["9.1.1", "9.2.1-rc-1", "9.3.3-milestone-2", "9.4.4-branch-XX-20121012100000+1000"] | "9.1.1"
        "9"            | "9"      | ["9.1.1", "invalid versions are ignored"]                                           | "9.1.1"
        "9"            | "9"      | ["9.0.0", "99.0.0", "10.0.0"]                                                       | "9.0.0"

        "9.1"          | "9"      | ["9.0.0", "9.1.0"]                                                                  | "9.1.0"
        "9.1"          | "9"      | ["9.1.0"]                                                                           | "9.1.0"
        "9.1"          | "9"      | ["9.1.1", "9.1.2", "9.0.0", "9.1.0"]                                                | "9.1.2"
        "9.1"          | "9"      | ["9.1.1", "9.1.2-rc-1"]                                                             | "9.1.1"
        "9.1"          | "9"      | ["9.1.1", "invalid versions are ignored"]                                           | "9.1.1"
        "9.1"          | "9"      | ["9.1.0", "99.0.0", "10.0.0", "9.11.0"]                                             | "9.1.0"
    }

    def "can handle no matching versions"() {
        given:
        server.expect(server.get("/versions/$endpoint").send(versionsJson(versionsList)))

        when:
        def failure = updateWrapperTo(versionRequest).runWithFailure()

        then:
        def description = versionRequest.contains(".") ? "version $versionRequest" : "major version $versionRequest"
        failure.assertHasCause("Invalid version specified for argument '--gradle-version': no final version found for $description")

        where:
        versionRequest | endpoint | versionsList
        "9"            | "9"      | []
        "9"            | "9"      | ["10.0.0"]
        "9"            | "9"      | ["9.2.1-rc-1", "9.3.3-milestone-2"]
        "9"            | "9"      | ["invalid versions are ignored"]

        "9.1"          | "9"      | []
        "9.1"          | "9"      | ["9.2.0"]
        "9.1"          | "9"      | ["9.1.1-rc-1", "9.1.2-rc-1", "9.1.3-milestone-2"]
        "9.1"          | "9"      | ["invalid versions are ignored"]
    }


    def "can handle missing endpoint for dynamic version"() {
        given:
        server.expect(server.get("/versions/current").missing())

        when:
        def failure = updateWrapperTo("latest").runWithFailure()

        then:
        failure.assertHasCause("Unable to resolve Gradle version for 'latest'.")
        assertHasResolution(failure)
    }

    def "can handle missing endpoint for major version"() {
        given:
        server.expect(server.get("/versions/9").missing())

        when:
        def failure = updateWrapperTo("9.1").runWithFailure()

        then:
        failure.assertHasCause("Unable to resolve list of Gradle versions for '9'.")
        assertHasResolution(failure)
    }

    def "can update exact version without additional requests"() {
        when:
        updateWrapperTo(version).run()

        then:
        validateVersionIsUpdated(version)

        where:
        version << [
            "8.14",
            "8.12.1",
            "9.0.0",
            "9.0.0-rc-1",
            "9.0.0-milestone-1",
            "9.0.0-20251220100000+0400",
            "9.0.0-branch-XX-20251220100000+1000",
            "9.0-milestone-1" // "wrong" version, but for that case it's ok to fail during download
        ]
    }

    private def updateWrapperTo(String versionRequest) {
        wrapperExecuter
            .withTasks("wrapper", "--gradle-version", versionRequest, "--no-validate-url")
    }

    private static String versionJson(String version) {
        """{ "version" : "${version}" }"""
    }

    private static def versionsJson(List<String> versions) {
        "[" + versions.collect { versionJson(it) }.join(",") + "]"
    }

    private void validateVersionIsUpdated(String version) {
        assert file("gradle/wrapper/gradle-wrapper.properties").text.contains("gradle-$version-bin.zip")
    }

    private void assertHasResolution(ExecutionFailure failure) {
        assert failure.assertHasResolution("Specify a valid Gradle release listed on https://gradle.org/releases/.")
        assert failure.assertHasResolution("Use one of the following dynamic version specifications: 'latest', 'release-candidate', 'release-milestone', 'release-nightly', 'nightly'.")
    }
}
