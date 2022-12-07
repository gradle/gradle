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

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

class GenerateGraphTask extends AbstractGenerateGraphTask {
    @Internal
    Configuration configuration

    @TaskAction
    def generateOutput() {
        outputFile.parentFile.mkdirs()

        outputFile.withPrintWriter { writer ->
            configuration.resolvedConfiguration.firstLevelModuleDependencies.each {
                writer.println("first-level:[${it.moduleGroup}:${it.moduleName}:${it.moduleVersion}:${it.configuration}]")
            }
            configuration.resolvedConfiguration.getFirstLevelModuleDependencies { true }.each {
                writer.println("first-level-filtered:[${it.moduleGroup}:${it.moduleName}:${it.moduleVersion}:${it.configuration}]")
            }
            configuration.resolvedConfiguration.lenientConfiguration.firstLevelModuleDependencies.each {
                writer.println("lenient-first-level:[${it.moduleGroup}:${it.moduleName}:${it.moduleVersion}:${it.configuration}]")
            }
            configuration.resolvedConfiguration.lenientConfiguration.getFirstLevelModuleDependencies { true }.each {
                writer.println("lenient-first-level-filtered:[${it.moduleGroup}:${it.moduleName}:${it.moduleVersion}:${it.configuration}]")
            }
            visitNodes(configuration.resolvedConfiguration.firstLevelModuleDependencies, writer, new HashSet<>())

            def resolutionResult = configuration.incoming.resolutionResult
            writeResolutionResult(writer, resolutionResult.root, resolutionResult.allComponents, resolutionResult.allDependencies)

            if (buildArtifacts) {
                configuration.each {
                    writer.println("file:${it.name}")
                }
                configuration.files.each {
                    writer.println("file-files:${it.name}")
                }
                configuration.incoming.files.each {
                    writer.println("file-incoming:${it.name}")
                }
                configuration.incoming.artifacts.artifacts.each {
                    writer.println("file-artifact-incoming:${it.file.name}")
                }
                configuration.files { true }.each {
                    writer.println("file-filtered:${it.name}")
                }
                configuration.fileCollection { true }.each {
                    writer.println("file-collection-filtered:${it.name}")
                }
                configuration.resolvedConfiguration.files.each {
                    writer.println("file-resolved-config:${it.name}")
                }
                configuration.resolvedConfiguration.getFiles { true }.each {
                    writer.println("file-resolved-config-filtered:${it.name}")
                }
                configuration.resolvedConfiguration.resolvedArtifacts.each {
                    writer.println("file-artifact-resolved-config:${it.file.name}")
                }
                configuration.resolvedConfiguration.lenientConfiguration.files.each {
                    writer.println("file-lenient-config:${it.name}")
                }
                configuration.resolvedConfiguration.lenientConfiguration.getFiles { true }.each {
                    writer.println("file-lenient-config-filtered:${it.name}")
                }
                configuration.resolvedConfiguration.lenientConfiguration.artifacts.each {
                    writer.println("file-artifact-lenient-config:${it.file.name}")
                }
                configuration.resolvedConfiguration.lenientConfiguration.getArtifacts { true }.each {
                    writer.println("file-artifact-lenient-config-filtered:${it.file.name}")
                }
            }

            configuration.incoming.artifacts.artifacts.each {
                writer.println("artifact-incoming:${it.id}")
            }
            configuration.resolvedConfiguration.resolvedArtifacts.each {
                writer.println("artifact:[${it.moduleVersion.id}][${it.name}:${it.classifier}:${it.extension}:${it.type}]")
            }
            configuration.resolvedConfiguration.lenientConfiguration.artifacts.each {
                writer.println("lenient-artifact:[${it.moduleVersion.id}][${it.name}:${it.classifier}:${it.extension}:${it.type}]")
            }
            configuration.resolvedConfiguration.lenientConfiguration.getArtifacts { true }.each {
                writer.println("filtered-lenient-artifact:[${it.moduleVersion.id}][${it.name}:${it.classifier}:${it.extension}:${it.type}]")
            }
        }
    }

    def visitNodes(Collection<ResolvedDependency> nodes, PrintWriter writer, Set<ResolvedDependency> visited) {
        for (ResolvedDependency node : nodes) {
            if (!visited.add(node)) {
                continue
            }
            writer.println("configuration:[${node.moduleGroup}:${node.moduleName}:${node.moduleVersion}]")
            visitNodes(node.children, writer, visited)
        }
    }
}
