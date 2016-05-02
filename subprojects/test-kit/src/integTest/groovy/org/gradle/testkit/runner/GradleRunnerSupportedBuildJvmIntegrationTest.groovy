/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.testkit.runner

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.testkit.runner.fixtures.NoDebug
import org.gradle.testkit.runner.fixtures.NonCrossVersion
import spock.lang.IgnoreIf

@NonCrossVersion
class GradleRunnerSupportedBuildJvmIntegrationTest extends BaseGradleRunnerIntegrationTest {
    @IgnoreIf({AvailableJavaHomes.jdk6 == null})
    @NoDebug
    def "warns when build is configured to use Java 6"() {
        given:
        testDirectory.file("gradle.properties").writeProperties("org.gradle.java.home": AvailableJavaHomes.jdk6.javaHome.absolutePath)

        when:
        def result = runner().build()

        then:
        result.output.contains("Support for running Gradle using Java 6 has been deprecated and will be removed in Gradle 3.0")
    }
}
