/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.nativebinaries.language.internal

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.internal.SourceTransformTaskConfig
import org.gradle.language.rc.WindowsResourceSet
import org.gradle.nativebinaries.internal.NativeBinarySpecInternal
import org.gradle.nativebinaries.internal.StaticLibraryBinarySpecInternal
import org.gradle.nativebinaries.language.rc.tasks.WindowsResourceCompile
import org.gradle.runtime.base.BinarySpec

// TODO:DAZ Convert to Java
public class WindowsResourcesCompileTaskConfig implements SourceTransformTaskConfig {

    String getTaskPrefix() {
        return "compile"
    }

    Class<? extends DefaultTask> getTaskType() {
        return WindowsResourceCompile.class
    }

    void configureTask(Task task, BinarySpec binary, LanguageSourceSet sourceSet) {
        configureResourceCompileTask(task as WindowsResourceCompile, binary as NativeBinarySpecInternal, sourceSet as WindowsResourceSet)
    }

    private void configureResourceCompileTask(WindowsResourceCompile task, NativeBinarySpecInternal binary, WindowsResourceSet sourceSet) {
        task.description = "Compiles resources of the $sourceSet of $binary"

        task.toolChain = binary.toolChain
        task.targetPlatform = binary.targetPlatform

        task.includes {
            sourceSet.exportedHeaders.srcDirs
        }
        task.source sourceSet.source

        Project project = task.getProject();
        task.outputDir = project.file("${project.buildDir}/objs/${binary.namingScheme.outputDirectoryBase}/${sourceSet.fullName}")

        task.macros = binary.rcCompiler.macros
        task.compilerArgs = binary.rcCompiler.args

        final resourceOutputs = task.outputs.files.asFileTree.matching { include '**/*.res' }
        binary.tasks.createOrLink.source resourceOutputs
        if (binary instanceof StaticLibraryBinarySpecInternal) {
            binary.additionalLinkFiles resourceOutputs
        }
    }
}