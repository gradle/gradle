/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.integtests.fixtures.resolve

import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class ConfigurationCacheCompatibleGenerateGraphTask extends AbstractGenerateGraphTask {
    @Internal
    abstract Property<ResolvedComponentResult> getRootComponent()

    @Internal
    abstract ConfigurableFileCollection getFiles()

    @Internal
    ArtifactCollection incomingArtifacts

    @Internal
    FileCollection incomingFiles
    
    @Internal
    FileCollection artifactViewFiles

    @Internal
    ArtifactCollection artifactViewArtifacts

    @Internal
    FileCollection lenientArtifactViewFiles

    @Internal
    ArtifactCollection lenientArtifactViewArtifacts

    @TaskAction
    void generateOutput() {
        outputFile.parentFile.mkdirs()
        outputFile.withPrintWriter { writer ->
            def root = rootComponent.get()

            def components = new LinkedHashSet()
            def dependencies = new LinkedHashSet()
            collectAllComponentsAndEdges(root, components, dependencies)

            // These are always checked
            writeRootAndComponentsAndDependencies(writer, root, components, dependencies)

            // As are these
            incomingArtifacts.artifacts.each {
                writeArtifact("artifact-incoming", writer, it)
            }

            if (buildArtifacts) {
                files.each {
                    writer.println("file-files:${it.name}")
                }
                incomingFiles.each {
                    writer.println("file-incoming:${it.name}")
                }
                files.filter { true }.each {
                    writer.println("file-filtered:${it.name}")
                }

                incomingArtifacts.artifacts.each {
                    writer.println("file-artifact-incoming:${it.file.name}")
                }

                artifactViewFiles.each {
                    writer.println("artifact-view-files:${it.name}")
                }
                artifactViewArtifacts.each {
                    writeArtifact("artifact-view-artifacts", writer, it)
                }
                artifactViewFiles.files.each {
                    writer.println("artifact-view-files-set:${it.name}")
                }
                artifactViewArtifacts.artifacts.each {
                    writeArtifact("artifact-view-artifacts-set", writer, it)
                }
                lenientArtifactViewFiles.each {
                    writer.println("artifact-view-lenient-files:${it.name}")
                }
                lenientArtifactViewArtifacts.each {
                    writeArtifact("artifact-view-lenient-artifacts", writer, it)
                }
                lenientArtifactViewFiles.files.each {
                    writer.println("artifact-view-lenient-files-set:${it.name}")
                }
                lenientArtifactViewArtifacts.artifacts.each {
                    writeArtifact("artifact-view-lenient-artifacts-set", writer, it)
                }
            }
        }
    }

    @SuppressWarnings('GrMethodMayBeStatic')
    protected void writeArtifact(String linePrefix, PrintWriter writer, ResolvedArtifactResult artifact) {
        writer.println("$linePrefix:${artifact.id}")
    }

    @SuppressWarnings('GrMethodMayBeStatic')
    protected void collectAllComponentsAndEdges(ResolvedComponentResult root, Collection<ResolvedComponentResult> components, Collection<DependencyResult> dependencies) {
        def queue = [root]
        def seen = new HashSet()

        while (!queue.isEmpty()) {
            def node = queue.remove(0)
            if (seen.add(node)) {
                components.add(node)
                for (final def dep in node.getDependencies()) {
                    dependencies.add(dep)
                    if (dep instanceof ResolvedDependencyResult) {
                        queue.add(dep.selected)
                    }
                }
            } // else, already seen
        }
    }
}
