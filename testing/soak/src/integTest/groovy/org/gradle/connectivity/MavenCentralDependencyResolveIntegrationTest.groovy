/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.connectivity

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.UnexpectedBuildFailure
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestEnvironmentPreconditions
import org.opentest4j.TestAbortedException


@Requires(TestEnvironmentPreconditions.Online)
class MavenCentralDependencyResolveIntegrationTest extends AbstractIntegrationSpec {
    def "resolves a minimal dependency from Maven Central"() {
        given:
        buildFile << """
repositories {
    mavenCentral()
    mavenCentral { // just test this syntax works.
        name = "otherCentral"
        content {
            includeGroup 'org.test'
        }
    }
}

configurations {
    compile
}

dependencies {
    compile "ch.qos.logback:logback-classic:1.0.13"
}

task check {
    doLast {
        def compile = configurations.compile
        assert compile.resolvedConfiguration.firstLevelModuleDependencies.collect { it.name } == [
            'ch.qos.logback:logback-classic:1.0.13',
        ]

        assert compile.collect { it.name } == [
            'logback-classic-1.0.13.jar',
            'logback-core-1.0.13.jar',
            'slf4j-api-1.7.5.jar'
        ]

        assert compile.resolvedConfiguration.resolvedArtifacts.collect { it.file.name } == [
            'logback-classic-1.0.13.jar',
            'logback-core-1.0.13.jar',
            'slf4j-api-1.7.5.jar'
        ]
    }
}

task repoNames {
    doLast {
        println repositories*.name
    }
}
"""

        when:
        resolveUnlessThrottled()

        then:
        outputContains(["MavenRepo", "otherCentral"].toString())
    }

    /**
     * This test exists to prove Maven Central is reachable, so it deliberately does not use the
     * repository mirror. Maven Central throttles our shared CI egress IP, and an HTTP 429 means we
     * reached it and were turned away - connectivity is fine, which is the thing under test. Treat
     * that as skipped, and keep failing for everything else: DNS, TLS, firewalls, wrong content.
     */
    private ExecutionResult resolveUnlessThrottled() {
        try {
            return succeeds("check", "repoNames")
        } catch (UnexpectedBuildFailure failure) {
            if (isThrottled(failure)) {
                throw new TestAbortedException("Maven Central answered HTTP 429; it is reachable but throttling this IP")
            }
            throw failure
        }
    }

    private static boolean isThrottled(Throwable failure) {
        def text = new StringBuilder()
        for (Throwable cause = failure; cause != null; cause = cause.cause) {
            text.append(cause.message ?: "")
            if (cause.cause === cause) {
                break
            }
        }
        def message = text.toString()
        return message.contains("Received status code 429") || message.contains("Too Many Requests")
    }
}
