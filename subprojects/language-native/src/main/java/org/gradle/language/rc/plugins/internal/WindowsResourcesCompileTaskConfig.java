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
package org.gradle.language.rc.plugins.internal;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileTree;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.language.rc.WindowsResourceSet;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.nativeplatform.internal.StaticLibraryBinarySpecInternal;
import org.gradle.language.PreprocessingTool;
import org.gradle.language.rc.tasks.WindowsResourceCompile;
import org.gradle.platform.base.BinarySpec;

import java.io.File;
import java.util.Set;
import java.util.concurrent.Callable;

public class WindowsResourcesCompileTaskConfig implements SourceTransformTaskConfig {
    public String getTaskPrefix() {
        return "compile";
    }

    public Class<? extends DefaultTask> getTaskType() {
        return WindowsResourceCompile.class;
    }

    public void configureTask(Task task, BinarySpec binary, LanguageSourceSet sourceSet) {
        configureResourceCompileTask((WindowsResourceCompile) task, (NativeBinarySpecInternal) binary, (WindowsResourceSet) sourceSet);
    }

    private void configureResourceCompileTask(WindowsResourceCompile task, final NativeBinarySpecInternal binary, final WindowsResourceSet sourceSet) {
        task.setDescription(String.format("Compiles resources of the %s of %s", sourceSet, binary));

        task.setToolChain(binary.getToolChain());
        task.setTargetPlatform(binary.getTargetPlatform());

        task.includes(new Callable<Set<File>>() {
            public Set<File> call() {
                return sourceSet.getExportedHeaders().getSrcDirs();
            }
        });
        task.source(sourceSet.getSource());

        final Project project = task.getProject();
        task.setOutputDir(project.file(String.valueOf(project.getBuildDir()) + "/objs/" + binary.getNamingScheme().getOutputDirectoryBase() + "/" + ((LanguageSourceSetInternal) sourceSet).getFullName()));

        PreprocessingTool rcCompiler = (PreprocessingTool) ((ExtensionAware) binary).getExtensions().getByName("rcCompiler");
        task.setMacros(rcCompiler.getMacros());
        task.setCompilerArgs(rcCompiler.getArgs());

        FileTree resourceOutputs = task.getOutputs().getFiles().getAsFileTree().matching(new PatternSet().include("**/*.res"));
        binary.getTasks().getCreateOrLink().source(resourceOutputs);
        if (binary instanceof StaticLibraryBinarySpecInternal) {
            ((StaticLibraryBinarySpecInternal) binary).additionalLinkFiles(resourceOutputs);
        }
    }

}
