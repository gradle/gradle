/*
 * Copyright 2012 the original author or authors.
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
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.util.GradleVersion
import spock.lang.IgnoreIf
import spock.lang.Unroll

class CommandLineIntegrationSpec extends AbstractIntegrationSpec {
    @IgnoreIf({ AvailableJavaHomes.java5 == null })
    def "provides reasonable failure message when attempting to run under java 5"() {
        def jdk = AvailableJavaHomes.java5

        given:
        executer.withJavaHome(jdk.javaHome)

        expect:
        fails("help")
        failure.assertHasDescription("Gradle ${GradleVersion.current().version} requires Java 6 or later to run. You are currently using Java 5.")
    }

    @IgnoreIf({ GradleContextualExecuter.parallel })
    @Unroll
    def "reasonable failure message when --max-workers=#value"() {
        given:
        requireGradleHome() // otherwise exception gets thrown in testing infrastructure

        when:
        args("--max-workers=$value")

        then:
        fails "help"

        and:
        errorOutput.trim().readLines()[0] == "Argument value '$value' given for --max-workers option is invalid (must be a positive, non-zero, integer)"

        where:
        value << ["-1", "0", "foo", " 1"]
    }

    @Unroll
    def "reasonable failure message when org.gradle.workers.max=#value"() {
        given:
        requireGradleHome() // otherwise exception gets thrown in testing infrastructure

        when:
        args("-Dorg.gradle.workers.max=$value")

        then:
        fails "help"

        and:
        failure.assertHasDescription "Value '$value' given for org.gradle.workers.max system property is invalid (must be a positive, non-zero, integer)"

        where:
        value << ["-1", "0", "foo", " 1"]
    }
}
