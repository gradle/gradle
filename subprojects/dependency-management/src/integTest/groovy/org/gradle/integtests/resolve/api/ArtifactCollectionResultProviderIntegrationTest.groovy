/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.integtests.resolve.api

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.extensions.FluidDependenciesResolveInterceptor
import org.gradle.integtests.fixtures.extensions.FluidDependenciesResolveTest

@FluidDependenciesResolveTest
class ArtifactCollectionResultProviderIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def setup() {
        settingsFile << """
            rootProject.name = 'root'
            include 'project-lib'
        """
        mavenRepo.module("org.external", "external-lib").publish()
        file('lib/file-lib.jar') << 'content'
        buildFile << """
            project(':project-lib') {
                apply plugin: 'java'
            }
            configurations {
                compile
            }
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            dependencies {
                compile 'org.external:external-lib:1.0'
                compile project('project-lib')
                compile files('lib/file-lib.jar')
            }

            abstract class TaskWithArtifactCollectionResultProviderInput extends DefaultTask {

                @InputFiles
                abstract ConfigurableFileCollection getArtifactFiles()

                @Internal
                abstract SetProperty<ResolvedArtifactResult> getResolvedArtifacts()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void action() {
                    println(artifactFiles.files)
                    println(resolvedArtifacts.get())
                }
            }
        """
    }

    def "result provider has artifact files and metadata"() {
        given:
        buildFile << """
            tasks.register('checkArtifacts', TaskWithArtifactCollectionResultProviderInput) {
                artifactFiles.from(configurations.compile.incoming.artifacts.artifactFiles)
                resolvedArtifacts.set(configurations.compile.incoming.artifacts.resolvedArtifacts)
                outputFile.set(layout.buildDirectory.file('output.txt'))
                doLast {
                    def artifactResults = resolvedArtifacts.get()

                    assert artifactResults.size() == 3

                    // Check external artifact
                    def idx = artifactFiles.findIndexOf { it.name == 'external-lib-1.0.jar' }

                    def result = artifactResults[idx]
                    assert result.file == artifactFiles[idx]

                    assert result.id instanceof org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
                    assert result.id.componentIdentifier.group == 'org.external'
                    assert result.id.componentIdentifier.module == 'external-lib'
                    assert result.id.componentIdentifier.version == '1.0'
                    assert result.id.fileName == 'external-lib-1.0.jar'

                    assert result.variant instanceof org.gradle.api.internal.artifacts.result.DefaultResolvedVariantResult
                    assert result.variant.owner == result.id.componentIdentifier
                    assert result.variant.attributes.getAttribute(org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE) == 'jar'
                    assert result.variant.attributes.getAttribute(org.gradle.api.internal.project.ProjectInternal.STATUS_ATTRIBUTE) == 'release'
                    assert result.variant.capabilities.size() == 1
                    assert result.variant.capabilities[0].group == 'org.external'
                    assert result.variant.capabilities[0].name == 'external-lib'
                    assert result.variant.capabilities[0].version == '1.0'

                    // Check project artifact
                    idx = artifactFiles.findIndexOf { it.name == 'project-lib.jar' }

                    result = artifactResults[idx]
                    assert result.file == artifactFiles[idx]

                    assert result.id.componentIdentifier instanceof ProjectComponentIdentifier
                    assert result.id.componentIdentifier.projectPath == ':project-lib'

                    // Check file artifact
                    idx = artifactFiles.findIndexOf { it.name == 'file-lib.jar' }

                    result = artifactResults[idx]
                    assert result.file == artifactFiles[idx]

                    assert result.id instanceof org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier
                    assert result.id.componentIdentifier == result.id
                    assert result.id.displayName == 'file-lib.jar'
                }
            }
        """

        expect:
        run 'checkArtifacts'
    }

    def "failure to resolve artifact collection"() {
        given:
        buildFile << """
            dependencies {
                compile 'org:does-not-exist:1.0'
            }

            task verify(type: TaskWithArtifactCollectionResultProviderInput) {
                artifactFiles.from(configurations.compile.incoming.artifacts.artifactFiles)
                resolvedArtifacts.set(configurations.compile.incoming.artifacts.resolvedArtifacts)
                outputFile.set(layout.buildDirectory.file('output.txt'))
            }
        """

        when:
        succeeds "help"

        and:
        fails "verify"

        then:
        if (FluidDependenciesResolveInterceptor.isFluid()) {
            failure.assertHasCause("Could not resolve all task dependencies for configuration ':compile'.")
        } else {
            failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
        }
        failure.assertHasCause("Could not find org:does-not-exist:1.0.")
    }
}
