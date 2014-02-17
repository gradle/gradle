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

class JvmLibraryArtifactResolutionIntegrationTest extends AbstractDependencyResolutionTest {
    def repo = mavenHttpRepo

    def setup() {
        server.start()
        def module = repo.module("some.group", "some-artifact", "1.0")
        module.artifact(classifier: "sources")
        module.artifact(classifier: "javadoc")
        module.publish().allowAll()
    }

    def "resolve sources artifacts"() {
        buildFile <<
"""
import org.gradle.api.artifacts.resolution.*

repositories {
    maven { url "$repo.uri" }
}

task verify << {
    def result = dependencies.createArtifactResolutionQuery()
        .forComponent("some.group", "some-artifact", "1.0")
        .withArtifacts(JvmLibrary, JvmLibrarySourcesArtifact)
        .execute()

    def components = result.components
    assert components.size() == 1
    for (component in components) {
        assert component.id.group == "some.group"
        assert component.id.module == "some-artifact"
        assert component.id.version == "1.0"
        assert component.allArtifacts.size() == 1
        assert component instanceof JvmLibrary

        assert component.sourcesArtifacts.size() == 1
        def sourceArtifact = component.sourcesArtifacts.iterator().next()
        assert sourceArtifact instanceof JvmLibrarySourcesArtifact
        assert sourceArtifact.file.name == "some-artifact-1.0-sources.jar"

        assert component.javadocArtifacts.empty
    }
}
"""

        expect:
        succeeds("verify")
    }

    def "resolve javadoc artifacts"() {
        buildFile <<
"""
import org.gradle.api.artifacts.resolution.*

repositories {
    maven { url "$repo.uri" }
}

task verify << {
    def result = dependencies.createArtifactResolutionQuery()
        .forComponent("some.group", "some-artifact", "1.0")
        .withArtifacts(JvmLibrary, JvmLibraryJavadocArtifact)
        .execute()

    def components = result.components
    assert components.size() == 1
    for (component in components) {
        assert component.id.group == "some.group"
        assert component.id.module == "some-artifact"
        assert component.id.version == "1.0"
        assert component.allArtifacts.size() == 1
        assert component instanceof JvmLibrary

        assert component.sourcesArtifacts.empty

        assert component.javadocArtifacts.size() == 1
        def javadocArtifact = component.javadocArtifacts.iterator().next()
        assert javadocArtifact instanceof JvmLibraryJavadocArtifact
        assert javadocArtifact.file.name == "some-artifact-1.0-javadoc.jar"
    }
}
"""

        expect:
        succeeds("verify")
    }

    def "resolve all artifacts"() {
        buildFile <<
"""
import org.gradle.api.artifacts.resolution.*

repositories {
    maven { url "$repo.uri" }
}

task verify << {
    def result = dependencies.createArtifactResolutionQuery()
        .forComponent("some.group", "some-artifact", "1.0")
        .withArtifacts(JvmLibrary)
        .execute()

    def components = result.components
    assert components.size() == 1
    for (component in components) {
        assert component.id.group == "some.group"
        assert component.id.module == "some-artifact"
        assert component.id.version == "1.0"
        assert component.allArtifacts.size() == 2
        assert component instanceof JvmLibrary

        assert component.sourcesArtifacts.size() == 1
        assert component.javadocArtifacts.size() == 1
        assert component.allArtifacts.contains(component.sourcesArtifacts.iterator().next())
        assert component.allArtifacts.contains(component.javadocArtifacts.iterator().next())
    }
}
"""

        expect:
        succeeds("verify")
    }
}