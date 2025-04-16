/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.integtests.resolve

import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

/**
 * Tests a regression related to the {@link DefaultArtifactPublicationSet}
 * <p>
 * TODO: Delete this test in 9.0, as DAPS has since been removed.
 */
@Issue("https://github.com/gradle/gradle/issues/33084")
class DefaultArtifactPublicationSetRegressionTest extends AbstractIntegrationSpec {

    def "original reproducer with java plugin"() {
        buildFile << """
            plugins {
                id("java-library")
            }

            def bootJar = tasks.register("bootJar", Jar)
            def bootArchives = configurations.create("bootArchives") {
                outgoing.artifact(bootJar)
            }

            def apiElements = configurations.apiElements
            bootJar.configure {
                apiElements.outgoing.artifact(bootJar)
            }
        """

        expect:
        succeeds("assemble")
    }

    def "minimized reproducer directly referencing DAPS"() {
        buildFile << """
            plugins {
                id("base")
            }

            def jar = tasks.register("jar", Jar)
            def apiElements = configurations.create("apiElements") {
                visible = false
                outgoing.artifact(jar)
            }

            extensions.getByType(${DefaultArtifactPublicationSet.class.name}.class)
                .addCandidateInternal(apiElements.outgoing.artifacts.first())

            def bootJar = tasks.register("bootJar", Jar)
            def bootArchives = configurations.create("bootArchives") {
                outgoing.artifact(bootJar)
            }

            bootJar.configure {
                apiElements.outgoing.artifact(bootJar)
            }
        """

        expect:
        succeeds("assemble")
    }

    def "minimized reproducer with slightly different error message"() {
        buildFile << """
            plugins {
                id("base")
            }

            def bootJar = tasks.register("bootJar", Jar)
            configurations.create("bootArchives") {
                outgoing.artifact(bootJar)
            }

            def apiElements = configurations.create("apiElements") {
                visible = false
            }
            bootJar.configure {
                apiElements.outgoing.artifact(bootJar)
            }
        """

        expect:
        succeeds("assemble")
    }

    def "minimized reproducer with same error message and base plugin"() {
        buildFile << """
            plugins {
                id("base")
            }

            def jar = tasks.register("jar", Jar)
            def apiElements = configurations.create("apiElements") {
                outgoing.artifact(jar)
            }

            def bootJar = tasks.register("bootJar", Jar)
            configurations.create("bootArchives") {
                outgoing.artifact(bootJar)
            }

            bootJar.configure {
                apiElements.outgoing.artifact(bootJar)
            }
        """

        expect:
        succeeds("assemble")
    }
}
