/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.buildinit.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class WrapperLazyPropertiesIntegrationTest extends AbstractIntegrationSpec {

    private Properties wrapperProperties(String path = "gradle/wrapper/gradle-wrapper.properties") {
        def properties = new Properties()
        file(path).withInputStream { properties.load(it) }
        properties
    }

    def "invalid --gradle-version fails as before"() {
        when:
        fails "wrapper", "--gradle-version", "bad-version"

        then:
        failure.assertHasDescription("Invalid version specified for argument '--gradle-version'")
        failure.assertHasCause("'bad-version' is not a valid Gradle version string (examples: '9.0.0', '9.1.0-rc-1')")
        failure.assertHasResolution("Specify a valid Gradle release listed on https://gradle.org/releases/.")
    }

    def "invalid gradleVersion bound lazily fails at execution with the same message"() {
        buildFile """
            tasks.wrapper {
                lazyProperties.gradleVersion = providers.provider { "bad-version" }
            }
        """

        when:
        fails "wrapper"

        then:
        failure.assertHasDescription("Execution failed for task ':wrapper'")
        failure.assertHasCause("Error while evaluating property 'distributionUrl' of task ':wrapper'.")
        failure.assertHasCause("Invalid version specified for argument '--gradle-version'")
        failure.assertHasResolution("Specify a valid Gradle release listed on https://gradle.org/releases/.")
    }

    def "options set their values"() {
        when:
        run "wrapper", "--gradle-version", "9.0.0", "--distribution-type", "all",
            "--gradle-distribution-sha256-sum", "abc123", "--network-timeout", "7000", "--retries", "4", "--retry-back-off-ms", "300", "--no-validate-url"

        then:
        def properties = wrapperProperties()
        properties.distributionUrl == "https://services.gradle.org/distributions/gradle-9.0.0-all.zip"
        properties.distributionSha256Sum == "abc123"
        properties.networkTimeout == "7000"
        properties.retries == "4"
        properties.retryBackOffMs == "300"
        properties.validateDistributionUrl == "false"

        when:
        run "wrapper", "--gradle-distribution-url", "https://example.invalid/custom.zip", "--no-validate-url"

        then:
        wrapperProperties().distributionUrl == "https://example.invalid/custom.zip"
    }

    def "values of an existing properties file are kept when nothing is set and replaced by the setter"() {
        file("gradle/wrapper/gradle-wrapper.properties").text = """
            distributionBase=PROJECT
            distributionPath=custom/dists
            zipStoreBase=PROJECT
            zipStorePath=custom/zips
        """.stripIndent()

        when:
        run "wrapper"

        then:
        with(wrapperProperties()) {
            distributionBase == "PROJECT"
            distributionPath == "custom/dists"
            zipStoreBase == "PROJECT"
            zipStorePath == "custom/zips"
        }

        when:
        buildFile """
            tasks.wrapper {
                distributionPath = "other/dists"
                archiveBase = Wrapper.PathBase.GRADLE_USER_HOME
            }
        """
        run "wrapper"

        then:
        with(wrapperProperties()) {
            distributionBase == "PROJECT"
            distributionPath == "other/dists"
            zipStoreBase == "GRADLE_USER_HOME"
            zipStorePath == "custom/zips"
        }
    }

    def "jarFile and scriptFile accept #description"() {
        buildFile """
            tasks.wrapper {
                jarFile = $jarFile
                scriptFile = $scriptFile
            }
        """

        when:
        run "wrapper"

        then:
        file("custom/wrapper.jar").exists()
        file("custom/wrapper.properties").exists()
        file("scripts/gradlew").exists()
        file("scripts/gradlew.bat").exists()

        where:
        description | jarFile                       | scriptFile
        "a String"  | '"custom/wrapper.jar"'        | '"scripts/gradlew"'
        "a File"    | 'file("custom/wrapper.jar")'  | 'file("scripts/gradlew")'
    }

    def "scriptFile set to a live object is resolved when it is read"() {
        buildFile """
            def name = "first"
            tasks.wrapper {
                scriptFile = { "\$name/gradlew" }
            }
            name = "second"
        """

        when:
        run "wrapper"

        then:
        file("second/gradlew").exists()
        !file("first/gradlew").exists()
    }

    def "distributionUrl bound lazily reaches the generated properties file"() {
        buildFile """
            tasks.wrapper {
                validateDistributionUrl = false
                lazyProperties.distributionUrl = providers.gradleProperty("distUrl")
            }
        """

        when:
        run "wrapper", "-PdistUrl=https://example.invalid/lazy.zip"

        then:
        wrapperProperties().distributionUrl == "https://example.invalid/lazy.zip"

        when:
        run "wrapper", "-PdistUrl=https://example.invalid/other.zip"

        then:
        wrapperProperties().distributionUrl == "https://example.invalid/other.zip"
    }
}
