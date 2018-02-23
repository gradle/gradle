/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.internal.jvm.UnsupportedJavaRuntimeException
import org.gradle.testkit.runner.fixtures.NonCrossVersion
import spock.lang.IgnoreIf
import spock.lang.Unroll

@NonCrossVersion
class GradleRunnerDeprecatedBuildJvmIntegrationTest extends BaseGradleRunnerIntegrationTest {

    @IgnoreIf({ AvailableJavaHomes.jdk7 == null })
    @Unroll
    def "warns when build is configured to use Java 7"() {
        given:
        testDirectory.file("gradle.properties").writeProperties("org.gradle.java.home": AvailableJavaHomes.jdk7.javaHome.absolutePath)

        when:
        def result = runner().withArguments('--daemon').withDebug(debugFlag).build()

        then:
        result.output.count(UnsupportedJavaRuntimeException.JAVA7_DEPRECATION_WARNING) == 1

        where:
        debugFlag | _
        true      | _
        false     | _
    }

    @IgnoreIf({ AvailableJavaHomes.jdk8 == null })
    @Unroll
    def "no warns when build is configured to use Java 8"() {
        given:
        testDirectory.file("gradle.properties").writeProperties("org.gradle.java.home": AvailableJavaHomes.jdk8.javaHome.absolutePath)

        when:
        def result = runner().withArguments('--daemon').withDebug(debugFlag).build()

        then:
        result.output.count(UnsupportedJavaRuntimeException.JAVA7_DEPRECATION_WARNING) == 0

        where:
        debugFlag | _
        true      | _
        false     | _
    }
}
