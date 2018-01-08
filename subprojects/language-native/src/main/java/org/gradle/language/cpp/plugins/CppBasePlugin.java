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

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.NonNullApi;
import org.gradle.api.Plugin;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.language.cpp.CppSharedLibrary;
import org.gradle.language.cpp.internal.DefaultCppBinary;
import org.gradle.language.cpp.tasks.CppCompile;
import org.gradle.language.nativeplatform.internal.Names;
import org.gradle.language.plugins.NativeBasePlugin;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.SystemIncludesAwarePlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.ToolType;
import org.gradle.nativeplatform.toolchain.internal.plugins.StandardToolChainsPlugin;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * A common base plugin for the C++ executable and library plugins
 *
 * @since 4.1
 */
@Incubating
@NonNullApi
public class CppBasePlugin implements Plugin<ProjectInternal> {
    @Override
    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(NativeBasePlugin.class);
        project.getPluginManager().apply(StandardToolChainsPlugin.class);

        final TaskContainerInternal tasks = project.getTasks();
        final DirectoryProperty buildDirectory = project.getLayout().getBuildDirectory();

        // Enable the use of Gradle metadata. This is a temporary opt-in switch until available by default
        project.getGradle().getExperimentalFeatures().enable();

        // Create the tasks for each C++ binary that is registered
        project.getComponents().withType(DefaultCppBinary.class, new Action<DefaultCppBinary>() {
            @Override
            public void execute(final DefaultCppBinary binary) {
                final Names names = binary.getNames();

                String language = "cpp";
                final NativePlatform currentPlatform = binary.getTargetPlatform();
                // TODO - make this lazy
                final NativeToolChainInternal toolChain = binary.getToolChain();

                Callable<List<File>> systemIncludes = new Callable<List<File>>() {
                    @Override
                    public List<File> call() {
                        PlatformToolProvider platformToolProvider = binary.getPlatformToolProvider();
                        if (platformToolProvider instanceof SystemIncludesAwarePlatformToolProvider) {
                            return ((SystemIncludesAwarePlatformToolProvider) platformToolProvider).getSystemIncludes(ToolType.CPP_COMPILER);
                        }
                        return ImmutableList.of();
                    }
                };

                CppCompile compile = tasks.create(names.getCompileTaskName(language), CppCompile.class);
                compile.includes(binary.getCompileIncludePath());
                compile.includes(systemIncludes);
                compile.source(binary.getCppSource());
                if (binary.isDebuggable()) {
                    compile.setDebuggable(true);
                }
                if (binary.isOptimized()) {
                    compile.setOptimized(true);
                }
                compile.setTargetPlatform(currentPlatform);
                compile.setToolChain(toolChain);
                compile.getObjectFileDir().set(buildDirectory.dir("obj/" + names.getDirName()));

                binary.getObjectsDir().set(compile.getObjectFileDir());
                binary.getCompileTask().set(compile);
            }
        });
        project.getComponents().withType(CppSharedLibrary.class, new Action<CppSharedLibrary>() {
            @Override
            public void execute(CppSharedLibrary library) {
                library.getCompileTask().get().setPositionIndependentCode(true);
            }
        });
    }
}
