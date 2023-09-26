/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.extensions.FluidDependenciesResolveInterceptor
import org.gradle.integtests.fixtures.extensions.FluidDependenciesResolveTest

@FluidDependenciesResolveTest
class ArtifactCollectionIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def setup() {
        createDirs("project-lib")
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

            class TaskWithArtifactCollectionInput extends DefaultTask {
                @Internal
                ArtifactCollection artifacts

                @InputFiles
                FileCollection getArtifactFiles() {
                    return artifacts.getArtifactFiles()
                }

                @OutputFile File outputFile
            }
"""
    }

    def "artifact collection has resolved artifact files and metadata"() {
        when:
        buildFile << """
            def artifacts = configurations.compile.incoming.artifacts

            task checkArtifacts {
                doLast {
                    def artifactFiles = artifacts.artifactFiles
                    def artifactResults = artifacts.artifacts

                    assert artifactResults.size() == 3

                    // Check external artifact
                    def idx = artifacts.findIndexOf { it.file.name == 'external-lib-1.0.jar' }

                    def result = artifactResults[idx]
                    assert result.file == artifactFiles[idx]

                    assert result.id instanceof org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
                    assert result.id.componentIdentifier.group == 'org.external'
                    assert result.id.componentIdentifier.module == 'external-lib'
                    assert result.id.componentIdentifier.version == '1.0'
                    assert result.id.fileName == 'external-lib-1.0.jar'

                    // Check project artifact
                    idx = artifacts.findIndexOf { it.file.name == 'project-lib.jar' }

                    result = artifactResults[idx]
                    assert result.file == artifactFiles[idx]

                    assert result.id.componentIdentifier instanceof ProjectComponentIdentifier
                    assert result.id.componentIdentifier.projectPath == ':project-lib'

                    // Check file artifact
                    idx = artifacts.findIndexOf { it.file.name == 'file-lib.jar' }

                    result = artifactResults[idx]
                    assert result.file == artifactFiles[idx]

                    assert result.id instanceof org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier
                    assert result.id.componentIdentifier == result.id
                    assert result.id.displayName == 'file-lib.jar'
                }
            }
"""

        then:
        succeeds "checkArtifacts"
    }

    def "can use artifact collection as task input"() {
        given:
        buildFile << """
            task verify(type: TaskWithArtifactCollectionInput) {
                artifacts = configurations.compile.incoming.artifacts
                outputFile = file('out')

                doLast {
                    assert artifacts.artifacts.size() == 3
                }

            }
"""

        expect:
        succeeds "verify"
    }

    def "task is not up-to-date when files of artifact collection input changes"() {
        given:
        buildFile << """
            task verify(type: TaskWithArtifactCollectionInput) {
                artifacts = configurations.compile.incoming.artifacts
                outputFile = file('out')

                doLast {
                    assert artifacts.artifacts.size() == 3
                }

            }
"""
        def sourceFile = file("project-lib/src/main/java/Main.java")
        sourceFile << """
class Main {}
"""
        sourceFile.makeOlder()

        when:
        succeeds "verify"

        then:
        executedAndNotSkipped ":project-lib:jar", ":verify"

        when:
        succeeds "verify"

        then:
        skipped ":project-lib:jar", ":verify"

        when:
        sourceFile.text = """
class Main {
    public static void main(String[] args) {}
}
"""
        succeeds "verify"

        then:
        executedAndNotSkipped ":project-lib:jar", ":verify"
    }

    def "failure to resolve artifact collection"() {
        given:
        buildFile << """
            dependencies {
                compile 'org:does-not-exist:1.0'
            }

            task verify(type: TaskWithArtifactCollectionInput) {
                artifacts = configurations.compile.incoming.artifacts
                outputFile = file('out')

                doLast {
                    assert artifacts.artifacts.size() == 3
                }
            }
        """

        when:
        succeeds "help"

        if (JavaVersion.current().isJava9Compatible() && GradleContextualExecuter.isConfigCache()) {
            // For java.util.concurrent.CopyOnWriteArrayList from DefaultMultiCauseException being serialized reflectively by configuration cache
            executer.withArgument('-Dorg.gradle.jvmargs=--add-opens java.base/java.util.concurrent=ALL-UNNAMED')
        }
        fails "verify"

        then:
        if (FluidDependenciesResolveInterceptor.isFluid()) {
            failure.assertHasDescription("Could not determine the dependencies of task ':verify'.")
            failure.assertHasCause("Could not resolve all task dependencies for configuration ':compile'.")
            failure.assertHasCause("Could not find org:does-not-exist:1.0.")
        } else {
            failure.assertHasDescription("Execution failed for task ':verify'.")
            failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
            failure.assertHasCause("Could not find org:does-not-exist:1.0.")
        }
    }
}
