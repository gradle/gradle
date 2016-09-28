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
import org.apache.commons.lang.StringUtils;
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
import org.gradle.nativeplatform.NativeComponentSpec;
import org.gradle.nativeplatform.NativeDependencySet;
import org.gradle.nativeplatform.NativeExecutableFileSpec;
import org.gradle.nativeplatform.NativeInstallationSpec;
import org.gradle.nativeplatform.tasks.InstallExecutable;
import org.gradle.nativeplatform.tasks.LinkExecutable;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.ComponentSpecContainer;
import org.gradle.platform.base.VariantComponentSpec;
import org.gradle.platform.base.internal.BinaryNamingScheme;
import org.gradle.platform.base.internal.BinarySpecInternal;
import org.gradle.platform.base.internal.dependents.DependentBinariesResolvedResult;
import org.gradle.platform.base.internal.dependents.DependentBinariesResolver;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

public class NativeComponents {

    private static final String ASSEMBLE_DEPENDENTS_TASK_NAME = "assembleDependents";
    private static final String BUILD_DEPENDENTS_TASK_NAME = "buildDependents";

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

    public static void createBuildDependentComponentsTasks(ModelMap<Task> tasks, ComponentSpecContainer components) {
        for (final VariantComponentSpec component : components.withType(NativeComponentSpec.class).withType(VariantComponentSpec.class)) {
            tasks.create(getAssembleDependentComponentsTaskName(component), DefaultTask.class, new Action<DefaultTask>() {
                @Override
                public void execute(DefaultTask assembleDependents) {
                    assembleDependents.setGroup("Build Dependents");
                    assembleDependents.setDescription("Assemble dependents of " + component.getDisplayName() + ".");
                }
            });
            tasks.create(getBuildDependentComponentsTaskName(component), DefaultTask.class, new Action<DefaultTask>() {
                @Override
                public void execute(DefaultTask buildDependents) {
                    buildDependents.setGroup("Build Dependents");
                    buildDependents.setDescription("Build dependents of " + component.getDisplayName() + ".");
                }
            });
        }
    }

    public static void createBuildDependentBinariesTasks(final NativeBinarySpecInternal binary, BinaryNamingScheme namingScheme) {
        binary.getTasks().create(namingScheme.getTaskName(ASSEMBLE_DEPENDENTS_TASK_NAME), DefaultTask.class, new Action<DefaultTask>() {
            @Override
            public void execute(DefaultTask buildDependentsTask) {
                buildDependentsTask.setGroup("Build Dependents");
                buildDependentsTask.setDescription("Assemble dependents of " + binary.getDisplayName() + ".");
                buildDependentsTask.dependsOn(binary);
            }
        });
        binary.getTasks().create(namingScheme.getTaskName(BUILD_DEPENDENTS_TASK_NAME), DefaultTask.class, new Action<DefaultTask>() {
            @Override
            public void execute(DefaultTask buildDependentsTask) {
                buildDependentsTask.setGroup("Build Dependents");
                buildDependentsTask.setDescription("Build dependents of " + binary.getDisplayName() + ".");
                buildDependentsTask.dependsOn(binary);
            }
        });
    }

    public static void wireBuildDependentTasks(final ModelMap<Task> tasks, BinaryContainer binaries, final DependentBinariesResolver dependentsResolver, final ProjectModelResolver projectModelResolver) {
        final ModelMap<NativeBinarySpecInternal> nativeBinaries = binaries.withType(NativeBinarySpecInternal.class);
        for (final BinarySpecInternal binary : nativeBinaries) {
            Task assembleDependents = tasks.get(binary.getNamingScheme().getTaskName(ASSEMBLE_DEPENDENTS_TASK_NAME));
            Task buildDependents = tasks.get(binary.getNamingScheme().getTaskName(BUILD_DEPENDENTS_TASK_NAME));
            // Wire build dependent components tasks dependencies
            Task assembleDependentComponents = tasks.get(getAssembleDependentComponentsTaskName(binary.getComponent()));
            if (assembleDependentComponents != null) {
                assembleDependentComponents.dependsOn(assembleDependents);
            }
            Task buildDependentComponents = tasks.get(getBuildDependentComponentsTaskName(binary.getComponent()));
            if (buildDependentComponents != null) {
                buildDependentComponents.dependsOn(buildDependents);
            }
            // Wire build dependent binaries tasks dependencies
            // Defer dependencies gathering as we need to resolve across project's boundaries
            assembleDependents.dependsOn(new Callable<Iterable<Task>>() {
                @Override
                public Iterable<Task> call() {
                    return getDependentTaskDependencies(ASSEMBLE_DEPENDENTS_TASK_NAME, binary, dependentsResolver, projectModelResolver);
                }
            });
            buildDependents.dependsOn(new Callable<Iterable<Task>>() {
                @Override
                public Iterable<Task> call() {
                    return getDependentTaskDependencies(BUILD_DEPENDENTS_TASK_NAME, binary, dependentsResolver, projectModelResolver);
                }
            });
        }
    }

    private static List<Task> getDependentTaskDependencies(String dependedOnBinaryTaskName, BinarySpecInternal binary, DependentBinariesResolver dependentsResolver, ProjectModelResolver projectModelResolver) {
        List<Task> dependencies = Lists.newArrayList();
        DependentBinariesResolvedResult result = dependentsResolver.resolve(binary).getRoot();
        for (DependentBinariesResolvedResult dependent : result.getChildren()) {
            if (dependent.isBuildable()) {
                ModelRegistry modelRegistry = projectModelResolver.resolveProjectModel(dependent.getId().getProjectPath());
                ModelMap<NativeBinarySpecInternal> projectBinaries = modelRegistry.realize("binaries", ModelTypes.modelMap(NativeBinarySpecInternal.class));
                ModelMap<Task> projectTasks = modelRegistry.realize("tasks", ModelTypes.modelMap(Task.class));
                NativeBinarySpecInternal dependentBinary = projectBinaries.get(dependent.getProjectScopedName());
                dependencies.add(projectTasks.get(dependentBinary.getNamingScheme().getTaskName(dependedOnBinaryTaskName)));
            }
        }
        return dependencies;
    }

    private static String getAssembleDependentComponentsTaskName(ComponentSpec component) {
        return ASSEMBLE_DEPENDENTS_TASK_NAME + StringUtils.capitalize(component.getName());
    }

    private static String getBuildDependentComponentsTaskName(ComponentSpec component) {
        return BUILD_DEPENDENTS_TASK_NAME + StringUtils.capitalize(component.getName());
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
