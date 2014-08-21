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
package org.gradle.language.assembler.plugins.internal;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.nativeplatform.Tool;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.language.assembler.tasks.Assemble;
import org.gradle.platform.base.BinarySpec;

public class AssembleTaskConfig implements SourceTransformTaskConfig {
    public String getTaskPrefix() {
        return "assemble";
    }

    public Class<? extends DefaultTask> getTaskType() {
        return Assemble.class;
    }

    public void configureTask(Task task, BinarySpec binary, LanguageSourceSet sourceSet) {
        configureAssembleTask((Assemble) task, (NativeBinarySpecInternal) binary, (LanguageSourceSetInternal) sourceSet);
    }

    private void configureAssembleTask(Assemble task, final NativeBinarySpecInternal binary, final LanguageSourceSetInternal sourceSet) {
        task.setDescription(String.format("Assembles the %s of %s", sourceSet, binary));

        task.setToolChain(binary.getToolChain());
        task.setTargetPlatform(binary.getTargetPlatform());

        task.source(sourceSet.getSource());

        final Project project = task.getProject();
        task.setObjectFileDir(project.file(project.getBuildDir() + "/objs/" + binary.getNamingScheme().getOutputDirectoryBase() + "/" + sourceSet.getFullName()));

        Tool assemblerTool = (Tool) ((ExtensionAware) binary).getExtensions().getByName("assembler");
        task.setAssemblerArgs(assemblerTool.getArgs());

        binary.getTasks().getCreateOrLink().source(task.getOutputs().getFiles().getAsFileTree().matching(new PatternSet().include("**/*.obj", "**/*.o")));
    }
}
