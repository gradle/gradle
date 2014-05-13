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
import org.gradle.api.component.Artifact
import org.gradle.api.artifacts.result.jvm.JavadocArtifact
import org.gradle.api.artifacts.result.jvm.SourcesArtifact
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

    // Check generic component result
    def componentResult = result.components.iterator().next()
    assert componentResult.id.displayName == "${id.displayName}"
    assert componentResult instanceof ResolvedComponentArtifactsResult

    ${checkComponentResultArtifacts("componentResult", "sources", expectedSources, expectedSourceFailure)}
    ${checkComponentResultArtifacts("componentResult", "javadoc", expectedJavadoc, expectedJavadocFailure)}
}
"""
    }

    private static String checkComponentResultArtifacts(String componentResult, String type, def expectedFiles, def expectedFailure) {
        """
    def ${type}ArtifactResultFiles = []
    ${componentResult}.getArtifacts(${type.capitalize()}Artifact).each { artifactResult ->
        if (artifactResult instanceof ResolvedArtifactResult) {
            copy {
                from artifactResult.file
                into "${type}"
            }
            ${type}ArtifactResultFiles << artifactResult.file.name
        } else {
            ${checkException("artifactResult.failure", expectedFailure)}
        }
    }
    assert ${type}ArtifactResultFiles as Set == ${toQuotedList(expectedFiles)} as Set
"""
    }


    private static String checkException(String reference, Throwable expected) {
        if (expected == null) {
            return "throw $reference"
        }
        String check = """
    assert ${reference} instanceof ${expected.class.name}
    assert ${reference}.message == "${expected.message}"
"""
        if (expected.cause != null) {
            check += """
    assert ${reference}.cause.message =~ "${expected.cause.message}"
"""
        }
        return check
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
            return [SourcesArtifact, JavadocArtifact].collect({it.simpleName}).join(',')
        }
        return artifactTypes.collect({ it.simpleName }).join(',')
    }
}

