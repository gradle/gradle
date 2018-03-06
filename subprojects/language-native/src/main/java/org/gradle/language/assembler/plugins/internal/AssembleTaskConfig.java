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
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.assembler.tasks.Assemble;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.nativeplatform.Tool;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.ToolType;
import org.gradle.platform.base.BinarySpec;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

public class AssembleTaskConfig implements SourceTransformTaskConfig {
    @Override
    public String getTaskPrefix() {
        return "assemble";
    }

    @Override
    public Class<? extends DefaultTask> getTaskType() {
        return Assemble.class;
    }

    @Override
    public void configureTask(Task task, BinarySpec binary, LanguageSourceSet sourceSet, ServiceRegistry serviceRegistry) {
        configureAssembleTask((Assemble) task, (NativeBinarySpecInternal) binary, (LanguageSourceSetInternal) sourceSet);
    }

    private void configureAssembleTask(Assemble task, final NativeBinarySpecInternal binary, final LanguageSourceSetInternal sourceSet) {
        task.setDescription("Assembles the " + sourceSet + " of " + binary);

        task.getToolChain().set(binary.getToolChain());
        task.getTargetPlatform().set(binary.getTargetPlatform());

        task.source(sourceSet.getSource());

        FileCollectionFactory fileCollectionFactory = ((ProjectInternal) task.getProject()).getServices().get(FileCollectionFactory.class);
        task.includes(fileCollectionFactory.create(new MinimalFileSet() {
            @Override
            public Set<File> getFiles() {
                PlatformToolProvider platformToolProvider = ((NativeToolChainInternal) binary.getToolChain()).select((NativePlatformInternal) binary.getTargetPlatform());
                return new LinkedHashSet<File>(platformToolProvider.getSystemLibraries(ToolType.ASSEMBLER).getIncludeDirs());
            }

            @Override
            public String getDisplayName() {
                return "System includes for " + binary.getToolChain().getDisplayName();
            }
        }));

        final Project project = task.getProject();
        task.setObjectFileDir(new File(binary.getNamingScheme().getOutputDirectory(project.getBuildDir(), "objs"), sourceSet.getProjectScopedName()));

        Tool assemblerTool = binary.getToolByName("assembler");
        task.setAssemblerArgs(assemblerTool.getArgs());

        binary.binaryInputs(task.getOutputs().getFiles().getAsFileTree().matching(new PatternSet().include("**/*.obj", "**/*.o")));
    }
}
