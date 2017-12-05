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

package org.gradle.launcher

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.jvm.UnsupportedJavaRuntimeException
import org.gradle.util.Requires
import spock.lang.IgnoreIf

@Requires(adhoc = { AvailableJavaHomes.getJdks("1.7", "1.8") })
class DeprecatedBuildIntegrationTest extends AbstractIntegrationSpec {
    @IgnoreIf({ AvailableJavaHomes.jdk7 == null })
    def "warns of deprecated java version when running under java 7"() {
        given:
        executer.withJavaHome(AvailableJavaHomes.jdk7.javaHome)

        expect:
        run("help")
        warningCount() == 1
    }

    @IgnoreIf({ AvailableJavaHomes.jdk7 == null })
    def "warns of deprecate java version when build is configured to use java 7"() {
        given:
        file("gradle.properties").writeProperties("org.gradle.java.home": AvailableJavaHomes.jdk7.javaHome.canonicalPath)

        expect:
        run("help")
        warningCount() == 1
    }

    @IgnoreIf({ AvailableJavaHomes.jdk8 == null })
    def "no warns of deprecated java version when running under java 8"() {
        given:
        executer.withJavaHome(AvailableJavaHomes.jdk8.javaHome)

        expect:
        run("help")
        warningCount() == 0
    }

    @IgnoreIf({ AvailableJavaHomes.jdk8 == null })
    def "warns of deprecate java version when build is configured to use java 8"() {
        given:
        file("gradle.properties").writeProperties("org.gradle.java.home": AvailableJavaHomes.jdk8.javaHome.canonicalPath)

        expect:
        run("help")
        warningCount() == 0
    }

    def warningCount() {
        return result.deprecationReport.count(UnsupportedJavaRuntimeException.JAVA7_DEPRECATION_WARNING)
    }
}
