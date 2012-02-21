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

import org.gradle.api.Plugin
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.plugins.binaries.BinariesPlugin
import org.gradle.plugins.binaries.model.internal.DefaultCompilerRegistry
import org.gradle.plugins.binaries.tasks.Compile
import org.gradle.plugins.cpp.gpp.GppCompilerPlugin
import org.gradle.plugins.cpp.gpp.internal.GppCompileSpecFactory
import org.gradle.plugins.cpp.msvcpp.MicrosoftVisualCppPlugin

class CppPlugin implements Plugin<ProjectInternal> {

    void apply(ProjectInternal project) {
        project.plugins.apply(BinariesPlugin)
        project.plugins.apply(GppCompilerPlugin)
        project.plugins.apply(MicrosoftVisualCppPlugin)
        project.extensions.addDecorated("cpp", CppExtension, project)

        project.extensions.getByType(DefaultCompilerRegistry).specFactory = new GppCompileSpecFactory(project)

        // Defaults for all cpp source sets
        project.cpp.sourceSets.all { sourceSet ->
            sourceSet.source.srcDir "src/${sourceSet.name}/cpp"
            sourceSet.exportedHeaders.srcDir "src/${sourceSet.name}/headers"
        }

        // Defaults for all executables
        project.executables.all { executable ->
            def task = project.task("compile${name.capitalize()}", type: Compile)
            executable.spec.configure(task)
        }

        // Defaults for all libraries
        project.libraries.all { library ->
            def task = project.task("compile${name.capitalize()}", type: Compile)
            library.spec.configure(task)
        }
    }

}