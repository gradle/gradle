/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.UnexpectedBuildFailure
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.archive.ZipTestFixture
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.util.internal.TextUtil
import org.junit.Rule
import spock.lang.Issue

import java.util.jar.Attributes
import java.util.jar.Manifest

import static org.hamcrest.CoreMatchers.containsString

class WrapperGenerationIntegrationTest extends AbstractIntegrationSpec {
    private static final HashCode EXPECTED_WRAPPER_JAR_HASH = HashCode.fromString("498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17")

    def "generated wrapper scripts use correct line separators"() {
        buildFile << """
            wrapper {
                distributionUrl = 'http://localhost:8080/gradlew/dist'
            }
        """

        when:
        run "wrapper", "--no-validate-url"

        then:
        file("gradlew").text.split(TextUtil.unixLineSeparator).length > 1
        file("gradlew").text.split(TextUtil.windowsLineSeparator).length == 1
        file("gradlew.bat").text.split(TextUtil.windowsLineSeparator).length > 1
    }

    def "wrapper jar is small"() {
        buildFile << """
            wrapper {
                distributionUrl = 'http://localhost:8080/gradlew/dist'
            }
        """

        when:
        run "wrapper", "--no-validate-url"

        then:
        // wrapper needs to be small. Let's check it's smaller than some arbitrary 'small' limit
        file("gradle/wrapper/gradle-wrapper.jar").length() < 46 * 1024
    }

    def "wrapper jar has LICENSE file"() {
        buildFile << """
            wrapper {
                distributionUrl = 'http://localhost:8080/gradlew/dist'
            }
        """

        when:
        run "wrapper", "--no-validate-url"

        then:
        new ZipTestFixture(file("gradle/wrapper/gradle-wrapper.jar"))
            .assertFileContent("META-INF/LICENSE", containsString("Apache License"))
    }

    def "generated wrapper scripts for given version from command-line"() {
        when:
        run "wrapper", "--gradle-version", "2.2.1", "--no-validate-url"

        then:
        file("gradle/wrapper/gradle-wrapper.properties").text.contains("distributionUrl=https\\://services.gradle.org/distributions/gradle-2.2.1-bin.zip")
    }

    def "generated wrapper files are reproducible"() {
        when:
        executer.inDirectory(file("first")).withTasks("wrapper").run()
        executer.inDirectory(file("second")).withTasks("wrapper").run()

        then: "the checksum should be constant (unless there are code changes)"
        Hashing.sha256().hashFile(file("first/gradle/wrapper/gradle-wrapper.jar")) == EXPECTED_WRAPPER_JAR_HASH

        and:
        file("first/gradle/wrapper/gradle-wrapper.jar").md5Hash == file("second/gradle/wrapper/gradle-wrapper.jar").md5Hash
        file("first/gradle/wrapper/gradle-wrapper.properties").md5Hash == file("second/gradle/wrapper/gradle-wrapper.properties").md5Hash
        file("first/gradlew").md5Hash == file("second/gradlew").md5Hash
        file("first/gradlew.bat").md5Hash == file("second/gradlew.bat").md5Hash
    }

    def "generated wrapper does not change unnecessarily"() {
        def wrapperJar = file("gradle/wrapper/gradle-wrapper.jar")
        def wrapperProperties = file("gradle/wrapper/gradle-wrapper.properties")
        run "wrapper", "--gradle-version", "2.2.1", "--no-validate-url"
        def testFile = file("modtime").touch()
        def originalTime = testFile.lastModified()
        when:
        // Zip file time resolution is 2 seconds
        ConcurrentTestUtil.poll {
            testFile.touch()
            assert (testFile.lastModified() - originalTime) >= 2000L
        }
        run "wrapper", "--gradle-version", "2.2.1", "--rerun-tasks", "--no-validate-url"

        then:
        result.assertTasksExecuted(":wrapper")
        wrapperJar.md5Hash == old(wrapperJar.md5Hash)
        wrapperProperties.text == old(wrapperProperties.text)
    }

    def "generated wrapper scripts for valid distribution types from command-line"() {
        when:
        run "wrapper", "--gradle-version", "2.13", "--distribution-type", distributionType, "--no-validate-url"

        then:
        file("gradle/wrapper/gradle-wrapper.properties").text.contains("distributionUrl=https\\://services.gradle.org/distributions/gradle-2.13-${distributionType}.zip")

        where:
        distributionType << ["bin", "all"]
    }

    def "no generated wrapper scripts for invalid distribution type from command-line"() {
        when:
        fails "wrapper", "--gradle-version", "2.13", "--distribution-type", "invalid-distribution-type", "--no-validate-url"

        then:
        failure.assertHasCause("Cannot convert string value 'invalid-distribution-type' to an enum value of type 'org.gradle.api.tasks.wrapper.Wrapper\$DistributionType' (valid case insensitive values: BIN, ALL)")
    }

