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
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.repositories.resolver.MavenUniqueSnapshotComponentIdentifier
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.test.fixtures.file.TestFile

/**
 * A test fixture that injects a task into a build that uses the Artifact Query API to download some artifacts, validating the results.
 */
class JvmLibraryArtifactResolveTestFixture {
    private final TestFile buildFile
    private final String config
    private ModuleComponentIdentifier id = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("some.group", "some-artifact"), "1.0")
    private artifactTypes = []
    private expectedSources = []
    private expectedJavadoc = []
    private boolean unresolvedComponentFailure

    JvmLibraryArtifactResolveTestFixture(TestFile buildFile, String config = "compile") {
        this.buildFile = buildFile
        this.config = config
    }

    JvmLibraryArtifactResolveTestFixture withComponentVersion(String group, String module, String version) {
        this.id = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId(group, module), version)
        this
    }

    JvmLibraryArtifactResolveTestFixture withSnapshotTimestamp(String timestamp) {
        this.id = new MavenUniqueSnapshotComponentIdentifier(DefaultModuleIdentifier.newId(id.group, id.module), id.version, timestamp)
        this
    }

    JvmLibraryArtifactResolveTestFixture requestingSource() {
        this.artifactTypes << "SourcesArtifact"
        this
    }

    JvmLibraryArtifactResolveTestFixture requestingJavadoc() {
        this.artifactTypes << "JavadocArtifact"
        this
    }


    JvmLibraryArtifactResolveTestFixture clearExpectations() {
        unresolvedComponentFailure = false
        this.expectedSources = []
        this.expectedJavadoc = []
        this
    }

    JvmLibraryArtifactResolveTestFixture expectComponentNotFound() {
        unresolvedComponentFailure = true
        this
    }

    JvmLibraryArtifactResolveTestFixture expectComponentResolutionFailure() {
        unresolvedComponentFailure = true
        this
    }

    JvmLibraryArtifactResolveTestFixture expectSourceArtifact(String classifier) {
        expectedSources << "${id.module}-${id.version}-${classifier}.jar"
        this
    }

    JvmLibraryArtifactResolveTestFixture expectSourceArtifactNotFound(String artifactClassifier) {
        this
    }

    JvmLibraryArtifactResolveTestFixture expectSourceArtifactFailure() {
        this
    }

    JvmLibraryArtifactResolveTestFixture expectJavadocArtifact(String classifier) {
        expectedJavadoc << "${id.module}-${id.version}-${classifier}.jar"
        this
    }

    JvmLibraryArtifactResolveTestFixture expectJavadocArtifactNotFound(String artifactClassifier) {
        this
    }

    JvmLibraryArtifactResolveTestFixture expectJavadocArtifactFailure() {
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

@org.gradle.internal.exceptions.Contextual
class VerificationException extends org.gradle.internal.exceptions.DefaultMultiCauseException {
    public VerificationException(String message, Iterable<? extends Throwable> causes) {
        super(message, causes)
    }
}
"""
        if (unresolvedComponentFailure) {
            prepareComponentNotFound()
        } else {
            createVerifyTask("verify")
        }
    }

    void createVerifyTask(String taskName) {
        def resolveAndCheck = """
        assert deps.size() == 1
        def componentId = deps[0].selected.id

        def result = dependencies.createArtifactResolutionQuery()
            .forComponents(componentId)
            .withArtifacts(JvmLibrary, $artifactTypesString)
            .execute()

        assert result.components.size() == 1

        // Check generic component result
        def componentResult = result.components.iterator().next()
        assert componentResult.id.displayName == "${id.displayName}"
        assert componentResult.id.group == "${id.group}"
        assert componentResult.id.module == "${id.module}"
        assert componentResult.id.version == "${id.version}"
        assert componentResult instanceof ComponentArtifactsResult

        def failures = []

        ${checkComponentResultArtifacts("componentResult", "sources", expectedSources)}
        ${checkComponentResultArtifacts("componentResult", "javadoc", expectedJavadoc)}
"""
        if (GradleContextualExecuter.configCache) {
            buildFile << """
task $taskName {
    def root = configurations.${config}.incoming.resolutionResult.rootComponent
    def result = root.map {
        def deps = it.dependencies
        $resolveAndCheck
        failures
    }
    doLast {
        def failures = result.get()
        if (!failures.empty) {
            throw new VerificationException("Artifact resolution failed", failures)
        }
    }
}
"""
        } else {
            buildFile << """
task $taskName {
    doLast {
        def deps = configurations.${config}.incoming.resolutionResult.allDependencies as List
        $resolveAndCheck
        if (!failures.empty) {
            throw new VerificationException("Artifact resolution failed", failures)
        }
    }
}
"""
        }
    }

    void prepareComponentNotFound() {
        def resolveAndCheck = """
        def mid = org.gradle.api.internal.artifacts.DefaultModuleIdentifier.newId("${id.group}", "${id.module}")
        def componentId = new org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier(mid, "${id.version}")

        def result = dependencies.createArtifactResolutionQuery()
            .forComponents(componentId)
            .withArtifacts(JvmLibrary, $artifactTypesString)
            .execute()

        assert result.components.size() == 1

        // Check generic component result
        def componentResult = result.components.iterator().next()
        assert componentResult.id.displayName == "${id.displayName}"
        assert componentResult.id.group == "${id.group}"
        assert componentResult.id.module == "${id.module}"
        assert componentResult.id.version == "${id.version}"
        assert componentResult instanceof UnresolvedComponentResult
        def failure = componentResult.failure
"""
        if (GradleContextualExecuter.configCache) {
            buildFile << """
task verify {
    def result = provider {
        $resolveAndCheck
        failure
    }
    doLast {
        throw result.get()
    }
}
"""
        } else {
            buildFile << """
task verify {
    doLast {
        $resolveAndCheck
        throw failure
    }
}
"""
        }
    }

    private String checkComponentResultArtifacts(String componentResult, String type, def expectedFiles) {
        """
    def ${type}ArtifactResultFiles = []
    ${componentResult}.getArtifacts(${type.capitalize()}Artifact).each { artifactResult ->
        assert artifactResult.id.componentIdentifier.displayName == "${id.displayName}"
        assert artifactResult.id.componentIdentifier.group == "${id.group}"
        assert artifactResult.id.componentIdentifier.module == "${id.module}"
        assert artifactResult.id.componentIdentifier.version == "${id.version}"
        if (artifactResult instanceof ResolvedArtifactResult) {
            copy {
                from artifactResult.file
                into "${type}"
            }
            ${type}ArtifactResultFiles << artifactResult.file.name
        } else {
            failures << artifactResult.failure
        }
    }
    assert ${type}ArtifactResultFiles as Set == ${toQuotedList(expectedFiles)} as Set
"""
    }

    private static String toQuotedList(def values) {
        return values.collect({"\"$it\""}).toListString()
    }

    private String getArtifactTypesString() {
        if (artifactTypes.empty) {
            return "SourcesArtifact,JavadocArtifact"
        }
        return artifactTypes.join(',')
    }
}

