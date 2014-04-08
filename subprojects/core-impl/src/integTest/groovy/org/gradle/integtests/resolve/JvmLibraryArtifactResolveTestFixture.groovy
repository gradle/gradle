/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.Artifact
import org.gradle.api.artifacts.result.jvm.JvmLibraryJavadocArtifact
import org.gradle.api.artifacts.result.jvm.JvmLibrarySourcesArtifact
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import org.gradle.api.internal.artifacts.component.DefaultModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionNotFoundException
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ArtifactNotFoundException
import org.gradle.api.internal.artifacts.metadata.DefaultModuleVersionArtifactIdentifier
import org.gradle.test.fixtures.file.TestFile

/**
 * A test fixture that injects a task into a build that uses the Artifact Query API to download some artifacts, validating the results.
 */
class JvmLibraryArtifactResolveTestFixture {
    private final TestFile buildFile
    private final String config
    private ModuleComponentIdentifier id = DefaultModuleComponentIdentifier.newId("some.group", "some-artifact", "1.0")
    private boolean noCache
    private artifactTypes = []
    private expectedSources = []
    Throwable expectedSourceFailure
    private expectedJavadoc = []
    Throwable expectedJavadocFailure
    private Throwable unresolvedComponentFailure

    JvmLibraryArtifactResolveTestFixture(TestFile buildFile, String config = "compile") {
        this.buildFile = buildFile
        this.config = config
    }

    JvmLibraryArtifactResolveTestFixture withComponentVersion(String group, String module, String version) {
        this.id = DefaultModuleComponentIdentifier.newId(group, module, version)
        this
    }

    JvmLibraryArtifactResolveTestFixture withNoCache(boolean noCache = true) {
        this.noCache = noCache
    }

    JvmLibraryArtifactResolveTestFixture requestingTypes(Class<? extends Artifact>... artifactTypes) {
        this.artifactTypes = artifactTypes as List
        this
    }

    JvmLibraryArtifactResolveTestFixture clearExpectations() {
        this.unresolvedComponentFailure = null
        this.expectedSources = []
        this.expectedJavadoc = []
        this.expectedSourceFailure = null
        this.expectedJavadocFailure = null
        this
    }

    JvmLibraryArtifactResolveTestFixture expectComponentNotFound() {
        this.unresolvedComponentFailure = new ModuleVersionNotFoundException(new DefaultModuleVersionSelector(id.group, id.module, id.version))
        this
    }

    JvmLibraryArtifactResolveTestFixture expectComponentResolutionFailure(Throwable failure) {
        unresolvedComponentFailure = failure
        this
    }

    JvmLibraryArtifactResolveTestFixture expectSourceArtifact(String classifier) {
        expectedSources << "${id.module}-${id.version}-${classifier}.jar"
        this
    }

    JvmLibraryArtifactResolveTestFixture expectSourceArtifactNotFound(String artifactClassifier) {
        expectedSourceFailure = new ArtifactNotFoundException(new DefaultModuleVersionArtifactIdentifier(id, id.module, "jar", "jar", [classifier: artifactClassifier]))
        this
    }

    JvmLibraryArtifactResolveTestFixture expectSourceArtifactFailure(Throwable failure) {
        expectedSourceFailure = failure
        this
    }

    JvmLibraryArtifactResolveTestFixture expectJavadocArtifact(String classifier) {
        expectedJavadoc << "${id.module}-${id.version}-${classifier}.jar"
        this
    }

    JvmLibraryArtifactResolveTestFixture expectJavadocArtifactNotFound(String artifactClassifier) {
        expectedJavadocFailure = new ArtifactNotFoundException(new DefaultModuleVersionArtifactIdentifier(id, id.module, "jar", "jar", [classifier: artifactClassifier]))
        this
    }

    JvmLibraryArtifactResolveTestFixture expectJavadocArtifactFailure(Throwable failure) {
        expectedJavadocFailure = failure
        this
    }

