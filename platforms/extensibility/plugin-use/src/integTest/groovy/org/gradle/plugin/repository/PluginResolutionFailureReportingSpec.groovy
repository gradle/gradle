/*
 * Copyright 2026 Gradle and contributors.
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

package org.gradle.plugin.repository

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.test.fixtures.server.http.MavenHttpRepository

class PluginResolutionFailureReportingSpec extends AbstractHttpDependencyResolutionTest {

    private static final String PLUGIN_ID = "org.test.foo"
    private static final String PLUGIN_VERSION = "1.0"

    def setup() {
        buildFile """
            plugins {
                id "$PLUGIN_ID" version "$PLUGIN_VERSION"
            }
"""
    }

    private void usePluginRepositories(MavenHttpRepository... repositories) {
        settingsFile << """
            pluginManagement {
                repositories {
                    ${repositories.collect { "maven { url = '${it.uri}' }" }.join("\n")}
                }
            }
"""
    }

    private marker(MavenHttpRepository repo) {
        repo.module(PLUGIN_ID, "${PLUGIN_ID}.gradle.plugin", PLUGIN_VERSION)
    }

    private void markerBroken(MavenHttpRepository repo) {
        marker(repo).pom.expectGetBroken()
    }

    private void markerMissing(MavenHttpRepository repo) {
        // Only the POM is requested. Resolution stops as soon as the marker is reported missing.
        marker(repo).pom.expectGetMissing()
    }

    def "reports the underlying failure when the plugin repository cannot be reached"() {
        given:
        def repo = mavenHttpRepo("broken")
        usePluginRepositories(repo)
        markerBroken(repo)

        when:
        fails("help")

        then:
        failureDescriptionContains("could not resolve plugin artifact")
        failureCauseContains("Could not GET")
    }

    def "does not report resolution failures when plugin is simply missing"() {
        given:
        def repo = mavenHttpRepo("empty")
        usePluginRepositories(repo)
        markerMissing(repo)

        when:
        fails("help")

        then:
        failureDescriptionContains("could not resolve plugin artifact")
        failure.assertHasNoCause()
    }

    def "reports a broken repository that is searched after one simply missing the plugin"() {
        given:
        def fizzRepo = mavenHttpRepo("fizz")
        def buzzRepo = mavenHttpRepo("buzz")
        usePluginRepositories(fizzRepo, buzzRepo)
        markerMissing(fizzRepo)
        markerBroken(buzzRepo)

        when:
        fails("help")

        then:
        failureDescriptionContains("Searched in the following repositories")
        failureCauseContains("Could not GET '${buzzRepo.uri}")
    }

    def "reports the underlying failure when the repository host is unreachable"() {
        given:
        def repo = mavenHttpRepo("gone")
        usePluginRepositories(repo)
        server.stop()

        when:
        fails("help")

        then:
        failureDescriptionContains("could not resolve plugin artifact")
        failureCauseContains("Connection refused")
    }

    def "reports the underlying failure when the repository rejects credentials"() {
        given:
        def repo = mavenHttpRepo("secured")
        usePluginRepositories(repo)
        marker(repo).pom.expectGetUnauthorized()

        when:
        fails("help")

        then:
        failureDescriptionContains("could not resolve plugin artifact")
        failureCauseContains("Could not GET '${repo.uri}")
        failureCauseContains("status code 401")
    }

    def "stops at the first repository that could not be searched"() {
        given:
        def firstRepo = mavenHttpRepo("first")
        def secondRepo = mavenHttpRepo("second")
        usePluginRepositories(firstRepo, secondRepo)
        markerBroken(firstRepo)
        // No expectation on the second repository. A failure that disables one clears the queue,
        // so the second is never queried, even though it is still listed as searched.

        when:
        fails("help")

        then:
        failureCauseContains("Could not GET '${firstRepo.uri}")
        !failure.output.contains("Could not GET '${secondRepo.uri}")
    }
}
