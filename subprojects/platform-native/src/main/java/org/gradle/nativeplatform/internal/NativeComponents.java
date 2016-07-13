/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.nativeplatform.internal;

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.resolve.ProjectModelResolver;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelTypes;
import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.NativeDependencySet;
import org.gradle.nativeplatform.NativeExecutableFileSpec;
import org.gradle.nativeplatform.NativeInstallationSpec;
import org.gradle.nativeplatform.tasks.InstallExecutable;
import org.gradle.nativeplatform.tasks.LinkExecutable;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.internal.BinaryNamingScheme;
import org.gradle.platform.base.internal.BinarySpecInternal;
import org.gradle.platform.base.internal.dependents.DependentBinariesResolvedResult;
import org.gradle.platform.base.internal.dependents.DependentBinariesResolver;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

public class NativeComponents {
    public static void createExecutableTask(final NativeBinarySpecInternal binary, final File executableFile) {
        String taskName = binary.getNamingScheme().getTaskName("link");
        binary.getTasks().create(taskName, LinkExecutable.class, new Action<LinkExecutable>() {
            @Override
            public void execute(LinkExecutable linkTask) {
                linkTask.setDescription("Links " + binary.getDisplayName());
                linkTask.setToolChain(binary.getToolChain());
                linkTask.setTargetPlatform(binary.getTargetPlatform());
                linkTask.setOutputFile(executableFile);
                linkTask.setLinkerArgs(binary.getLinker().getArgs());

                linkTask.lib(new BinaryLibs(binary) {
                    @Override
                    protected FileCollection getFiles(NativeDependencySet nativeDependencySet) {
                        return nativeDependencySet.getLinkFiles();
                    }
                });
                binary.builtBy(linkTask);
            }
        });
    }

    public static void createInstallTask(final NativeBinarySpecInternal binary, final NativeInstallationSpec installation, final NativeExecutableFileSpec executable, final BinaryNamingScheme namingScheme) {
        binary.getTasks().create(namingScheme.getTaskName("install"), InstallExecutable.class, new Action<InstallExecutable>() {
            @Override
            public void execute(InstallExecutable installTask) {
                installTask.setDescription("Installs a development image of " + binary.getDisplayName());
                installTask.setGroup(LifecycleBasePlugin.BUILD_GROUP);
                installTask.setToolChain(executable.getToolChain());
                installTask.setPlatform(binary.getTargetPlatform());
                installTask.setExecutable(executable.getFile());
                installTask.setDestinationDir(installation.getDirectory());
                //TODO:HH wire binary libs via executable
                installTask.lib(new BinaryLibs(binary) {
                    @Override
                    protected FileCollection getFiles(NativeDependencySet nativeDependencySet) {
                        return nativeDependencySet.getRuntimeFiles();
                    }
                });

                //TODO:HH installTask.dependsOn(executable)
                installTask.dependsOn(binary);
            }
        });
    }

    public static void createBuildDependentBinariesTasks(final NativeBinarySpecInternal binary, BinaryNamingScheme namingScheme) {
        binary.getTasks().create(namingScheme.getTaskName("assembleDependents"), DefaultTask.class, new Action<Task>() {
            @Override
            public void execute(Task task) {
                task.setGroup("Build Dependents");
                task.setDescription("Assemble dependents of " + binary.getDisplayName());
                task.dependsOn(binary);
            }
        });
    }

    public static void wireBuildDependentTasks(ModelMap<Task> tasks, BinaryContainer binaries, final DependentBinariesResolver dependentsResolver, final ProjectModelResolver projectModelResolver) {
        final ModelMap<NativeBinarySpecInternal> nativeBinaries = binaries.withType(NativeBinarySpecInternal.class);
        for (final BinarySpecInternal binary : nativeBinaries) {
            Task assembleDependents = tasks.get(binary.getNamingScheme().getTaskName("assembleDependents"));
            // Wire dependent binaries tasks dependencies
            // Defer dependencies gathering as we need to resolve across project's boundaries
            Callable<Iterable<BinarySpec>> deferredDependencies = new Callable<Iterable<BinarySpec>>() {
                @Override
                public Iterable<BinarySpec> call() {
                    List<BinarySpec> dependencies = Lists.newArrayList();
                    DependentBinariesResolvedResult result = dependentsResolver.resolve(binary).getRoot();
                    for (DependentBinariesResolvedResult dependent : result.getChildren()) {
                        if (dependent.isBuildable()) {
                            ModelRegistry modelRegistry = projectModelResolver.resolveProjectModel(dependent.getId().getProjectPath());
                            ModelMap<NativeBinarySpecInternal> projectBinaries = modelRegistry.realize("binaries", ModelTypes.modelMap(NativeBinarySpecInternal.class));
                            NativeBinarySpecInternal dependentBinary = projectBinaries.get(dependent.getProjectScopedName());
                            dependencies.add(dependentBinary);
                        }
                    }
                    return dependencies;
                }
            };
            assembleDependents.dependsOn(deferredDependencies);
        }
    }

    public abstract static class BinaryLibs implements Callable<List<FileCollection>> {
        private final NativeBinarySpec binary;

        public BinaryLibs(NativeBinarySpec binary) {
            this.binary = binary;
        }

        @Override
        public List<FileCollection> call() throws Exception {
            List<FileCollection> runtimeFiles = Lists.newArrayList();
            for (NativeDependencySet nativeDependencySet : binary.getLibs()) {
                runtimeFiles.add(getFiles(nativeDependencySet));
            }
            return runtimeFiles;
        }

        protected abstract FileCollection getFiles(NativeDependencySet nativeDependencySet);
    }
}