    /**
     * Injects the appropriate stuff into the build script.
     */
    void prepare() {
        buildFile << """
configurations {
    ${config}
}
dependencies {
    ${config} "${id.group}:${id.module}:${id.version}"
}
"""
        if (unresolvedComponentFailure != null) {
            prepareComponentNotFound()
        } else {
            createVerifyTask("verify")
        }
    }

    void createVerifyTask(String taskName) {
        buildFile << """
task $taskName << {
    def deps = configurations.${config}.incoming.resolutionResult.allDependencies as List
    assert deps.size() == 1
    def componentId = deps[0].selected.id

    def result = dependencies.createArtifactResolutionQuery()
        .forComponents(deps[0].selected.id)
        .withArtifacts(JvmLibrary, $artifactTypesString)
        .execute()

    assert result.components.size() == 1

    def jvmLibrary = result.getResolvedComponents(JvmLibrary).iterator().next()

    def sourceArtifactFiles = []
    jvmLibrary.sourcesArtifacts.each { artifact ->
        assert artifact instanceof JvmLibrarySourcesArtifact
        if (artifact.failure != null) {
            ${checkException("artifact.failure", expectedSourceFailure)}
        } else {
            copy {
                from artifact.file
                into "sources"
            }
            sourceArtifactFiles << artifact.file.name
        }
    }
    assert sourceArtifactFiles as Set == ${toQuotedList(expectedSources)} as Set

    def javadocArtifactFiles = []
    jvmLibrary.javadocArtifacts.each { artifact ->
        assert artifact instanceof JvmLibraryJavadocArtifact
        if (artifact.failure != null) {
            ${checkException("artifact.failure", expectedJavadocFailure)}
        } else {
            copy {
                from artifact.file
                into "javadoc"
            }
            javadocArtifactFiles << artifact.file.name
        }
    }
    assert javadocArtifactFiles as Set == ${toQuotedList(expectedJavadoc)} as Set

    def componentResult = result.components.iterator().next()
    assert componentResult.id == jvmLibrary.id
    assert componentResult instanceof ResolvedComponentArtifactsResult
    assert componentResult.id.displayName == "${id.displayName}"
    assert componentResult.getArtifacts(JvmLibrarySourcesArtifact).size() == jvmLibrary.sourcesArtifacts.size()
    assert componentResult.getArtifacts(JvmLibraryJavadocArtifact).size() == jvmLibrary.javadocArtifacts.size()
    assert componentResult.getArtifacts(Artifact).size() == jvmLibrary.sourcesArtifacts.size() + jvmLibrary.javadocArtifacts.size()
}
"""
    }

    private static String checkException(String reference, Throwable expected) {
        if (expected == null) {
            return "throw $reference"
        }
        return """
    assert ${reference} instanceof ${expected.class.name}
    assert ${reference}.message == "${expected.message}"
"""
    }

    private static String toQuotedList(def values) {
        return values.collect({"\"$it\""}).toListString()
    }

    private void prepareComponentNotFound() {
        buildFile << """
task verify << {
    def unknownComponentId = [getGroup: {'${id.group}'}, getModule: {'${id.module}'}, getVersion: {'${id.version}'}, getDisplayName: {'unknown'}] as ModuleComponentIdentifier
    def result = dependencies.createArtifactResolutionQuery()
        .forComponents(unknownComponentId)
        .withArtifacts(JvmLibrary, $artifactTypesString)
        .execute()

    assert result.components.size() == 1
    def component = result.components.iterator().next()
    assert component instanceof UnresolvedComponentResult
    assert component.id.group == "${id.group}"
    assert component.id.module == "${id.module}"
    assert component.id.version == "${id.version}"
    assert component.id.displayName == 'unknown'
    ${checkException("component.failure", unresolvedComponentFailure)}
}
"""
    }

    private String getArtifactTypesString() {
        if (artifactTypes.empty) {
            return [JvmLibrarySourcesArtifact, JvmLibraryJavadocArtifact].collect({it.simpleName}).join(',')
        }
        return artifactTypes.collect({ it.simpleName }).join(',')
    }
}

