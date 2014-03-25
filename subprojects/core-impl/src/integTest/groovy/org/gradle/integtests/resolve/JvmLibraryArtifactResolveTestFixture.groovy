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
import org.gradle.api.artifacts.resolution.JvmLibraryArtifact
import org.gradle.test.fixtures.file.TestFile
/**
 * A test fixture that injects a task into a build that uses the Artifact Query API to download some artifacts, validating the results.
 */
class JvmLibraryArtifactResolveTestFixture {
    private final TestFile buildFile
    private repository
    private componentVersion = "1.0"
    private artifactTypes = []
    private expectedSources = []
    private expectedSourceFailures = []
    private expectedJavadoc = []
    private expectedJavadocFailures = []
    private boolean missingComponent

    JvmLibraryArtifactResolveTestFixture(TestFile buildFile) {
        this.buildFile = buildFile
    }

    def withRepository(def repository) {
        this.repository = repository
    }

    def withComponentVersion(def componentVersion) {
        this.componentVersion = componentVersion
        this
    }

    def requestingTypes(Class<? extends JvmLibraryArtifact>... artifactTypes) {
        this.artifactTypes = artifactTypes as List
        this
    }

    def clearExpectations() {
        this.missingComponent = false
        this.expectedSources = []
        this.expectedJavadoc = []
        this.expectedSourceFailures = []
        this.expectedJavadocFailures = []
        this
    }

    def expectComponentNotFound() {
        this.missingComponent = true
        this
    }

    def expectSourceArtifact(def sourceArtifact) {
        expectedSources << sourceArtifact
        this
    }

    def expectSourceArtifactNotFound(def sourceArtifact) {
        expectedSourceFailures << "Artifact 'some.group:some-artifact:$componentVersion:$sourceArtifact' not found."
        this
    }

    def expectSourceArtifactFailure(def failure) {
        // TODO:DAZ Validate more than just the failure message
        expectedSourceFailures << failure
        this
    }

    def expectJavadocArtifact(def javadocArtifact) {
        expectedJavadoc << javadocArtifact
        this
    }

    def expectJavadocArtifactNotFound(def sourceArtifact) {
        expectedJavadocFailures << "Artifact 'some.group:some-artifact:$componentVersion:$sourceArtifact' not found."
        this
    }

    def expectJavadocArtifactFailure(def failure) {
        expectedJavadocFailures << failure
        this
    }

    /**
     * Injects the appropriate stuff into the build script.
     */
    void prepare() {
        buildFile << """
import org.gradle.api.internal.artifacts.component.*

repositories {
    $repository
}
"""
        if (missingComponent) {
            prepareComponentNotFound()
        } else {
            createVerifyTask("verify")
        }
    }

    void createVerifyTask(def taskName) {
        buildFile << """
task $taskName << {
    def result = dependencies.createArtifactResolutionQuery()
        .forComponents([new DefaultModuleComponentIdentifier("some.group", "some-artifact", "$componentVersion")] as Set)
        .withArtifacts(JvmLibrary$artifactTypesString)
        .execute()

    assert result.components.size() == 1
    def jvmLibrary = result.components.iterator().next()
    assert jvmLibrary.id.group == "some.group"
    assert jvmLibrary.id.module == "some-artifact"
    assert jvmLibrary.id.version == "$componentVersion"
    assert jvmLibrary instanceof JvmLibrary

    def sourceArtifactFiles = []
    def sourceArtifactFailures = []
    jvmLibrary.sourcesArtifacts.each { artifact ->
        assert artifact instanceof JvmLibrarySourcesArtifact
        if (artifact.failure != null) {
            sourceArtifactFailures << artifact.failure.message
        } else {
            copy {
                from artifact.file
                into "sources"
            }
            sourceArtifactFiles << artifact.file.name
        }
    }
    assert sourceArtifactFiles as Set == ${toQuotedList(expectedSources)} as Set
    assert sourceArtifactFailures as Set == ${toQuotedList(expectedSourceFailures)} as Set

    def javadocArtifactFiles = []
    def javadocArtifactFailures = []
    jvmLibrary.javadocArtifacts.each { artifact ->
        assert artifact instanceof JvmLibraryJavadocArtifact
        if (artifact.failure != null) {
            javadocArtifactFailures << artifact.failure.message
        } else {
            copy {
                from artifact.file
                into "javadoc"
            }
            javadocArtifactFiles << artifact.file.name
        }
    }
    assert javadocArtifactFiles as Set == ${toQuotedList(expectedJavadoc)} as Set
    assert javadocArtifactFailures as Set == ${toQuotedList(expectedJavadocFailures)} as Set

    assert jvmLibrary.allArtifacts as Set == (jvmLibrary.sourcesArtifacts as Set) + (jvmLibrary.javadocArtifacts as Set)

    assert result.unresolvedComponents.empty
}
"""
    }

    private static String toQuotedList(def values) {
        return values.collect({"\"$it\""}).toListString()
    }

    private void prepareComponentNotFound() {
        buildFile << """
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
    }

    private String getArtifactTypesString() {
        def artifactTypesString = ""
        for (Class<? extends JvmLibraryArtifact> type : artifactTypes) {
            artifactTypesString += ", ${type.simpleName}"
        }
        return artifactTypesString
    }
}

