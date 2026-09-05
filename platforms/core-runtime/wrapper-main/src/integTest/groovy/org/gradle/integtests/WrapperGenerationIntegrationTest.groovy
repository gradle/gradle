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
import org.gradle.test.preconditions.TestExecutionPreconditions
import org.gradle.util.internal.TextUtil
import org.junit.Rule
import spock.lang.Issue

import java.nio.charset.StandardCharsets
import java.util.jar.Attributes
import java.util.jar.Manifest

import static org.hamcrest.CoreMatchers.containsString

class WrapperGenerationIntegrationTest extends AbstractIntegrationSpec {
    private static final HashCode EXPECTED_WRAPPER_JAR_HASH = HashCode.fromString("f5d7c54844d73dcd7c6242b886093763a0167afe78329083a90916f572fab2fd")

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

    @Issue('https://github.com/gradle/gradle/issues/35905')
    def "generated wrapper script contains correct application name"() {
        buildFile << """
            wrapper {
                distributionUrl = 'http://localhost:8080/gradlew/dist'
            }
        """

        when:
        run "wrapper", "--no-validate-url"

        then:
        file("gradlew").text.contains("ksh gradlew")
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
        file("gradle/wrapper/gradle-wrapper.jar").length() < 52 * 1024
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

    // These are the byte offsets that 9.0-9.7 executions might land around, so the safety net
    // must contain them.
    private static final int MAX_SAFETY_NET_FILLER_START = 2486
    private static final int MIN_SAFETY_NET_TERMINATOR_START = 2737

    @Issue("https://github.com/gradle/gradle/issues/38082")
    def "generated batch script contains the overwrite safety net"() {
        when:
        run "wrapper", "--no-validate-url"

        then:
        byte[] bytes = file("gradlew.bat").bytes
        String text = new String(bytes, StandardCharsets.US_ASCII)
        bytes.length == text.length()

        and:
        String skipLine = 'goto afterSafetyNet\r\n'
        String net = skipLine +
            (':' * 78 + '\r\n') * 20 +
            'goto exitWithErrorLevel\r\n' +
            ':afterSafetyNet\r\n'
        int netStart = text.indexOf(net)
        netStart > 0

        and:
        int fillerStart = netStart + skipLine.length()
        int terminatorStart = text.indexOf('goto exitWithErrorLevel\r\n', fillerStart)
        fillerStart <= MAX_SAFETY_NET_FILLER_START
        terminatorStart >= MIN_SAFETY_NET_TERMINATOR_START
    }

    // NOTE: this test will fail on any changes to wrapper code
    // If your changes do relate to wrapper, just change the hash.
    // Otherwise, investigate.
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
        result.assertTasksScheduled(":wrapper")
        wrapperJar.md5Hash == old(wrapperJar.md5Hash)
        wrapperProperties.text == old(wrapperProperties.text)
    }

    def "wrapper preserves existing properties and updates the distribution URL"() {
        given:
        file("gradle/wrapper").mkdirs()
        file("gradle/wrapper/gradle-wrapper.properties").text = """distributionUrl=https\\://services.gradle.org/distributions/gradle-2.12-all.zip
networkTimeout=20000
validateDistributionUrl=false
retries=5
retryBackOffMs=1500
"""

        when:
        run "wrapper", "--gradle-version", "2.13", "--no-validate-url"

        then:
        file("gradle/wrapper/gradle-wrapper.properties").text.contains("distributionUrl=https\\://services.gradle.org/distributions/gradle-2.13-all.zip")
        file("gradle/wrapper/gradle-wrapper.properties").text.contains("networkTimeout=20000")
        file("gradle/wrapper/gradle-wrapper.properties").text.contains("validateDistributionUrl=false")
        file("gradle/wrapper/gradle-wrapper.properties").text.contains("retries=5")
        file("gradle/wrapper/gradle-wrapper.properties").text.contains("retryBackOffMs=1500")
    }

    def "wrapper preserves existing properties next to a relocated wrapper jar"() {
        given:
        buildFile << """
            wrapper {
                jarFile = file("custom/gradle-wrapper.jar")
            }
        """
        file("custom").mkdirs()
        file("custom/gradle-wrapper.properties").text = "networkTimeout=20000\n"

        when:
        run "wrapper", "--gradle-version", "2.13", "--no-validate-url"

        then:
        file("custom/gradle-wrapper.properties").text.contains("networkTimeout=20000")
        file("gradle/wrapper/gradle-wrapper.properties").assertDoesNotExist()
    }

    def "explicit wrapper properties override existing values"() {
        given:
        buildFile << """
            wrapper {
                distributionUrl = 'http://localhost:8080/gradlew/dist'
                networkTimeout = 30000
                distributionBase = Wrapper.PathBase.GRADLE_USER_HOME
            }
        """
        file("gradle/wrapper").mkdirs()
        file("gradle/wrapper/gradle-wrapper.properties").text = """distributionUrl=http\\://localhost\\:8080/gradlew/dist
networkTimeout=20000
distributionBase=PROJECT
"""

        when:
        run "wrapper", "--no-validate-url"

        then:
        file("gradle/wrapper/gradle-wrapper.properties").text.contains("networkTimeout=30000")
        file("gradle/wrapper/gradle-wrapper.properties").text.contains("distributionBase=GRADLE_USER_HOME")
    }

