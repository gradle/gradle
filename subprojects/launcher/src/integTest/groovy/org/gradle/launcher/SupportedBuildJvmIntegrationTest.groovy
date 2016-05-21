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

package org.gradle.launcher

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.util.GradleVersion
import spock.lang.IgnoreIf

class SupportedBuildJvmIntegrationTest extends AbstractIntegrationSpec {
    @IgnoreIf({ AvailableJavaHomes.java5 == null })
    def "provides reasonable failure message when attempting to run under java 5"() {
        def jdk = AvailableJavaHomes.java5

        given:
        executer.withJavaHome(jdk.javaHome)

        expect:
        fails("help")
        failure.assertHasDescription("Gradle ${GradleVersion.current().version} requires Java 7 or later to run. You are currently using Java 5.")
    }

    @IgnoreIf({ AvailableJavaHomes.jdk6 == null })
    def "provides reasonable failure message when attempting to run under java 6"() {
        def jdk = AvailableJavaHomes.jdk6

        given:
        executer.withJavaHome(jdk.javaHome)

        expect:
        fails("help")
        failure.assertHasDescription("Gradle ${GradleVersion.current().version} requires Java 7 or later to run. You are currently using Java 6.")
    }

    @IgnoreIf({ AvailableJavaHomes.java5 == null })
    def "fails when build is configured to use Java 5"() {
        given:
        file("gradle.properties").writeProperties("org.gradle.java.home": AvailableJavaHomes.java5.javaHome.canonicalPath)

        expect:
        fails()
        failure.assertHasDescription("Gradle ${GradleVersion.current().version} requires Java 7 or later to run. Your build is currently configured to use Java 5.")
    }

    @IgnoreIf({ AvailableJavaHomes.jdk6 == null })
    def "fails when build is configured to use Java 6"() {
        given:
        file("gradle.properties").writeProperties("org.gradle.java.home": AvailableJavaHomes.jdk6.javaHome.canonicalPath)

        expect:
        fails()
        failure.assertHasDescription("Gradle ${GradleVersion.current().version} requires Java 7 or later to run. Your build is currently configured to use Java 6.")
    }
}
