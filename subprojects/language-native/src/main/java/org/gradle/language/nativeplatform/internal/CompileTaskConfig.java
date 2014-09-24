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
package org.gradle.language.nativeplatform.internal;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.language.nativeplatform.tasks.AbstractNativeCompileTask;
import org.gradle.language.nativeplatform.DependentSourceSet;
import org.gradle.language.nativeplatform.HeaderExportingSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.LanguageRegistration;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.nativeplatform.NativeDependencySet;
import org.gradle.nativeplatform.SharedLibraryBinarySpec;
import org.gradle.nativeplatform.Tool;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.language.PreprocessingTool;
import org.gradle.platform.base.BinarySpec;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

public class CompileTaskConfig implements SourceTransformTaskConfig {
    public CompileTaskConfig(LanguageRegistration<? extends LanguageSourceSet> languageRegistration, Class<? extends DefaultTask> taskType) {
        this.language = languageRegistration;
        this.taskType = taskType;
    }

    public String getTaskPrefix() {
        return "compile";
    }

    public Class<? extends DefaultTask> getTaskType() {
        return taskType;
    }

    public void configureTask(Task task, BinarySpec binary, LanguageSourceSet sourceSet) {
        configureCompileTask((AbstractNativeCompileTask) task, (NativeBinarySpecInternal) binary, (LanguageSourceSetInternal) sourceSet);
    }

    private void configureCompileTask(AbstractNativeCompileTask task, final NativeBinarySpecInternal binary, final LanguageSourceSetInternal sourceSet) {
        task.setDescription(String.format("Compiles the %s of %s", sourceSet, binary));

        task.setToolChain(binary.getToolChain());
        task.setTargetPlatform(binary.getTargetPlatform());
        task.setPositionIndependentCode(binary instanceof SharedLibraryBinarySpec);

        // TODO:DAZ Not sure if these both need to be lazy
        task.includes(new Callable<Set<File>>() {
            public Set<File> call() throws Exception {
                return ((HeaderExportingSourceSet) sourceSet).getExportedHeaders().getSrcDirs();
            }
        });
        task.includes(new Callable<List<FileCollection>>() {
            public List<FileCollection> call() {
                Collection<NativeDependencySet> libs = binary.getLibs((DependentSourceSet) sourceSet);
                return CollectionUtils.collect(libs, new Transformer<FileCollection, NativeDependencySet>() {
                    public FileCollection transform(NativeDependencySet original) {
                        return original.getIncludeRoots();
                    }
                });
            }
        });

        task.source(sourceSet.getSource());

        final Project project = task.getProject();
        task.setObjectFileDir(project.file(String.valueOf(project.getBuildDir()) + "/objs/" + binary.getNamingScheme().getOutputDirectoryBase() + "/" + sourceSet.getFullName()));

        for (String toolName : language.getBinaryTools().keySet()) {
            Tool tool = (Tool) ((ExtensionAware) binary).getExtensions().getByName(toolName);
            if (tool instanceof PreprocessingTool) {
                task.setMacros(((PreprocessingTool) tool).getMacros());
            }

            task.setCompilerArgs(tool.getArgs());
        }


        binary.getTasks().getCreateOrLink().source(task.getOutputs().getFiles().getAsFileTree().matching(new PatternSet().include("**/*.obj", "**/*.o")));
    }

    private final LanguageRegistration<? extends LanguageSourceSet> language;
    private final Class<? extends DefaultTask> taskType;
}
