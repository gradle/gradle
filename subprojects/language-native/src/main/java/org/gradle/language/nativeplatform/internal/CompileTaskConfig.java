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
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.language.nativeplatform.DependentSourceSet;
import org.gradle.language.nativeplatform.HeaderExportingSourceSet;
import org.gradle.language.nativeplatform.tasks.AbstractNativeCompileTask;
import org.gradle.nativeplatform.NativeDependencySet;
import org.gradle.nativeplatform.PreprocessingTool;
import org.gradle.nativeplatform.SharedLibraryBinarySpec;
import org.gradle.nativeplatform.Tool;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.ToolType;
import org.gradle.platform.base.BinarySpec;
import org.gradle.util.internal.CollectionUtils;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

public abstract class CompileTaskConfig implements SourceTransformTaskConfig {

    private final NativeLanguageTransform<?> languageTransform;
    private final Class<? extends DefaultTask> taskType;

    public CompileTaskConfig(NativeLanguageTransform<?> languageTransform, Class<? extends DefaultTask> taskType) {
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

    private void configureCompileTaskCommon(final AbstractNativeCompileTask task, final NativeBinarySpecInternal binary, final LanguageSourceSetInternal sourceSet) {
        task.getToolChain().set(binary.getToolChain());
        task.getTargetPlatform().set(binary.getTargetPlatform());
        task.setPositionIndependentCode(binary instanceof SharedLibraryBinarySpec);

        task.includes(((HeaderExportingSourceSet) sourceSet).getExportedHeaders().getSourceDirectories());
        task.includes(new Callable<List<FileCollection>>() {
            @Override
            public List<FileCollection> call() {
                Collection<NativeDependencySet> libs = binary.getLibs((DependentSourceSet) sourceSet);
                return CollectionUtils.collect(libs, NativeDependencySet::getIncludeRoots);
            }
        });
        FileCollectionFactory fileCollectionFactory = ((ProjectInternal) task.getProject()).getServices().get(FileCollectionFactory.class);
        task.getSystemIncludes().from(fileCollectionFactory.create(new MinimalFileSet() {
            @Override
            public Set<File> getFiles() {
                PlatformToolProvider platformToolProvider = ((NativeToolChainInternal) binary.getToolChain()).select((NativePlatformInternal) binary.getTargetPlatform());
                ToolType toolType = languageTransform.getToolType();
                return new LinkedHashSet<File>(platformToolProvider.getSystemLibraries(toolType).getIncludeDirs());
            }

            @Override
            public String getDisplayName() {
                return "System includes for " + binary.getToolChain().getDisplayName();
            }
        }));

        for (String toolName : languageTransform.getBinaryTools().keySet()) {
            Tool tool = binary.getToolByName(toolName);
            if (tool instanceof PreprocessingTool) {
                task.setMacros(((PreprocessingTool) tool).getMacros());
            }

            task.getCompilerArgs().set(tool.getArgs());
        }
    }

    abstract void configureCompileTask(AbstractNativeCompileTask task, final NativeBinarySpecInternal binary, final LanguageSourceSetInternal sourceSet);
}
