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
package org.gradle.nativecode.base.plugins

import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.BasePlugin
import org.gradle.language.base.BinaryContainer
import org.gradle.nativecode.base.*
import org.gradle.nativecode.base.internal.NativeBinaryInternal
import org.gradle.nativecode.base.tasks.*

/**
 * A plugin that creates tasks used for constructing native binaries.
 */
@Incubating
public class NativeBinariesPlugin implements Plugin<Project> {

    public void apply(final Project project) {
        project.getPlugins().apply(NativeBinariesModelPlugin.class);
        final BinaryContainer binaries = project.getExtensions().getByType(BinaryContainer.class);

        binaries.withType(NativeBinary) { NativeBinaryInternal binary ->
            bindSourceSetLibsToBinary(binary)
            createTasks(project, binary)
        }
    }

    private static void bindSourceSetLibsToBinary(binary) {
        // TODO:DAZ Move this logic into NativeBinary (once we have laziness sorted)
        binary.source.withType(DependentSourceSet).all { DependentSourceSet sourceSet ->
            sourceSet.libs.each { NativeDependencySet lib ->
                binary.lib lib
            }
        }
    }

    def createTasks(ProjectInternal project, NativeBinaryInternal binary) {
        BuildBinaryTask buildBinaryTask
        if (binary instanceof StaticLibraryBinary) {
            buildBinaryTask = createStaticLibraryTask(project, binary)
        } else {
            buildBinaryTask = createLinkTask(project, binary)
        }
        binary.builderTask = buildBinaryTask

        if (binary instanceof ExecutableBinary) {
            createInstallTask(project, (NativeBinaryInternal) binary);
        }
    }

    private AbstractLinkTask createLinkTask(ProjectInternal project, NativeBinaryInternal binary) {
        AbstractLinkTask linkTask = project.task(binary.namingScheme.getTaskName("link"), type: linkTaskType(binary)) {
             description = "Links ${binary}"
             group = BasePlugin.BUILD_GROUP
         }

        linkTask.toolChain = binary.toolChain

        binary.libs.each { NativeDependencySet lib ->
            linkTask.lib lib.linkFiles
        }

        linkTask.conventionMapping.outputFile = { binary.outputFile }
        linkTask.conventionMapping.linkerArgs = { binary.linkerArgs }
        return linkTask
    }

    private static Class<? extends AbstractLinkTask> linkTaskType(NativeBinary binary) {
        if (binary instanceof SharedLibraryBinary) {
            return LinkSharedLibrary
        }
        return LinkExecutable
    }

    private CreateStaticLibrary createStaticLibraryTask(ProjectInternal project, NativeBinaryInternal binary) {
        CreateStaticLibrary task = project.task(binary.namingScheme.getTaskName("create"), type: CreateStaticLibrary) {
             description = "Creates ${binary}"
             group = BasePlugin.BUILD_GROUP
         }

        task.toolChain = binary.toolChain
        task.conventionMapping.outputFile = { binary.outputFile }
        return task
    }

    def createInstallTask(ProjectInternal project, NativeBinaryInternal executable) {
        InstallExecutable installTask = project.task(executable.namingScheme.getTaskName("install"), type: InstallExecutable) {
            description = "Installs a development image of $executable"
            group = BasePlugin.BUILD_GROUP
        }

        installTask.conventionMapping.destinationDir = { project.file("${project.buildDir}/install/$executable.name") }

        installTask.conventionMapping.executable = { executable.outputFile }
        installTask.lib { executable.libs*.runtimeFiles }

        installTask.dependsOn(executable)
    }
}