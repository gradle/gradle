/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest

class FlatDirJvmLibraryArtifactResolutionIntegrationTest extends AbstractDependencyResolutionTest {
    JvmLibraryArtifactResolveTestFixture fixture

    def setup() {
        buildFile << """
repositories {
    flatDir { dir 'repo' }
}
"""
        fixture = new JvmLibraryArtifactResolveTestFixture(buildFile)
    }

    def "resolves and does not cache source and javadoc artifacts"() {
        publishModule()
        fixture.expectSourceArtifact("sources")
                .expectJavadocArtifact("javadoc")
                .prepare()

        when:
        succeeds("verify")

        and:
        def snapshot = file("sources/some-artifact-1.0-sources.jar").snapshot()

        and:
        publishChanged()

        then:
        succeeds("verify")
        file("sources/some-artifact-1.0-sources.jar").assertHasChangedSince(snapshot)
    }

    def "resolves artifacts of non-existing component"() {
        def location1 = file("repo/some-artifact-1.0.jar").toURL()
        def location2 = file("repo/some-artifact.jar").toURL()

        fixture.expectComponentNotFound().prepare()

        expect:
        fails("verify")
        failure.assertHasCause("""Could not find some.group:some-artifact:1.0.
Searched in the following locations:
  - ${location1}
  - ${location2}""")
    }

    def "resolve missing source and javadoc artifacts"() {
        file("repo/some-artifact-1.0.jar").createFile()

        fixture.prepare()

        expect:
        succeeds("verify")
    }

    def "resolve partially missing artifacts"() {
        file("repo/some-artifact-1.0.jar").createFile()
        file("repo/some-artifact-1.0-sources.jar").createFile()

        fixture.expectSourceArtifact("sources")
                .prepare()

        expect:
        succeeds("verify")
    }

    def "can only resolve component if main artifact exists"() {
        file("repo/some-artifact-1.0-sources.jar").createFile()
        file("repo/some-artifact-1.0-javadoc.jar").createFile()
        def location1 = file("repo/some-artifact-1.0.jar").toURL().toString()
        def location2 = file("repo/some-artifact.jar").toURL().toString()

        fixture.expectComponentNotFound().prepare()

        expect:
        fails("verify")
        failure.assertHasCause("""Could not find some.group:some-artifact:1.0.
Searched in the following locations:
  - ${location1}
  - ${location2}""")
    }

    private publishModule() {
        file("repo/some-artifact-1.0.jar").createFile()
        file("repo/some-artifact-1.0-sources.jar").createFile()
        file("repo/some-artifact-1.0-javadoc.jar").createFile()
    }

    private publishChanged() {
        file("repo/some-artifact-1.0.jar") << "more"
        file("repo/some-artifact-1.0-sources.jar") << "more"
        file("repo/some-artifact-1.0-javadoc.jar") << "more"
    }
}
