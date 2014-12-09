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

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.test.fixtures.file.TestFile

class MetadataArtifactResolveTestFixture {
    private final TestFile buildFile
    final String config
    final ModuleComponentIdentifier id = DefaultModuleComponentIdentifier.newId('some.group', 'some-artifact', '1.0')
    private String requestedComponent
    private String requestedArtifact
    private String expectedComponentResult
    private Set<File> expectedMetadataFiles

    MetadataArtifactResolveTestFixture(TestFile buildFile, String config = "compile") {
        this.buildFile = buildFile
        this.config = config
    }

    void basicSetup() {
        buildFile << """
import org.gradle.ivy.IvyModule
import org.gradle.ivy.IvyDescriptorArtifact
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact

configurations {
    $config
}

dependencies {
    $config '$id.displayName'
}
"""
    }

    void configureChangingModule() {
        buildFile << """
dependencies {
    components {
        all { ComponentMetadataDetails details ->
            details.changing = true
        }
    }
}

if (project.hasProperty('nocache')) {
    configurations.all {
        resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
    }
}
"""
    }

    MetadataArtifactResolveTestFixture requestComponent(String component) {
        this.requestedComponent = component
        this
    }

    MetadataArtifactResolveTestFixture requestArtifact(String artifact) {
        this.requestedArtifact = artifact
        this
    }

    MetadataArtifactResolveTestFixture expectComponentResult(String componentResult) {
        this.expectedComponentResult = componentResult
        this
    }

    MetadataArtifactResolveTestFixture expectMetadataFiles(Set<File> metadataFiles) {
        this.expectedMetadataFiles = metadataFiles
        this
    }

    void createVerifyTaskModuleComponentIdentifier() {
        buildFile << """
task verify {
    doLast {
        def deps = configurations.${config}.incoming.resolutionResult.allDependencies as List
        assert deps.size() == 1
        def componentId = deps[0].selected.id

        def result = dependencies.createArtifactResolutionQuery()
            .forComponents(deps[0].selected.id)
            .withArtifacts($requestedComponent, $requestedArtifact)
            .execute()

        assert result.components.size() == 1

        // Check generic component result
        def componentResult = result.components.iterator().next()
        assert componentResult.id.displayName == '$id.displayName'
        assert componentResult instanceof $expectedComponentResult

        Set<File> resultArtifactFiles = result.artifactFiles
        assert resultArtifactFiles.size() == ${expectedMetadataFiles.size()}

        def resolvedArtifactFileNames = resultArtifactFiles.collect { it.name } as Set
        def expectedMetadataFileNames = ${expectedMetadataFiles.collect { "'" + it.name + "'" }} as Set
        assert resolvedArtifactFileNames == expectedMetadataFileNames
    }
}
"""
    }

    void createVerifyTaskForProjectComponentIdentifier() {
        buildFile << """
task verify {
    doLast {
        def rootId = configurations.${config}.incoming.resolutionResult.root.id
        assert rootId instanceof ProjectComponentIdentifier

        dependencies.createArtifactResolutionQuery()
            .forComponents(rootId)
            .withArtifacts($requestedComponent, $requestedArtifact)
            .execute()
    }
}
"""
    }

    void createVerifyTaskForDuplicateCallToWithArtifacts() {
        buildFile << """
task verify {
    doLast {
        def deps = configurations.${config}.incoming.resolutionResult.allDependencies as List
        assert deps.size() == 1
        def componentId = deps[0].selected.id

        dependencies.createArtifactResolutionQuery()
            .forComponents(deps[0].selected.id)
            .withArtifacts($requestedComponent, $requestedArtifact)
            .withArtifacts($requestedComponent, $requestedArtifact)
    }
}
"""
    }
}
