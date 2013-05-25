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

import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Task
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.tasks.Sync
import org.gradle.internal.os.OperatingSystem
import org.gradle.plugins.binaries.BinariesPlugin
import org.gradle.plugins.binaries.model.*
import org.gradle.plugins.cpp.gpp.GppCompilerPlugin
import org.gradle.plugins.cpp.internal.CppCompileSpec
import org.gradle.plugins.cpp.msvcpp.MicrosoftVisualCppPlugin

@Incubating
class CppPlugin implements Plugin<ProjectInternal> {

    void apply(ProjectInternal project) {
        project.plugins.apply(BinariesPlugin)
        project.plugins.apply(MicrosoftVisualCppPlugin)
        project.plugins.apply(GppCompilerPlugin)
        project.extensions.create("cpp", CppExtension, project)

        // Defaults for all cpp source sets
        project.cpp.sourceSets.all { sourceSet ->
            sourceSet.source.srcDir "src/${sourceSet.name}/cpp"
            sourceSet.exportedHeaders.srcDir "src/${sourceSet.name}/headers"
        }

        project.binaries.withType(NativeBinary) { binary ->
            createTasks(project, binary)
        }
    }

    def createTasks(ProjectInternal project, NativeBinary binary) {
        final toolChain = project.extensions.getByType(ToolChainRegistry).defaultToolChain
        CppCompile compileTask = createCompileTask(project, binary, toolChain)
        AbstractLinkTask linkTask = createLinkTask(project, binary, toolChain, compileTask)
        if (binary instanceof ExecutableBinary) {
            createInstallTask(project, binary, linkTask)
        }
    }

    private CppCompile createCompileTask(ProjectInternal project, NativeBinary binary, ToolChain toolChain) {
        CppCompile compileTask = project.task(binary.getTaskName("compile"), type: CppCompile) {
            description = "Compiles $binary"
            group = BasePlugin.BUILD_GROUP
        }
        // TODO:DAZ Make this work with @SkipWhenEmpty
        compileTask.onlyIf {
            !compileTask.source.files.empty
        }

        binary.component.sourceSets.withType(CppSourceSet).all { CppSourceSet sourceSet -> compileTask.from(sourceSet) }

        compileTask.outputDirectory = project.file("${project.buildDir}/objectFiles/${binary.name}")

        compileTask.compiler = toolChain.createCompiler(CppCompileSpec)
        compileTask.sharedLibrary = binary instanceof SharedLibraryBinary
        compileTask.conventionMapping.compilerArgs = { binary.compilerArgs }
        compileTask
    }

    private AbstractLinkTask createLinkTask(ProjectInternal project, NativeBinary binary, ToolChain toolChain, CppCompile compileTask) {
        AbstractLinkTask linkTask = project.task(binary.getTaskName(null), type: linkTaskType(binary)) {
             description = "Links ${binary}"
             group = BasePlugin.BUILD_GROUP
         }
        // TODO:DAZ Make this work with @SkipWhenEmpty
        linkTask.onlyIf {
            !linkTask.source.files.empty
        }
        linkTask.dependsOn compileTask // TODO:DAZ Avoid this explicit dependency by wiring inputs/outputs better

        linkTask.source project.fileTree(compileTask.outputDirectory)

        binary.component.sourceSets.withType(CppSourceSet).all { CppSourceSet sourceSet ->
            linkTask.libs(sourceSet.libs)

            sourceSet.nativeDependencySets.all { NativeDependencySet nativeDependencySet ->
                linkTask.source nativeDependencySet.files
            }
        }

        linkTask.outputFile = { binary.outputFile }
        linkTask.linker = toolChain.createLinker(binary)
        linkTask.conventionMapping.linkerArgs = { binary.linkerArgs }
        binary.component.builtBy(linkTask)
        linkTask
    }


    private static Class<? extends AbstractLinkTask> linkTaskType(NativeBinary binary) {
        if (binary instanceof SharedLibraryBinary) {
            return LinkSharedLibrary
        }
        if (binary instanceof StaticLibraryBinary) {
            return LinkStaticLibrary
        }
        return LinkExecutable
    }

    def createInstallTask(ProjectInternal project, ExecutableBinary executable, Task linkTask) {
        project.task(executable.getTaskName("install"), type: Sync) {
            description = "Installs a development image of $executable"
            into { project.file("${project.buildDir}/install/$executable.name") }
            dependsOn linkTask
            if (OperatingSystem.current().windows) {
                from { executable.component.outputFile }
                from { executable.component.sourceSets*.libs*.outputFile }
            } else {
                into("lib") {
                    from { executable.component.outputFile }
                    from { executable.component.sourceSets*.libs*.outputFile }
                }
                doLast {
                    def script = new File(destinationDir, executable.component.outputFile.name)
                    script.text = """
#/bin/sh
APP_BASE_NAME=`dirname "\$0"`
export DYLD_LIBRARY_PATH="\$APP_BASE_NAME/lib"
export LD_LIBRARY_PATH="\$APP_BASE_NAME/lib"
exec "\$APP_BASE_NAME/lib/${executable.component.outputFile.name}" \"\$@\"
                    """
                    ant.chmod(perm: 'u+x', file: script)
                }
            }
        }
    }
}