    def "generated wrapper scripts for given distribution URL from command-line"() {
        when:
        run "wrapper", "--gradle-distribution-url", "http://localhost:8080/gradlew/dist", "--no-validate-url"

        then:
        file("gradle/wrapper/gradle-wrapper.properties").text.contains("distributionUrl=http\\://localhost\\:8080/gradlew/dist")
    }

    def "generated wrapper scripts for given distribution SHA-256 hash sum from command-line"() {
        when:
        run "wrapper", "--gradle-distribution-sha256-sum", "somehash"

        then:
        file("gradle/wrapper/gradle-wrapper.properties").text.contains("distributionSha256Sum=somehash")
    }

    def "generated wrapper scripts for given network timeout from command-line"() {
        when:
        run "wrapper", "--network-timeout", "7000"

        then:
        file("gradle/wrapper/gradle-wrapper.properties").text.contains("networkTimeout=7000")
    }

    def "wrapper JAR does not contain version in manifest"() {
        when:
        run "wrapper"

        then:
        def contents = file('contents')
        // ProGuard removes parent directory entries to keep JARs smaller
        file("gradle/wrapper/gradle-wrapper.jar").unzipToWithoutCheckingParentDirs(contents)

        Manifest manifest = contents.file('META-INF/MANIFEST.MF').withInputStream { new Manifest(it) } as Manifest
        with(manifest.mainAttributes) {
            size() == 2
            getValue(Attributes.Name.MANIFEST_VERSION) == '1.0'
            getValue(Attributes.Name.IMPLEMENTATION_TITLE) == 'Gradle Wrapper'
        }
    }

    @Rule
    HttpServer httpServer = new HttpServer()

    @Requires(IntegTestPreconditions.NotEmbeddedExecutor)
    def "wrapper task fails if http distribution url from command-line is invalid"() {
        given:
        def path = "/distributions/8.0-RC-5"
        httpServer.start()
        httpServer.expectHeadMissing(path)
        def url = "${httpServer.uri}" + path

        when:
        run "wrapper", "--gradle-distribution-url", url

        then:
        Throwable throwable = thrown(UnexpectedBuildFailure.class)
        assert throwable.message.contains("Test of distribution url ${url} failed. Please check the values set with --gradle-distribution-url and --gradle-version.")
        file("gradle/wrapper/gradle-wrapper.properties").assertDoesNotExist()
    }

    def "wrapper task succeeds if http distribution url from command-line is valid"() {
        given:
        def path = "/distributions/8.0-rc-5"
        def file = file(path) << "some content"
        httpServer.start()
        httpServer.expectHead(path, file)
        def url = "${httpServer.uri}" + path

        when:
        run "wrapper", "--gradle-distribution-url", url

        then:
        succeeds()
    }

    @Requires(IntegTestPreconditions.NotEmbeddedExecutor)
    def "wrapper task fails if file distribution url from command-line is invalid"() {
        given:
        def target = file("/distributions/8.0-rc-5")
        def url = target.toURI().toString()
        target.delete()
        target.assertDoesNotExist()

        when:
        run "wrapper", "--gradle-distribution-url", url

        then:
        Throwable throwable = thrown(UnexpectedBuildFailure.class)
        assert throwable.message.contains("Test of distribution url ${url} failed. Please check the values set with --gradle-distribution-url and --gradle-version.")
        file("gradle/wrapper/gradle-wrapper.properties").assertDoesNotExist()
    }

    def "wrapper task succeeds if file distribution url from command-line is valid"() {
        given:
        def target = file("/distributions/8.0-rc-5") << "some content"
        def url = target.toURI().toString()

        when:
        run "wrapper", "--gradle-distribution-url", url

        then:
        succeeds()
    }

    def "wrapper task with distribution url from command-line respects --offline"() {
        httpServer.start()
        def path = "/distributions/8.0-RC-5"
        def url = "${httpServer.uri}" + path
        when:
        run("wrapper", "--gradle-distribution-url", "${url}", "--offline")

        then:
        succeeds()
    }

    def "wrapper task with distribution url from command-line respects --no-validate-url"() {
        httpServer.start()
        def path = "/distributions/8.0-RC-5"
        def url = "${httpServer.uri}" + path
        when:
        run("wrapper", "--gradle-distribution-url", "${url}", "--no-validate-url")

        then:
        succeeds()
    }

    def "wrapper task with distribution url from command-line respects --validate-url"() {
        given:
        def target = file("/distributions/8.0-rc-5") << "some content"
        def url = target.toURI().toString()

        when:
        run("wrapper", "--gradle-distribution-url", url, "--validate-url")

        then:
        succeeds()
    }

    @Issue('https://github.com/gradle/gradle/issues/25252')
    def "wrapper task succeeds if distribution url from command-line results in relative uri (no scheme)"() {
        given:
        file("gradle/wrapper/../distributions/8.0-rc-5") << "some content"

        def url = "../distributions/8.0-rc-5"

        when:
        run "wrapper", "--gradle-distribution-url", url

        then:
        succeeds()
    }
}
