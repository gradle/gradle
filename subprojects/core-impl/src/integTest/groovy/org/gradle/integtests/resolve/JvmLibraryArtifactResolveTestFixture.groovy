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
import org.gradle.api.artifacts.resolution.JvmLibraryArtifact
import org.gradle.api.internal.artifacts.component.DefaultModuleComponentIdentifier
import org.gradle.test.fixtures.file.TestFile
/**
 * A test fixture that injects a task into a build that uses the Artifact Query API to download some artifacts, validating the results.
 */
class JvmLibraryArtifactResolveTestFixture {
    private final TestFile buildFile
    private ModuleComponentIdentifier id = DefaultModuleComponentIdentifier.newId("some.group", "some-artifact", "1.0")
    private artifactTypes = []
    private expectedSourcesListFailure
    private expectedSources = []
    private expectedSourceFailures = []
    private expectedJavadocListFailure
    private expectedJavadoc = []
    private expectedJavadocFailures = []
    private boolean missingComponent

    JvmLibraryArtifactResolveTestFixture(TestFile buildFile) {
        this.buildFile = buildFile
    }

    JvmLibraryArtifactResolveTestFixture withComponentVersion(String group, String module, String version) {
        this.id = DefaultModuleComponentIdentifier.newId(group, module, version)
        this
    }

    JvmLibraryArtifactResolveTestFixture requestingTypes(Class<? extends JvmLibraryArtifact>... artifactTypes) {
        this.artifactTypes = artifactTypes as List
        this
    }

    JvmLibraryArtifactResolveTestFixture clearExpectations() {
        this.missingComponent = false
        this.expectedSources = []
        this.expectedJavadoc = []
        this.expectedSourceFailures = []
        this.expectedJavadocFailures = []
        this.expectedSourcesListFailure = null
        this.expectedJavadocListFailure = null
        this
    }

    JvmLibraryArtifactResolveTestFixture expectComponentNotFound() {
        this.missingComponent = true
        this
    }

    JvmLibraryArtifactResolveTestFixture expectSourceArtifact(String classifier) {
        expectedSources << "${id.module}-${id.version}-${classifier}.jar"
        this
    }

    JvmLibraryArtifactResolveTestFixture expectSourceArtifactNotFound(String classifier) {
        expectedSourceFailures << "Artifact '${id.group}:${id.module}:${id.version}:${id.module}-${classifier}.jar' not found."
        this
    }

    JvmLibraryArtifactResolveTestFixture expectSourceArtifactListFailure(def failure) {
        expectedSourcesListFailure = failure
        this
    }

    JvmLibraryArtifactResolveTestFixture expectSourceArtifactFailure(def failure) {
        // TODO:DAZ Validate more than just the failure message
        expectedSourceFailures << failure
        this
    }

    JvmLibraryArtifactResolveTestFixture expectJavadocArtifact(def classifier) {
        expectedJavadoc << "${id.module}-${id.version}-${classifier}.jar"
        this
    }

    JvmLibraryArtifactResolveTestFixture expectJavadocArtifactNotFound(def classifier) {
        expectedJavadocFailures << "Artifact '${id.group}:${id.module}:${id.version}:${id.module}-${classifier}.jar' not found."
        this
    }

    JvmLibraryArtifactResolveTestFixture expectJavadocArtifactListFailure(def failure) {
        expectedJavadocListFailure = failure
        this
    }

    JvmLibraryArtifactResolveTestFixture expectJavadocArtifactFailure(def failure) {
        expectedJavadocFailures << failure
        this
    }

    /**
     * Injects the appropriate stuff into the build script.
     */
    void prepare() {
        buildFile << """
import org.gradle.api.internal.artifacts.component.*
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
        .forComponents([new DefaultModuleComponentIdentifier("${id.group}", "${id.module}", "${id.version}")] as Set)
        .withArtifacts(JvmLibrary$artifactTypesString)
        .execute()

    assert result.components.size() == 1
    def jvmLibrary = result.components.iterator().next()
    assert jvmLibrary.id.group == "${id.group}"
    assert jvmLibrary.id.module == "${id.module}"
    assert jvmLibrary.id.version == "${id.version}"
    assert jvmLibrary instanceof JvmLibrary
"""
        if (expectedSourcesListFailure != null) {
            buildFile << """
    assert jvmLibrary.sourcesArtifacts.failure.message == "${expectedSourcesListFailure}"
"""
        } else {
            buildFile << """
    def sourceArtifactFiles = []
    def sourceArtifactFailures = []
    jvmLibrary.sourcesArtifacts.artifacts.each { artifact ->
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
"""
        }

        if (expectedJavadocListFailure != null) {
            buildFile << """
    assert jvmLibrary.javadocArtifacts.failure.message == "${expectedJavadocListFailure}"
"""
        } else {
            buildFile << """
    def javadocArtifactFiles = []
    def javadocArtifactFailures = []
    jvmLibrary.javadocArtifacts.artifacts.each { artifact ->
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
"""
        }

        buildFile << """
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
        .forComponents([new DefaultModuleComponentIdentifier("${id.group}", "${id.module}", "${id.version}")] as Set)
        .withArtifacts(JvmLibrary)
        .execute()

    assert result.components.empty
    assert result.unresolvedComponents.size() == 1
    for (component in result.unresolvedComponents) {
        assert component.id.group == "${id.group}"
        assert component.id.module == "${id.module}"
        assert component.id.version == "${id.version}"
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

