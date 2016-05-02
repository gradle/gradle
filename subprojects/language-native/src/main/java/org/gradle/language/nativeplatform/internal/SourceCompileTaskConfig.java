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
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.language.base.internal.registry.LanguageTransform;
import org.gradle.language.nativeplatform.tasks.AbstractNativeCompileTask;
import org.gradle.language.nativeplatform.tasks.AbstractNativeSourceCompileTask;
import org.gradle.nativeplatform.ObjectFile;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.nativeplatform.toolchain.internal.PreCompiledHeader;

import java.io.File;

public class SourceCompileTaskConfig extends CompileTaskConfig {
    public SourceCompileTaskConfig(LanguageTransform<? extends LanguageSourceSet, ObjectFile> languageTransform, Class<? extends DefaultTask> taskType) {
        super(languageTransform, taskType);
    }

    @Override
    protected void configureCompileTask(AbstractNativeCompileTask abstractTask, final NativeBinarySpecInternal binary, final LanguageSourceSetInternal sourceSet) {
        AbstractNativeSourceCompileTask task = (AbstractNativeSourceCompileTask) abstractTask;

        task.setDescription("Compiles the " + sourceSet + " of " + binary);

        task.source(sourceSet.getSource());

        final Project project = task.getProject();
        task.setObjectFileDir(new File(binary.getNamingScheme().getOutputDirectory(project.getBuildDir(), "objs"), sourceSet.getProjectScopedName()));

        // If this task uses a pre-compiled header
        if (sourceSet instanceof DependentSourceSetInternal && ((DependentSourceSetInternal) sourceSet).getPreCompiledHeader() != null) {
            final DependentSourceSetInternal dependentSourceSet = (DependentSourceSetInternal)sourceSet;
            PreCompiledHeader pch = binary.getPrefixFileToPCH().get(dependentSourceSet.getPrefixHeaderFile());
            pch.setPrefixHeaderFile(dependentSourceSet.getPrefixHeaderFile());
            pch.setIncludeString(dependentSourceSet.getPreCompiledHeader());
            task.setPreCompiledHeader(pch);
        }

        binary.binaryInputs(task.getOutputs().getFiles().getAsFileTree().matching(new PatternSet().include("**/*.obj", "**/*.o")));
    }
}