    def "configuration cache does not overwrite edits to existing wrapper properties"() {
        given:
        buildFile << """
            wrapper {
                distributionUrl = 'http://localhost:8080/gradlew/dist'
            }
        """
        file("gradle/wrapper").mkdirs()
        file("gradle/wrapper/gradle-wrapper.properties").text = "networkTimeout=20000\n"

        when:
        run "wrapper", "--configuration-cache", "--no-validate-url"
        file("gradle/wrapper/gradle-wrapper.properties").text = "networkTimeout=30000\n"
        run "wrapper", "--configuration-cache", "--no-validate-url"

        then:
        file("gradle/wrapper/gradle-wrapper.properties").text.contains("networkTimeout=30000")
    }

    def "explicit wrapper property equal to the default is not skipped as up-to-date"() {
        given:
        file("gradle/wrapper").mkdirs()
        file("gradle/wrapper/gradle-wrapper.properties").text = "distributionBase=PROJECT\n"

        when:
        run "wrapper", "--no-validate-url"
        buildFile << """
            tasks.wrapper {
                distributionBase = Wrapper.PathBase.GRADLE_USER_HOME
            }
        """
        run "wrapper", "--no-validate-url"

        then:
        file("gradle/wrapper/gradle-wrapper.properties").text.contains("distributionBase=GRADLE_USER_HOME")
    }

    def "malformed existing wrapper property identifies the file and property"() {
        given:
        buildFile << """
            wrapper {
                distributionUrl = 'http://localhost:8080/gradlew/dist'
            }
        """
        file("gradle/wrapper").mkdirs()
        file("gradle/wrapper/gradle-wrapper.properties").text = "networkTimeout=invalid\n"

        when:
        fails "wrapper", "--no-validate-url"

        then:
        failure.assertHasCause("Invalid value 'invalid' for property 'networkTimeout'")
        failure.assertThatCause(containsString("gradle-wrapper.properties"))
    }

    def "malformed existing path base identifies the file and property"() {
        given:
        buildFile << """
            wrapper {
                distributionUrl = 'http://localhost:8080/gradlew/dist'
            }
        """
        file("gradle/wrapper").mkdirs()
        file("gradle/wrapper/gradle-wrapper.properties").text = "distributionBase=invalid\n"

        when:
        fails "wrapper", "--no-validate-url"

        then:
        failure.assertHasCause("Invalid value 'invalid' for property 'distributionBase'")
        failure.assertThatCause(containsString("gradle-wrapper.properties"))
    }

    def "malformed existing boolean property identifies the file and property"() {
        given:
        buildFile << """
            wrapper {
                distributionUrl = 'http://localhost:8080/gradlew/dist'
            }
        """
        file("gradle/wrapper").mkdirs()
        file("gradle/wrapper/gradle-wrapper.properties").text = "validateDistributionUrl=invalid\n"

        when:
        fails "wrapper", "--offline"

        then:
        failure.assertHasCause("Invalid value 'invalid' for property 'validateDistributionUrl'")
        failure.assertThatCause(containsString("gradle-wrapper.properties"))
    }

    def "existing path bases are normalized to the values understood by the wrapper runtime"() {
        given:
        file("gradle/wrapper").mkdirs()
        file("gradle/wrapper/gradle-wrapper.properties").text = "distributionBase=project\nzipStoreBase=gradle_user_home\n"

        when:
        run "wrapper", "--no-validate-url"

        then:
        file("gradle/wrapper/gradle-wrapper.properties").text.contains("distributionBase=PROJECT")
        file("gradle/wrapper/gradle-wrapper.properties").text.contains("zipStoreBase=GRADLE_USER_HOME")
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

    def "generated wrapper scripts for given retries from command-line"() {
        when:
        run "wrapper", "--retries", "5"

        then:
        file("gradle/wrapper/gradle-wrapper.properties").text.contains("retries=5")
    }

    def "generated wrapper scripts for given retry back off from command-line"() {
        when:
        run "wrapper", "--retry-back-off-ms", "1000"

        then:
        file("gradle/wrapper/gradle-wrapper.properties").text.contains("retryBackOffMs=1000")
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
            size() == 7
            getValue(Attributes.Name.MANIFEST_VERSION) == '1.0'
            getValue(Attributes.Name.IMPLEMENTATION_TITLE) == 'Gradle Wrapper'
            getValue(Attributes.Name.MAIN_CLASS) == org.gradle.wrapper.GradleWrapperMain.class.getName()
            getValue(Attributes.Name.IMPLEMENTATION_VENDOR) == 'Gradle Inc.'
            getValue("Implementation-Vendor-Id") == "org.gradle"
            getValue("SPDX-License-Identifier") == "Apache-2.0"
            getValue("Enable-Native-Access") == "ALL-UNNAMED"
        }
    }

    @Rule
    HttpServer httpServer = new HttpServer()

    @Requires(TestExecutionPreconditions.NotEmbeddedExecutor)
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

    @Requires(TestExecutionPreconditions.NotEmbeddedExecutor)
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
