/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.api.Action
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.InProcessGradleExecuter
import org.gradle.internal.Actions
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.keystore.TestKeyStore
import org.gradle.test.fixtures.server.http.BlockingHttpsServer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.junit.Rule

@Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = NOT_EMBEDDED_REASON)
class WrapperAuthIntegrationTest extends AbstractWrapperIntegrationSpec {
    @Rule
    BlockingHttpsServer server = new BlockingHttpsServer()
    @Rule
    TestResources resources = new TestResources(temporaryFolder)
    TestKeyStore keyStore
    TestFile gradleUserHomeDir = testDirectory.file('user-home')
    public static final String API_TOKEN_VALID = "apiTokenValid"
    public static final String API_TOKEN_INVALID = "apiTokenInvalid"

    def assertWrapperDirectoryContent(TestFile base, String expectedFile) {
        TestFile[] files = base.file("wrapper/dists/gradle-bin").assertIsDir().listFiles()
        assert files.size() == 1:"Expected one directory in wrapper distribution directory, but found: $files"
        files.first().file(expectedFile).assertIsFile()
    }

    def assertWrapperDirectoryContentDownloaded(TestFile base = gradleUserHomeDir) {
        assertWrapperDirectoryContent(base, "gradle-bin.zip.ok")
    }

    private String gradleBinUri() {
        return server.uri("gradle-bin.zip").toString()
    }

    private GradleExecuter prepareWrapperInstaller() {
        keyStore = TestKeyStore.init(resources.dir)
        server.configure(keyStore)
        server.start()

        var wrapperInstaller = new InProcessGradleExecuter(distribution, temporaryFolder)

        // wrapperInstaller is used to download the gradle wrapper script into the test directory
        // wrapperExecuter is used to execute the help task so that the wrapper will download
        // the distribution into the test directory

        wrapperInstaller.withGradleUserHomeDir(gradleUserHomeDir)
        wrapperExecuter.withGradleUserHomeDir(gradleUserHomeDir)

        wrapperInstaller.beforeExecute { w ->
            keyStore.trustStoreArguments.each {
                w.withArgument(it)
            }
        }
        keyStore.trustStoreArguments.each {
            wrapperExecuter.withArgument(it)
        }

        wrapperInstaller.withArgument("wrapper")
        wrapperInstaller.withArgument("--gradle-distribution-url")
        wrapperInstaller.withArgument(gradleBinUri())
        wrapperExecuter.withArgument("help")

        return wrapperInstaller
    }

    private assertSucces(ExecutionResult success) {
        success.output.contains('BUILD SUCCESSFUL')
    }

    def setup() {
        assert distribution.binDistribution.exists(): "bin distribution must exist to run this test; make sure a <test>NormalizedDistribution dependency is defined."
    }

    def "download wrapper anonymously"() {
        given:
        server.expect(server.head("/gradle-bin.zip"))
        server.expect(server.get("/gradle-bin.zip").sendFile(distribution.binDistribution))

        and:
        var installer = prepareWrapperInstaller()

        and:
        // run wrapper task to install gradlew script into the test directory
        ExecutionResult result = installer.run()
        assertSucces(result)

        when:
        // use wrapperExecuter to run the wrapper task a second time
        // now the gradle wrapper script should download the wrapper distribution
        // into the test directory
        result = wrapperExecuter.run()

        then:
        assertSucces(result)

        and:
        assertWrapperDirectoryContentDownloaded()
    }

    def "download wrapper with valid api token"() {
        given:
        server.expect(server.head("/gradle-bin.zip"))
        server.expect(server.get("/gradle-bin.zip").sendFile(distribution.binDistribution))

        and:
        server.withBearerAuthentication(API_TOKEN_VALID)
        file("gradle.properties") << """
            systemProp.gradle.wrapperToken=${API_TOKEN_VALID}
        """.stripIndent()

        and:
        var installer = prepareWrapperInstaller()

        and:
        // run wrapper task to install gradlew script into the test directory
        ExecutionResult result = installer.run()
        assertSucces(result)

        when:
        // use wrapperExecuter to run the wrapper task a second time
        // now the gradle wrapper script should download the wrapper distribution
        // into the test directory
        result = wrapperExecuter.run()

        then:
        assertSucces(result)

        and:
        assertWrapperDirectoryContentDownloaded()
    }

    def "download wrapper with valid host api token"() {
        given:
        server.expect(server.head("/gradle-bin.zip"))
        server.expect(server.get("/gradle-bin.zip").sendFile(distribution.binDistribution))

        and:
        server.withBearerAuthentication(API_TOKEN_VALID)
        file("gradle.properties") << """
            systemProp.gradle.wrapperToken=${API_TOKEN_INVALID}
            systemProp.gradle.localhost.wrapperToken=${API_TOKEN_VALID}
        """.stripIndent()

        and:
        var installer = prepareWrapperInstaller()

        and:
        // run wrapper task to install gradlew script into the test directory
        ExecutionResult result = installer.run()
        assertSucces(result)

        when:
        // use wrapperExecuter to run the wrapper task a second time
        // now the gradle wrapper script should download the wrapper distribution
        // into the test directory
        result = wrapperExecuter.run()

        then:
        assertSucces(result)

        and:
        assertWrapperDirectoryContentDownloaded()
    }

    def "download wrapper with invalid api token fails"() {
        given:
        server.withBearerAuthentication(API_TOKEN_INVALID)
        file("gradle.properties") << """
            systemProp.gradle.wrapperToken=${API_TOKEN_VALID}
        """.stripIndent()

        and:
        var installer = prepareWrapperInstaller()

        when:
        // run wrapper task to install gradlew script into the test directory
        // this will fail because of invalid api token
        ExecutionFailure failure = installer.withStackTraceChecksDisabled().runWithFailure()

        then:
        assert failure.error.contains("Test of distribution url " + gradleBinUri() + " failed.")
    }
}
