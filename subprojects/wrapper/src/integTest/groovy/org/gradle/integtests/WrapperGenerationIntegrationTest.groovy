/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.UncheckedIOException
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.util.internal.TextUtil

import java.util.jar.Attributes
import java.util.jar.Manifest

class WrapperGenerationIntegrationTest extends AbstractIntegrationSpec {
    def "generated wrapper scripts use correct line separators"() {
        buildFile << """
            wrapper {
                distributionUrl = 'https://services.gradle.org/distributions'
            }
        """

        when:
        run "wrapper"

        then:
        file("gradlew").text.split(TextUtil.unixLineSeparator).length > 1
        file("gradlew").text.split(TextUtil.windowsLineSeparator).length == 1
        file("gradlew.bat").text.split(TextUtil.windowsLineSeparator).length > 1
    }

    def "wrapper jar is small"() {
        buildFile << """
            wrapper {
                distributionUrl = 'https://services.gradle.org/distributions'
            }
        """

        when:
        run "wrapper"

        then:
        // wrapper needs to be small. Let's check it's smaller than some arbitrary 'small' limit
        file("gradle/wrapper/gradle-wrapper.jar").length() < 62 * 1024
    }

    def "generated wrapper scripts for given version from command-line"() {
        when:
        run "wrapper", "--gradle-version", "2.2.1"

        then:
        file("gradle/wrapper/gradle-wrapper.properties").text.contains("distributionUrl=https\\://services.gradle.org/distributions/gradle-2.2.1-bin.zip")
    }

    def "generated wrapper files are reproducible"() {
        when:
        executer.inDirectory(file("first")).withTasks("wrapper").run()
        executer.inDirectory(file("second")).withTasks("wrapper").run()

        then: "the checksum should be constant (unless there are code changes)"
        Hashing.sha256().hashFile(file("first/gradle/wrapper/gradle-wrapper.jar")) == HashCode.fromString("db163900b4008d4556d12cd8dd312dfb5b1efdb63050db5cfec4561eb4eff495")

        and:
        file("first/gradle/wrapper/gradle-wrapper.jar").md5Hash == file("second/gradle/wrapper/gradle-wrapper.jar").md5Hash
        file("first/gradle/wrapper/gradle-wrapper.properties").md5Hash == file("second/gradle/wrapper/gradle-wrapper.properties").md5Hash
        file("first/gradlew").md5Hash == file("second/gradlew").md5Hash
        file("first/gradlew.bat").md5Hash == file("second/gradlew.bat").md5Hash
    }

    def "generated wrapper does not change unnecessarily"() {
        def wrapperJar = file("gradle/wrapper/gradle-wrapper.jar")
        def wrapperProperties = file("gradle/wrapper/gradle-wrapper.properties")
        run "wrapper", "--gradle-version", "2.2.1"
        def testFile = file("modtime").touch()
        def originalTime = testFile.lastModified()
        when:
        // Zip file time resolution is 2 seconds
        ConcurrentTestUtil.poll {
            testFile.touch()
            assert (testFile.lastModified() - originalTime) >= 2000L
        }
        run "wrapper", "--gradle-version", "2.2.1", "--rerun-tasks"

        then:
        result.assertTasksExecuted(":wrapper")
        wrapperJar.md5Hash == old(wrapperJar.md5Hash)
        wrapperProperties.text == old(wrapperProperties.text)
    }

    def "generated wrapper scripts for valid distribution types from command-line"() {
        when:
        run "wrapper", "--gradle-version", "2.13", "--distribution-type", distributionType

        then:
        file("gradle/wrapper/gradle-wrapper.properties").text.contains("distributionUrl=https\\://services.gradle.org/distributions/gradle-2.13-${distributionType}.zip")

        where:
        distributionType << ["bin", "all"]
    }

    def "no generated wrapper scripts for invalid distribution type from command-line"() {
        when:
        fails "wrapper", "--gradle-version", "2.13", "--distribution-type", "invalid-distribution-type"

        then:
        failure.assertHasCause("Cannot convert string value 'invalid-distribution-type' to an enum value of type 'org.gradle.api.tasks.wrapper.Wrapper\$DistributionType' (valid case insensitive values: BIN, ALL)")
    }

    def "generated wrapper scripts for given distribution URL from command-line"() {
        when:
        run "wrapper", "--gradle-distribution-url", "https://services.gradle.org/distributions"

        then:
        file("gradle/wrapper/gradle-wrapper.properties").text.contains("distributionUrl=https\\://services.gradle.org/distributions")
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
        file("gradle/wrapper/gradle-wrapper.jar").unzipTo(contents)

        Manifest manifest = contents.file('META-INF/MANIFEST.MF').withInputStream { new Manifest(it) } as Manifest
        with(manifest.mainAttributes) {
            size() == 2
            getValue(Attributes.Name.MANIFEST_VERSION) == '1.0'
            getValue(Attributes.Name.IMPLEMENTATION_TITLE) == 'Gradle Wrapper'
        }
    }

    def "wrapper task fails if version from command-line does not produce a valid url"() {
        when:
        run "wrapper", option, argument

        then:
        Throwable throwable = thrown()
        def exception = throwable.cause.cause.cause
        assert exception.class == UncheckedIOException.class
        assert exception.message == "Test of distribution url failed. Please check the values set with --gradle-distribution-url and --gradle-version."

        def wrappedException = exception.cause
        assert wrappedException.class == RuntimeException.class
        assert wrappedException.message == "HEAD request to " + url + " failed: response code (404)"
        file("gradle/wrapper/gradle-wrapper.properties").assertDoesNotExist()

        where:
        option                      | argument                                                    | url
        "--gradle-version"          | "8.0-RC-5"                                                  | "https://services.gradle.org/distributions/gradle-8.0-RC-5-bin.zip"
        "--gradle-distribution-url" | "https://services.gradle.org/distributions/not-a-valid-url" | "https://services.gradle.org/distributions/not-a-valid-url"

    }

    def "wrapper task with version set on command-line respects --offline mode"() {
        when:
        run "wrapper", "--gradle-version", "8.0-RC-5", "--offline"
        then:
        succeeds()
    }
}
