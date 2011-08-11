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
package org.gradle.plugins.cpp

import org.gradle.api.Project
import org.gradle.api.Plugin

import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact

class CppExeConventionPlugin implements Plugin<Project> {

    void apply(Project project) {
        project.apply(plugin: "cpp")
        
        project.with {
            cpp {
                sourceSets {
                    main {}
                }
            }
            executables {
                main {
                    sourceSets << cpp.sourceSets.main
                }
            }
            
            def exeArtifact = new DefaultPublishArtifact(
                archivesBaseName, // name
                "exe", // ext
                "exe", // type
                null, // classifier
                null, // date

                // needs to be more general and not peer into the spec
                executables.main.spec.outputFile,
                executables.main.spec.task
            )

            artifacts {
                archives exeArtifact
            }
        }
    }

}