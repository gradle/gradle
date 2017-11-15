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
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.cpp.CppBinary;
import org.gradle.language.cpp.CppExecutable;
import org.gradle.language.cpp.CppSharedLibrary;
import org.gradle.language.cpp.internal.DefaultCppBinary;
import org.gradle.language.cpp.internal.DefaultCppExecutable;
import org.gradle.language.cpp.internal.DefaultCppSharedLibrary;
import org.gradle.language.cpp.tasks.CppCompile;
import org.gradle.language.nativeplatform.internal.Names;
import org.gradle.language.nativeplatform.tasks.Depend;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.nativeplatform.tasks.InstallExecutable;
import org.gradle.nativeplatform.tasks.LinkExecutable;
import org.gradle.nativeplatform.tasks.LinkSharedLibrary;
import org.gradle.nativeplatform.toolchain.Clang;
import org.gradle.nativeplatform.toolchain.Gcc;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.SystemIncludesAwarePlatformToolProvider;
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
        project.getPluginManager().apply(LifecycleBasePlugin.class);
        project.getPluginManager().apply(StandardToolChainsPlugin.class);

        final TaskContainerInternal tasks = project.getTasks();
        final DirectoryProperty buildDirectory = project.getLayout().getBuildDirectory();
        final ModelRegistry modelRegistry = project.getModelRegistry();
        final ProviderFactory providers = project.getProviders();

        // Enable the use of Gradle metadata. This is a temporary opt-in switch until available by default
        project.getGradle().getExperimentalFeatures().enable();

        // Create the tasks for each C++ binary that is registered
        project.getComponents().withType(CppBinary.class, new Action<CppBinary>() {
            @Override
            public void execute(final CppBinary binary) {
                final Names names = Names.of(binary.getName());

                String language = "cpp";
                final DefaultNativePlatform currentPlatform = new DefaultNativePlatform("current");
                // TODO - make this lazy
                final NativeToolChain toolChain = modelRegistry.realize("toolChains", NativeToolChainRegistryInternal.class).getForPlatform(currentPlatform);

                Callable<List<File>> systemIncludes = new Callable<List<File>>() {
                    @Override
                    public List<File> call() throws Exception {
                        PlatformToolProvider platformToolProvider = ((NativeToolChainInternal) toolChain).select(currentPlatform);
                        if (platformToolProvider instanceof SystemIncludesAwarePlatformToolProvider) {
                            return ((SystemIncludesAwarePlatformToolProvider) platformToolProvider).getSystemIncludes();
                        }
                        return ImmutableList.of();
                    }
                };

                CppCompile compile = tasks.create(names.getCompileTaskName(language), CppCompile.class);
                configureCompile(compile, binary, currentPlatform, toolChain, systemIncludes);
                compile.getObjectFileDir().set(buildDirectory.dir("obj/" + names.getDirName()));

                ((DefaultCppBinary)binary).getObjectsDir().set(compile.getObjectFileDir());

                Depend depend = tasks.create(names.getDependTaskName(language), Depend.class);
                configureDepend(depend, binary, toolChain, systemIncludes);
                compile.getHeaderDependenciesFile().set(depend.getHeaderDependenciesFile());

                if (binary instanceof CppExecutable) {
                    // Add a link task
                    LinkExecutable link = tasks.create(names.getTaskName("link"), LinkExecutable.class);
                    link.source(binary.getObjects());
                    link.lib(binary.getLinkLibraries());
                    final PlatformToolProvider toolProvider = ((NativeToolChainInternal) toolChain).select(currentPlatform);
                    link.setOutputFile(buildDirectory.file(providers.provider(new Callable<String>() {
                        @Override
                        public String call() throws Exception {
                            return toolProvider.getExecutableName("exe/" + names.getDirName() + binary.getBaseName().get());
                        }
                    })));
                    link.setTargetPlatform(currentPlatform);
                    link.setToolChain(toolChain);
                    link.setDebuggable(binary.isDebuggable());

                    // Add an install task
                    // TODO - should probably not add this for all executables?
                    final InstallExecutable install = tasks.create(names.getTaskName("install"), InstallExecutable.class);
                    install.setPlatform(link.getTargetPlatform());
                    install.setToolChain(link.getToolChain());
                    install.getInstallDirectory().set(buildDirectory.dir("install/" + names.getDirName()));
                    install.getSourceFile().set(link.getBinaryFile());
                    install.lib(binary.getRuntimeLibraries());

                    ((DefaultCppExecutable) binary).getExecutableFile().set(link.getBinaryFile());
                    ((DefaultCppExecutable) binary).getInstallDirectory().set(install.getInstallDirectory());
                } else if (binary instanceof CppSharedLibrary) {
                    final PlatformToolProvider toolProvider = ((NativeToolChainInternal) toolChain).select(currentPlatform);

                    compile.setPositionIndependentCode(true);

                    // Add a link task
                    LinkSharedLibrary link = tasks.create(names.getTaskName("link"), LinkSharedLibrary.class);
                    link.source(binary.getObjects());
                    link.lib(binary.getLinkLibraries());
                    // TODO - need to set soname
                    Provider<RegularFile> runtimeFile = buildDirectory.file(providers.provider(new Callable<String>() {
                        @Override
                        public String call() {
                            return toolProvider.getSharedLibraryName("lib/" + names.getDirName() + binary.getBaseName().get());
                        }
                    }));
                    link.getBinaryFile().set(runtimeFile);
                    link.setTargetPlatform(currentPlatform);
                    link.setToolChain(toolChain);
                    link.setDebuggable(binary.isDebuggable());

                    Provider<RegularFile> linkFile = link.getBinaryFile();
                    if (toolProvider.producesImportLibrary()) {
                        Provider<RegularFile> importLibrary = buildDirectory.file(providers.provider(new Callable<String>() {
                            @Override
                            public String call() {
                                return toolProvider.getImportLibraryName("lib/" + names.getDirName() + binary.getBaseName().get());
                            }
                        }));
                        link.getImportLibrary().set(importLibrary);
                        linkFile = link.getImportLibrary();
                    }

                    ((DefaultCppSharedLibrary) binary).getRuntimeFile().set(link.getBinaryFile());
                    ((DefaultCppSharedLibrary) binary).getLinkFile().set(linkFile);
                }
            }

            private void configureCompile(CppCompile compile, CppBinary binary, DefaultNativePlatform currentPlatform, NativeToolChain toolChain, Callable<List<File>> systemIncludes) {
                compile.includes(binary.getCompileIncludePath());
                compile.includes(systemIncludes);
                compile.source(binary.getCppSource());
                if (binary.isDebuggable()) {
                    compile.setDebuggable(true);
                } else {
                    compile.setOptimized(true);
                }
                compile.setTargetPlatform(currentPlatform);
                compile.setToolChain(toolChain);
            }

            private void configureDepend(Depend depend, CppBinary binary, NativeToolChain toolChain, Callable<List<File>> systemIncludesProvider) {
                depend.includes(binary.getCompileIncludePath());
                depend.includes(systemIncludesProvider);
                depend.source(binary.getCppSource());
                depend.getHeaderDependenciesFile().set(project.getLayout().getBuildDirectory().file(depend.getName() + "/" + "inputs.txt"));
                depend.getImportsAreIncludes().set(Clang.class.isAssignableFrom(toolChain.getClass()) || Gcc.class.isAssignableFrom(toolChain.getClass()));
            }
        });
    }

}
