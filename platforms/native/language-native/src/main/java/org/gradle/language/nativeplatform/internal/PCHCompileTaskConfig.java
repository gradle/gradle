/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.nativeplatform.internal;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.language.nativeplatform.tasks.AbstractNativeCompileTask;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.nativeplatform.tasks.PrefixHeaderFileGenerateTask;
import org.gradle.nativeplatform.toolchain.internal.PreCompiledHeader;

import java.io.File;

public class PCHCompileTaskConfig extends CompileTaskConfig {
    public PCHCompileTaskConfig(NativeLanguageTransform<?> languageTransform, Class<? extends DefaultTask> taskType) {
        super(languageTransform, taskType);
    }

    @Override
    protected void configureCompileTask(AbstractNativeCompileTask task, final NativeBinarySpecInternal binary, final LanguageSourceSetInternal languageSourceSet) {
        // Note that the sourceSet is the sourceSet this pre-compiled header will be used with - it's not an
        // input sourceSet to the compile task.
        final DependentSourceSetInternal sourceSet = (DependentSourceSetInternal) languageSourceSet;

        task.setDescription("Compiles a pre-compiled header for the " + sourceSet + " of " + binary);

        // Add the source of the source set to the include paths to resolve any headers that may be in source directories
        task.includes(sourceSet.getSource().getSourceDirectories());

        final Project project = task.getProject();
        task.source(sourceSet.getPrefixHeaderFile());


        task.getObjectFileDir().fileProvider(project.getLayout().getBuildDirectory().getAsFile().map(it -> new File(binary.getNamingScheme().getOutputDirectory(it, "objs"), languageSourceSet.getProjectScopedName() + "PCH")));

        task.dependsOn(project.getTasks().withType(PrefixHeaderFileGenerateTask.class).matching(new Spec<PrefixHeaderFileGenerateTask>() {
            @Override
            public boolean isSatisfiedBy(PrefixHeaderFileGenerateTask prefixHeaderFileGenerateTask) {
                return prefixHeaderFileGenerateTask.getPrefixHeaderFile().equals(sourceSet.getPrefixHeaderFile());
            }
        }));

        // This is so that VisualCpp has the object file of the generated source file available at link time
        binary.binaryInputs(task.getOutputs().getFiles().getAsFileTree().matching(new PatternSet().include("**/*.obj", "**/*.o")));

        PreCompiledHeader pch = binary.getPrefixFileToPCH().get(sourceSet.getPrefixHeaderFile());
        pch.setPchObjects(task.getOutputs().getFiles().getAsFileTree().matching(new PatternSet().include("**/*.pch", "**/*.gch")));
        pch.builtBy(task);
    }
}
