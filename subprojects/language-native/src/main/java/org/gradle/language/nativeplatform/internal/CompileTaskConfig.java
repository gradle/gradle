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
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.file.FileCollection;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.language.base.internal.registry.LanguageTransform;
import org.gradle.language.nativeplatform.DependentSourceSet;
import org.gradle.language.nativeplatform.HeaderExportingSourceSet;
import org.gradle.language.nativeplatform.tasks.AbstractNativeCompileTask;
import org.gradle.nativeplatform.NativeDependencySet;
import org.gradle.nativeplatform.ObjectFile;
import org.gradle.nativeplatform.PreprocessingTool;
import org.gradle.nativeplatform.SharedLibraryBinarySpec;
import org.gradle.nativeplatform.Tool;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.platform.base.BinarySpec;
import org.gradle.util.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

public abstract class CompileTaskConfig implements SourceTransformTaskConfig {

    private final LanguageTransform<? extends LanguageSourceSet, ObjectFile> languageTransform;
    private final Class<? extends DefaultTask> taskType;

    public CompileTaskConfig(LanguageTransform<? extends LanguageSourceSet, ObjectFile> languageTransform, Class<? extends DefaultTask> taskType) {
        this.languageTransform = languageTransform;
        this.taskType = taskType;
    }

    @Override
    public String getTaskPrefix() {
        return "compile";
    }

    @Override
    public Class<? extends DefaultTask> getTaskType() {
        return taskType;
    }

    @Override
    public void configureTask(Task task, BinarySpec binary, LanguageSourceSet sourceSet, ServiceRegistry serviceRegistry) {
        configureCompileTaskCommon((AbstractNativeCompileTask) task, (NativeBinarySpecInternal) binary, (LanguageSourceSetInternal) sourceSet);
        configureCompileTask((AbstractNativeCompileTask) task, (NativeBinarySpecInternal) binary, (LanguageSourceSetInternal) sourceSet);
    }

    private void configureCompileTaskCommon(AbstractNativeCompileTask task, final NativeBinarySpecInternal binary, final LanguageSourceSetInternal sourceSet) {
        task.setToolChain(binary.getToolChain());
        task.setTargetPlatform(binary.getTargetPlatform());
        task.setPositionIndependentCode(binary instanceof SharedLibraryBinarySpec);

        task.includes(((HeaderExportingSourceSet) sourceSet).getExportedHeaders().getSourceDirectories());
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

        for (String toolName : languageTransform.getBinaryTools().keySet()) {
            Tool tool = binary.getToolByName(toolName);
            if (tool instanceof PreprocessingTool) {
                task.setMacros(((PreprocessingTool) tool).getMacros());
            }

            task.setCompilerArgs(tool.getArgs());
        }
    }

    abstract void configureCompileTask(AbstractNativeCompileTask task, final NativeBinarySpecInternal binary, final LanguageSourceSetInternal sourceSet);
}
