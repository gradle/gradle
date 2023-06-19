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
//file:noinspection GrMethodMayBeStatic

package org.gradle.integtests.fixtures.resolve

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ComponentSelectionCause
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.util.Path

/**
 * This task writes information about a resolved graph to a file in a flat format (one line per item).
 *
 * It exists in order to compare the result of resolution to an expectation; and to ensure that the multiple
 * ways of accessing artifacts and files from a resolved graph are consistent.
 *
 * This task intentionally does <strong>NOT</strong> test the {@link org.gradle.api.artifacts.ResolvedConfiguration ResolvedConfiguration} API,
 * which ought to be considered legacy and deprecated for internal use.  That type's behavior will be verified elsewhere.
 * This is meant to be Configuration Cache compatible, so it does not store and test the {@link org.gradle.api.artifacts.result.ResolutionResult ResolutionResult}
 * directly, but stores the root node of the graph and walks it to compare the components and dependencies instead.
 */
abstract class GenerateGraphTask extends DefaultTask {
    @Internal
    File outputFile

    @Internal
    boolean buildArtifacts

    @Internal
    abstract Property<ResolvedComponentResult> getRootComponent()

    @Internal
    abstract ConfigurableFileCollection getFiles()

    @Internal
    FileCollection incomingFiles

    @Internal
    ArtifactCollection incomingArtifacts

    @Internal
    FileCollection artifactViewFiles

    @Internal
    ArtifactCollection artifactViewArtifacts

    @Internal
    FileCollection lenientArtifactViewFiles

    @Internal
    ArtifactCollection lenientArtifactViewArtifacts

    GenerateGraphTask() {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    void generateOutput() {
        outputFile.parentFile.mkdirs()
        //noinspection GroovyMissingReturnStatement
        outputFile.withPrintWriter { writer ->
            def root = rootComponent.get()

            def components = new LinkedHashSet()
            def dependencies = new LinkedHashSet()
            collectAllComponentsAndEdges(root, components, dependencies)

            // These are always checked regardless of whether or not building artifacts is requested
            writeGraphStructure(writer, root, components, dependencies)

            incomingArtifacts.artifacts.each {
                writeArtifact("incoming-artifact-artifact", writer, it)
            }

            if (buildArtifacts) {
                files.each {
                    writeFile("file-file", writer, it)
                }
                files.filter { true }.each {
                    writeFile("file-filtered", writer, it)
                }

                incomingFiles.each {
                    writeFile("incoming-file", writer, it)
                }
                incomingArtifacts.each {
                    writeArtifact("incoming-artifact", writer, it)
                }
                incomingArtifacts.resolvedArtifacts.get().each {
                    writeArtifact("incoming-resolved-artifact", writer, it)
                }
                incomingArtifacts.artifactFiles.each {
                    writeFile("incoming-artifact-file", writer, it)
                }

                artifactViewFiles.each {
                    writeFile("artifact-view-file", writer, it)
                }
                artifactViewArtifacts.each {
                    writeArtifact("artifact-view-artifact", writer, it)
                }
                artifactViewFiles.files.each {
                    writeFile("artifact-view-file-file", writer, it)
                }
                artifactViewArtifacts.artifacts.each {
                    writeArtifact("artifact-view-artifact-artifact", writer, it)
                }
                artifactViewArtifacts.resolvedArtifacts.get().each {
                    writeArtifact("artifact-view-resolved-artifact", writer, it)
                }
                artifactViewArtifacts.artifactFiles.each {
                    writeFile("artifact-view-artifact-file", writer, it)
                }

                lenientArtifactViewFiles.each {
                    writeFile("lenient-artifact-view-file", writer, it)
                }
                lenientArtifactViewArtifacts.each {
                    writeArtifact("lenient-artifact-view-artifact", writer, it)
                }
                lenientArtifactViewFiles.files.each {
                    writeFile("lenient-artifact-view-file-file", writer, it)
                }
                lenientArtifactViewArtifacts.artifacts.each {
                    writeArtifact("lenient-artifact-view-artifact-artifact", writer, it)
                }
                lenientArtifactViewArtifacts.resolvedArtifacts.get().each {
                    writeArtifact("lenient-artifact-view-resolved-artifact", writer, it)
                }
                lenientArtifactViewArtifacts.artifactFiles.each {
                    writeFile("lenient-artifact-view-artifact-file", writer, it)
                }
            }
        }
    }

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

    protected void writeGraphStructure(PrintWriter writer, ResolvedComponentResult root, Collection<ResolvedComponentResult> components, Collection<DependencyResult> dependencies) {
        writer.println("root:${formatComponent(root)}")
        components.each {
            writer.println("component:${formatComponent(it)}")
        }
        dependencies.each {
            writer.println("dependency:${it.constraint ? '[constraint]' : ''}[from:${it.from.id}][${it.requested}->${it.selected.id}]")
        }
    }

    protected String formatComponent(ResolvedComponentResult result) {
        String type
        if (result.id instanceof ProjectComponentIdentifier) {
            type = "project:${Path.path(result.id.build.buildPath).append(Path.path(result.id.projectPath))}"
        } else if (result.id instanceof ModuleComponentIdentifier) {
            type = "module:${result.id.group}:${result.id.module}:${result.id.version},${result.id.moduleIdentifier.group}:${result.id.moduleIdentifier.name}"
        } else {
            type = "other"
        }
        String variants = result.variants.collect { variant ->
            "variant:${formatVariant(variant)}"
        }.join('@@')
        "[$type][id:${result.id}][mv:${result.moduleVersion}][reason:${formatReason(result.selectionReason as ComponentSelectionReasonInternal)}][$variants]"
    }

    protected String formatVariant(ResolvedVariantResult variant) {
        return "name:${variant.displayName} attributes:${formatAttributes(variant.attributes)}"
    }

    protected String formatAttributes(AttributeContainer attributes) {
        attributes.keySet().collect {
            "$it.name=${attributes.getAttribute(it as Attribute<Object>)}"
        }.sort().join(',')
    }

    protected String formatReason(ComponentSelectionReasonInternal reason) {
        def reasons = reason.descriptions.collect {
            def message
            if (it.hasCustomDescription() && it.cause != ComponentSelectionCause.REQUESTED) {
                message = "${it.cause.defaultReason}: ${it.description}"
            } else {
                message = it.description
            }
            message.readLines().join(" ")
        }.join('!!')
        return reasons
    }

    protected void writeFile(String linePrefix, PrintWriter writer, File file) {
        writer.println("$linePrefix:${file.name}")
    }

    protected void writeArtifact(String linePrefix, PrintWriter writer, ResolvedArtifactResult artifact) {
        writer.println("$linePrefix:${artifact.id}")
    }
}
