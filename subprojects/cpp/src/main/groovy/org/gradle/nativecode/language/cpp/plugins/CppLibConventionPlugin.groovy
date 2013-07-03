/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.nativecode.language.cpp.plugins

import org.gradle.api.Incubating
import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.tasks.bundling.Zip

import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet
import org.gradle.configuration.project.ProjectConfigurationActionContainer

import javax.inject.Inject

/**
 * A convention-based plugin that automatically adds a single C++ source set named "main" and wires it into a {@link org.gradle.nativecode.base.Library} named "main".
 */
@Incubating
class CppLibConventionPlugin implements Plugin<Project> {
    private final ProjectConfigurationActionContainer configureActions

    @Inject
    CppLibConventionPlugin(ProjectConfigurationActionContainer configureActions) {
        this.configureActions = configureActions
    }

    void apply(Project project) {
        project.plugins.apply(CppPlugin)

        project.with {
            sources {
                main {}
            }
            libraries {
                main {
                    baseName = project.name
                    source sources.main.cpp
                }
            }
        }

        configureActions.add {
            project.with {
                def libArtifact = new DefaultPublishArtifact(
                    archivesBaseName, // name
                    "so", // ext
                    "so", // type
                    "so", // classifier
                    null, // date

                    // needs to be more general and not peer into the spec
                    binaries.mainSharedLibrary.outputFile,
                    binaries.mainSharedLibrary
                )

                task("assembleHeaders", type: Zip) {
                    from libraries.main.headers
                    classifier = "headers"
                }

                def headerArtifact = new ArchivePublishArtifact(assembleHeaders)

                extensions.getByType(DefaultArtifactPublicationSet).addCandidate(libArtifact)
                extensions.getByType(DefaultArtifactPublicationSet).addCandidate(headerArtifact)
            }
        }
    }

}