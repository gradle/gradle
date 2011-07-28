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
import org.gradle.api.plugins.BasePlugin

class CppPlugin implements Plugin<Project> {

    void apply(Project project) {
        project.plugins.apply(BasePlugin)

        def extension = new CppProjectExtension(project)
        configureSourceSetDefaults(extension)
        
        project.extensions.add('cpp', extension)
    }

    private configureSourceSetDefaults(CppProjectExtension extension) {
        extension.sourceSets.all { sourceSet ->
            sourceSet.cpp.srcDir "src/${sourceSet.name}/cpp"
            sourceSet.headers.srcDir "src/${sourceSet.name}/headers"
        }

        // Wire up all binaries to their matching source set
        extension.binaries.all { binary ->
            extension.sourceSets.matching { it.name == binary.name }.all { sourceSet ->
                binary.spec {
                    includes sourceSet.headers
                    source sourceSet.cpp
                }
            }
        }

        extension.sourceSets.create("main")
        extension.binaries.create("main")
    }

}