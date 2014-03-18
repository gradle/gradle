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
package org.gradle.integtests.resolve.ivy

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest

class IvyJvmLibraryArtifactResolutionIntegrationTest extends AbstractDependencyResolutionTest {
    def repo = ivyHttpRepo
    def module = repo.module("some.group", "some-artifact", "1.0")

    def setup() {
        server.start()
    }

    def "resolve sources artifacts"() {
        publishModule()
        module.ivy.expectGet()
        module.getArtifact(classifier: "sources").expectGet()


        buildFile <<
"""
import org.gradle.api.internal.artifacts.component.*

repositories {
    ivy { url "$repo.uri" }
}

task verify << {
    def result = dependencies.createArtifactResolutionQuery()
        .forComponents([new DefaultModuleComponentIdentifier("some.group", "some-artifact", "1.0")] as Set)
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

    assert result.unresolvedComponents.empty
}
"""

        expect:
        succeeds("verify")
    }

    def "resolve javadoc artifacts"() {
        publishModule()
        module.ivy.expectGet()
        module.getArtifact(classifier: "javadoc").expectGet()

        buildFile <<
"""
import org.gradle.api.internal.artifacts.component.*

repositories {
    ivy { url "$repo.uri" }
}

task verify << {
    def result = dependencies.createArtifactResolutionQuery()
        .forComponents([new DefaultModuleComponentIdentifier("some.group", "some-artifact", "1.0")] as Set)
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

    assert result.unresolvedComponents.empty
}
"""

        expect:
        succeeds("verify")
    }

    def "resolve all artifacts"() {
        publishModule()
        module.ivy.expectGet()
        module.getArtifact(classifier: "sources").expectGet()
        module.getArtifact(classifier: "javadoc").expectGet()

        buildFile <<
"""
import org.gradle.api.internal.artifacts.component.*

repositories {
    ivy { url "$repo.uri" }
}

task verify << {
    def result = dependencies.createArtifactResolutionQuery()
        .forComponents([new DefaultModuleComponentIdentifier("some.group", "some-artifact", "1.0")] as Set)
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

    assert result.unresolvedComponents.empty
}
"""

        expect:
        succeeds("verify")
    }

    def "resolve artifacts of non-existing component"() {
        module.ivy.expectGetMissing()
        module.jar.expectHeadMissing()

        buildFile <<
"""
import org.gradle.api.internal.artifacts.component.*

repositories {
    ivy { url "$repo.uri" }
}

task verify << {
    def result = dependencies.createArtifactResolutionQuery()
        .forComponents([new DefaultModuleComponentIdentifier("some.group", "some-artifact", "1.0")] as Set)
        .withArtifacts(JvmLibrary)
        .execute()

    assert result.components.empty
    assert result.unresolvedComponents.size() == 1
    for (component in result.unresolvedComponents) {
        assert component.id.group == "some.group"
        assert component.id.module == "some-artifact"
        assert component.id.version == "1.0"
        assert component.failure instanceof org.gradle.api.internal.artifacts.ivyservice.ModuleVersionNotFoundException
    }
}
"""

        expect:
        succeeds("verify")
    }

    def "resolve non-existing artifacts of existing component"() {
        publishModule()
        module.ivy.expectGet()
        module.getArtifact(classifier: "sources").expectGetMissing()
        module.getArtifact(classifier: "javadoc").expectGetMissing()

        buildFile <<
"""
import org.gradle.api.internal.artifacts.component.*

repositories {
    ivy { url "$repo.uri" }
}

task verify << {
    def result = dependencies.createArtifactResolutionQuery()
        .forComponents([new DefaultModuleComponentIdentifier("some.group", "some-artifact", "1.0")] as Set)
        .withArtifacts(JvmLibrary)
        .execute()

    assert result.components.size() == 1
    for (component in components) {
        assert component.id.group == "some.group"
        assert component.id.module == "some-artifact"
        assert component.id.version == "1.0"
        assert component instanceof JvmLibrary

        assert component.allArtifacts.empty
        assert component.sourceArtifacts.empty
        assert component.javadocArtifacts.empty
    }

    assert result.unresolvedComponents.empty
}
"""

        expect:
        succeeds("verify")
    }

    def "resolve partially missing artifacts"() {
        publishModule()
        module.ivy.expectGet()
        module.getArtifact(classifier: "sources").expectGet()
        module.getArtifact(classifier: "javadoc").expectGetMissing()

        buildFile <<
"""
import org.gradle.api.internal.artifacts.component.*

repositories {
    ivy { url "$repo.uri" }
}

task verify << {
    def result = dependencies.createArtifactResolutionQuery()
        .forComponents([new DefaultModuleComponentIdentifier("some.group", "some-artifact", "1.0")] as Set)
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
        def sourceArtifact = component.sourcesArtifacts.iterator().next()
        assert sourceArtifact instanceof JvmLibrarySourcesArtifact
        assert sourceArtifact.file.name == "some-artifact-1.0-sources.jar"

        assert component.javadocArtifacts.size() == 1
        def javadocArtifact = component.javadocArtifacts.iterator().next()
        assert javadocArtifact instanceof JvmLibraryJavadocArtifact
        assert javadocArtifact.failure instanceof org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ArtifactResolveException
    }

    assert result.unresolvedComponents.empty
}
"""

        expect:
        succeeds("verify")
    }

    def "resolve partially broken artifacts"() {
        publishModule()
        module.ivy.expectGet()
        module.getArtifact(classifier: "sources").expectGet()
        module.getArtifact(classifier: "javadoc").expectGetBroken()

        buildFile <<
"""
import org.gradle.api.internal.artifacts.component.*

repositories {
    ivy { url "$repo.uri" }
}

task verify << {
    def result = dependencies.createArtifactResolutionQuery()
        .forComponents([new DefaultModuleComponentIdentifier("some.group", "some-artifact", "1.0")] as Set)
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
        def sourceArtifact = component.sourcesArtifacts.iterator().next()
        assert sourceArtifact instanceof JvmLibrarySourcesArtifact
        assert sourceArtifact.file.name == "some-artifact-1.0-sources.jar"

        assert component.javadocArtifacts.size() == 1
        def javadocArtifact = component.javadocArtifacts.iterator().next()
        assert javadocArtifact instanceof JvmLibraryJavadocArtifact
        assert javadocArtifact.failure instanceof org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ArtifactResolveException
    }

    assert result.unresolvedComponents.empty
}
"""

        expect:
        succeeds("verify")
    }

    private publishModule() {
        module.configuration("sources")
        module.configuration("javadoc")
        module.artifact(type: "source", classifier: "sources", ext: "jar", conf: "sources")
        module.artifact(type: "javadoc", classifier: "javadoc", ext: "jar", conf: "javadoc")
        module.publish()
    }
}