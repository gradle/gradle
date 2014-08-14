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
import org.gradle.api.plugins.ExtensionAware
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.internal.LanguageRegistration
import org.gradle.language.base.internal.LanguageSourceSetInternal
import org.gradle.language.base.internal.SourceTransformTaskConfig
import org.gradle.nativebinaries.SharedLibraryBinarySpec
import org.gradle.nativebinaries.Tool
import org.gradle.nativebinaries.internal.NativeBinarySpecInternal
import org.gradle.nativebinaries.language.PreprocessingTool
import org.gradle.nativebinaries.language.c.tasks.AbstractNativeCompileTask
import org.gradle.runtime.base.BinarySpec

// TODO:DAZ Convert to Java
public class CompileTaskConfig implements SourceTransformTaskConfig {
    private final LanguageRegistration<? extends LanguageSourceSet> language;
    private final Class<? extends DefaultTask> taskType;

    public CompileTaskConfig(LanguageRegistration<? extends LanguageSourceSet> languageRegistration, Class<? extends DefaultTask> taskType) {
        this.language = languageRegistration;
        this.taskType = taskType
    }

    @Override
    String getTaskPrefix() {
        return "compile"
    }

    @Override
    Class<? extends DefaultTask> getTaskType() {
        return taskType
    }

    @Override
    void configureTask(Task task, BinarySpec binary, LanguageSourceSet sourceSet) {
        configureCompileTask(task as AbstractNativeCompileTask, binary as NativeBinarySpecInternal, sourceSet as LanguageSourceSetInternal)
    }

    private AbstractNativeCompileTask configureCompileTask(AbstractNativeCompileTask task, NativeBinarySpecInternal binary, LanguageSourceSetInternal sourceSet) {
        task.setDescription("Compiles the $sourceSet of $binary");

        task.setToolChain(binary.getToolChain());
        task.setTargetPlatform(binary.getTargetPlatform());
        task.setPositionIndependentCode(binary instanceof SharedLibraryBinarySpec);

        task.includes {
            sourceSet.exportedHeaders.srcDirs
        }
        task.includes {
            binary.getLibs(sourceSet)*.includeRoots
        }

        task.source(sourceSet.getSource());

        Project project = task.getProject();
        task.objectFileDir = project.file("${project.buildDir}/objs/${binary.namingScheme.outputDirectoryBase}/${sourceSet.fullName}");

        for (String toolName : language.getBinaryTools().keySet()) {
            Tool tool = (Tool) ((ExtensionAware) binary).getExtensions().getByName(toolName);
            if (tool instanceof PreprocessingTool) {
                task.setMacros(((PreprocessingTool) tool).getMacros());
            }
            task.setCompilerArgs(tool.getArgs());
        }

        binary.getTasks().createOrLink.source task.outputs.files.asFileTree.matching { include '**/*.obj', '**/*.o' }

        return task;
    }
}