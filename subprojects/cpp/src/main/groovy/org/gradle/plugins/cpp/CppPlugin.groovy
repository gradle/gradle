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
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.tasks.Sync
import org.gradle.plugins.binaries.BinariesPlugin
import org.gradle.plugins.binaries.model.Binary
import org.gradle.plugins.binaries.model.Executable
import org.gradle.plugins.binaries.model.internal.DefaultCompilerRegistry
import org.gradle.plugins.binaries.tasks.Compile
import org.gradle.plugins.cpp.gpp.GppCompilerPlugin
import org.gradle.plugins.cpp.gpp.internal.GppCompileSpecFactory
import org.gradle.plugins.cpp.msvcpp.MicrosoftVisualCppPlugin
import org.gradle.util.GUtil

class CppPlugin implements Plugin<ProjectInternal> {

    void apply(ProjectInternal project) {
        project.plugins.apply(BinariesPlugin)
        project.plugins.apply(GppCompilerPlugin)
        project.plugins.apply(MicrosoftVisualCppPlugin)
        project.extensions.create("cpp", CppExtension, project)

        project.extensions.getByType(DefaultCompilerRegistry).specFactory = new GppCompileSpecFactory(project)

        // Defaults for all cpp source sets
        project.cpp.sourceSets.all { sourceSet ->
            sourceSet.source.srcDir "src/${sourceSet.name}/cpp"
            sourceSet.exportedHeaders.srcDir "src/${sourceSet.name}/headers"
        }

        // Defaults for all executables
        project.executables.all { executable ->
            configureExecutable(project, executable)
        }

        // Defaults for all libraries
        project.libraries.all { library ->
            configureBinary(project, library)
        }
    }

    def configureExecutable(ProjectInternal project, Executable executable) {
        configureBinary(project, executable)

        def baseName = GUtil.toCamelCase(executable.name).capitalize()
        project.task("install${baseName}", type: Sync) {
            description = "Installs a development image of $executable"
            into { project.file("${project.buildDir}/install/$executable.name") }
            dependsOn executable
            from { executable.spec.outputFile }
            from { executable.sourceSets*.libs*.spec*.outputFile }
        }
    }

    def configureBinary(ProjectInternal project, Binary binary) {
        def baseName = GUtil.toCamelCase(binary.name).capitalize()

        def task = project.task("compile${baseName}", type: Compile) {
            description = "Compiles and links $binary"
            group = BasePlugin.BUILD_GROUP
        }
        binary.spec.configure(task)
    }
}