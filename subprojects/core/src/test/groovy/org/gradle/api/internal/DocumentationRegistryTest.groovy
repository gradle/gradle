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

package org.gradle.api.internal

import spock.lang.Specification
import org.junit.Rule
import org.gradle.util.TemporaryFolder
import org.gradle.util.GradleVersion

class DocumentationRegistryTest extends Specification {
    @Rule TemporaryFolder tmpDir
    final GradleDistributionLocator locator = Mock()
    final GradleVersion gradleVersion = GradleVersion.current()
    final DocumentationRegistry registry = new DocumentationRegistry(locator, gradleVersion)


    def "points users at the local user guide when target page is present in distribution"() {
        def distDir = tmpDir.createDir("home")
        distDir.createFile("docs/userguide/userguide.html")
        def daemonPage = distDir.createFile("docs/userguide/gradle_daemon.html")

        given:
        _ * locator.gradleHome >> distDir

        expect:
        registry.getDocumentationFor('gradle_daemon') == daemonPage.absolutePath
    }

    def "fails when local user guide is present in distribution but target page not found"() {
        def distDir = tmpDir.createDir("home")
        distDir.createFile("docs/userguide/userguide.html")
        def expectedPage = distDir.file("docs/userguide/gradle_daemon.html")

        given:
        _ * locator.gradleHome >> distDir

        when:
        registry.getDocumentationFor('gradle_daemon')

        then:
        IllegalArgumentException e = thrown()
        e.message == "User guide page '${expectedPage}' not found."
    }

    def "points users at the remote user guide when user guide not present in distribution"() {
        def distDir = tmpDir.createDir("home")

        given:
        _ * locator.gradleHome >> distDir

        expect:
        registry.getDocumentationFor('gradle_daemon') == "http://gradle.org/docs/${gradleVersion.version}/userguide/gradle_daemon.html"
    }

    def "points users at the remote user guide when no distribution"() {
        given:
        _ * locator.gradleHome >> null

        expect:
        registry.getDocumentationFor('gradle_daemon') == "http://gradle.org/docs/${gradleVersion.version}/userguide/gradle_daemon.html"
    }
}
