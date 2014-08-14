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
import org.gradle.language.base.internal.LanguageSourceSetInternal
import org.gradle.language.base.internal.SourceTransformTaskConfig
import org.gradle.nativebinaries.internal.NativeBinarySpecInternal
import org.gradle.nativebinaries.language.assembler.tasks.Assemble
import org.gradle.nativebinaries.language.c.tasks.AbstractNativeCompileTask
import org.gradle.runtime.base.BinarySpec

// TODO:DAZ Convert to Java
public class AssembleTaskConfig implements SourceTransformTaskConfig {

    @Override
    String getTaskPrefix() {
        return "assemble"
    }

    @Override
    Class<? extends DefaultTask> getTaskType() {
        return Assemble.class
    }

    @Override
    void configureTask(Task task, BinarySpec binary, LanguageSourceSet sourceSet) {
        configureAssembleTask(task as Assemble, binary as NativeBinarySpecInternal, sourceSet as LanguageSourceSetInternal)
    }

    private AbstractNativeCompileTask configureAssembleTask(Assemble task, NativeBinarySpecInternal binary, LanguageSourceSetInternal sourceSet) {
        task.setDescription("Assembles the $sourceSet of $binary");


        task.setToolChain(binary.getToolChain());
        task.setTargetPlatform(binary.getTargetPlatform());

        task.source(sourceSet.getSource());

        Project project = task.getProject()
        task.objectFileDir = project.file("${project.buildDir}/objs/${binary.namingScheme.outputDirectoryBase}/${sourceSet.fullName}")

        task.assemblerArgs = binary.assembler.args

        binary.getTasks().createOrLink.source task.outputs.files.asFileTree.matching { include '**/*.obj', '**/*.o' }
    }
}