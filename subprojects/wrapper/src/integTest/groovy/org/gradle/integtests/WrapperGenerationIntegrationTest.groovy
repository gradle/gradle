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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.TextUtil

class WrapperGenerationIntegrationTest extends AbstractIntegrationSpec {
    def "generated wrapper scripts use correct line separators"() {
        buildFile << """
            wrapper {
                distributionUrl = 'http://localhost:8080/gradlew/dist'
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
                distributionUrl = 'http://localhost:8080/gradlew/dist'
            }
        """

        when:
        run "wrapper"

        then:
        // wrapper needs to be small. Let's check it's smaller than some arbitrary 'small' limit
        file("gradle/wrapper/gradle-wrapper.jar").length() < 54 * 1024
    }

    def "generated wrapper scripts for given version from command-line"() {
        when:
        run "wrapper", "--gradle-version", "2.2.1"

        then:
        file("gradle/wrapper/gradle-wrapper.properties").text.contains("distributionUrl=https\\://services.gradle.org/distributions/gradle-2.2.1-bin.zip")
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
        run "wrapper", "--gradle-distribution-url", "http://localhost:8080/gradlew/dist"

        then:
        file("gradle/wrapper/gradle-wrapper.properties").text.contains("distributionUrl=http\\://localhost\\:8080/gradlew/dist")
    }
}
