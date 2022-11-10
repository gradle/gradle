/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.cpp.plugins;

import org.gradle.api.NonNullApi;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.cpp.CppSharedLibrary;
import org.gradle.language.cpp.ProductionCppComponent;
import org.gradle.language.cpp.internal.DefaultCppBinary;
import org.gradle.language.cpp.internal.DefaultCppComponent;
import org.gradle.language.cpp.tasks.CppCompile;
import org.gradle.language.nativeplatform.internal.Names;
import org.gradle.language.plugins.NativeBasePlugin;
import org.gradle.nativeplatform.toolchain.internal.ToolType;
import org.gradle.nativeplatform.toolchain.internal.plugins.StandardToolChainsPlugin;
import org.gradle.swiftpm.internal.NativeProjectPublication;
import org.gradle.swiftpm.internal.SwiftPmTarget;

import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * A common base plugin for the C++ executable and library plugins
 *
 * @since 4.1
 */
@NonNullApi
public abstract class CppBasePlugin implements Plugin<Project> {
    private final ProjectPublicationRegistry publicationRegistry;

    @Inject
    public CppBasePlugin(ProjectPublicationRegistry publicationRegistry) {
        this.publicationRegistry = publicationRegistry;
    }

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(NativeBasePlugin.class);
        project.getPluginManager().apply(StandardToolChainsPlugin.class);

        final TaskContainer tasks = project.getTasks();
        final DirectoryProperty buildDirectory = project.getLayout().getBuildDirectory();

        // Create the tasks for each C++ binary that is registered
        project.getComponents().withType(DefaultCppBinary.class, binary -> {
            final Names names = binary.getNames();
            String language = "cpp";

            TaskProvider<CppCompile> compile = tasks.register(names.getCompileTaskName(language), CppCompile.class, task -> {
                final Callable<List<File>> systemIncludes = () -> binary.getPlatformToolProvider().getSystemLibraries(ToolType.CPP_COMPILER).getIncludeDirs();

                task.includes(binary.getCompileIncludePath());
                task.getSystemIncludes().from(systemIncludes);
                task.source(binary.getCppSource());
                if (binary.isDebuggable()) {
                    task.setDebuggable(true);
                }
                if (binary.isOptimized()) {
                    task.setOptimized(true);
                }
                task.getTargetPlatform().set(binary.getNativePlatform());
                task.getToolChain().set(binary.getToolChain());
                task.getObjectFileDir().set(buildDirectory.dir("obj/" + names.getDirName()));

                if (binary instanceof CppSharedLibrary) {
                    task.setPositionIndependentCode(true);
                }
            });

            binary.getObjectsDir().set(compile.flatMap(task -> task.getObjectFileDir()));
            binary.getCompileTask().set(compile);
        });

        project.getComponents().withType(ProductionCppComponent.class, component -> {
            project.afterEvaluate(p -> {
                DefaultCppComponent componentInternal = (DefaultCppComponent) component;
                publicationRegistry.registerPublication((ProjectInternal) project, new NativeProjectPublication(componentInternal.getDisplayName(), new SwiftPmTarget(component.getBaseName().get())));
            });
        });
    }
}
