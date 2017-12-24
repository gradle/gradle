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
import org.gradle.api.Task;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.language.cpp.CppBinary;
import org.gradle.language.cpp.CppExecutable;
import org.gradle.language.cpp.CppSharedLibrary;
import org.gradle.language.cpp.CppStaticLibrary;
import org.gradle.language.cpp.internal.DefaultCppBinary;
import org.gradle.language.cpp.internal.DefaultCppExecutable;
import org.gradle.language.cpp.internal.DefaultCppSharedLibrary;
import org.gradle.language.cpp.internal.DefaultCppStaticLibrary;
import org.gradle.language.cpp.tasks.CppCompile;
import org.gradle.language.nativeplatform.internal.Names;
import org.gradle.language.plugins.NativeBasePlugin;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.tasks.AbstractLinkTask;
import org.gradle.nativeplatform.tasks.CreateStaticLibrary;
import org.gradle.nativeplatform.tasks.ExtractSymbols;
import org.gradle.nativeplatform.tasks.InstallExecutable;
import org.gradle.nativeplatform.tasks.LinkExecutable;
import org.gradle.nativeplatform.tasks.LinkSharedLibrary;
import org.gradle.nativeplatform.tasks.StripSymbols;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
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
        final ProviderFactory providers = project.getProviders();

        // Enable the use of Gradle metadata. This is a temporary opt-in switch until available by default
        project.getGradle().getExperimentalFeatures().enable();

        // Create the tasks for each C++ binary that is registered
        project.getComponents().withType(CppBinary.class, new Action<CppBinary>() {
            @Override
            public void execute(final CppBinary binary) {
                final Names names = Names.of(binary.getName());

                String language = "cpp";
                final NativePlatform currentPlatform = binary.getTargetPlatform();
                // TODO - make this lazy
                final NativeToolChainInternal toolChain = ((DefaultCppBinary) binary).getToolChain();

                Callable<List<File>> systemIncludes = new Callable<List<File>>() {
                    @Override
                    public List<File> call() throws Exception {
                        PlatformToolProvider platformToolProvider = ((DefaultCppBinary) binary).getPlatformToolProvider();
                        if (platformToolProvider instanceof SystemIncludesAwarePlatformToolProvider) {
                            return ((SystemIncludesAwarePlatformToolProvider) platformToolProvider).getSystemIncludes(ToolType.CPP_COMPILER);
                        }
                        return ImmutableList.of();
                    }
                };

                CppCompile compile = tasks.create(names.getCompileTaskName(language), CppCompile.class);
                configureCompile(compile, binary, currentPlatform, toolChain, systemIncludes);
                compile.getObjectFileDir().set(buildDirectory.dir("obj/" + names.getDirName()));

                ((DefaultCppBinary)binary).getObjectsDir().set(compile.getObjectFileDir());
                ((DefaultCppBinary)binary).getCompileTask().set(compile);

                Task lifecycleTask = tasks.maybeCreate(names.getTaskName("assemble"));

                if (binary instanceof CppExecutable) {
                    DefaultCppExecutable executable = (DefaultCppExecutable) binary;
                    // Add a link task
                    LinkExecutable link = tasks.create(names.getTaskName("link"), LinkExecutable.class);
                    link.source(binary.getObjects());
                    link.lib(binary.getLinkLibraries());
                    final PlatformToolProvider toolProvider = ((DefaultCppBinary) binary).getPlatformToolProvider();
                    link.setOutputFile(buildDirectory.file(providers.provider(new Callable<String>() {
                        @Override
                        public String call() throws Exception {
                            return toolProvider.getExecutableName("exe/" + names.getDirName() + binary.getBaseName().get());
                        }
                    })));
                    link.setTargetPlatform(currentPlatform);
                    link.setToolChain(toolChain);
                    link.setDebuggable(binary.isDebuggable());

                    executable.getLinkTask().set(link);

                    if (executable.isDebuggable() && executable.isOptimized() && toolChain.requiresDebugBinaryStripping()) {
                        Provider<RegularFile> symbolLocation = buildDirectory.file(providers.provider(new Callable<String>() {
                            @Override
                            public String call() {
                                return toolProvider.getExecutableSymbolFileName("exe/" + names.getDirName() + "stripped/" + binary.getBaseName().get());
                            }
                        }));
                        Provider<RegularFile> strippedLocation = buildDirectory.file(providers.provider(new Callable<String>() {
                            @Override
                            public String call() {
                                return toolProvider.getExecutableName("exe/" + names.getDirName() + "stripped/" + binary.getBaseName().get());
                            }
                        }));
                        StripSymbols stripSymbols = extractAndStripSymbols(link, names, tasks, toolChain, currentPlatform, symbolLocation, strippedLocation, lifecycleTask);
                        executable.getExecutableFile().set(stripSymbols.getOutputFile());
                    } else {
                        executable.getExecutableFile().set(link.getBinaryFile());
                    }

                    // Add an install task
                    // TODO - should probably not add this for all executables?
                    // TODO - add stripped symbols to the installation
                    final InstallExecutable install = tasks.create(names.getTaskName("install"), InstallExecutable.class);
                    install.setPlatform(link.getTargetPlatform());
                    install.setToolChain(link.getToolChain());
                    install.getInstallDirectory().set(buildDirectory.dir("install/" + names.getDirName()));
                    install.getSourceFile().set(executable.getExecutableFile());
                    install.lib(binary.getRuntimeLibraries());

                    executable.getInstallTask().set(install);
                    executable.getInstallDirectory().set(install.getInstallDirectory());

                    lifecycleTask.dependsOn(install.getInstallDirectory());
                } else if (binary instanceof CppSharedLibrary) {
                    DefaultCppSharedLibrary library = (DefaultCppSharedLibrary) binary;

                    final PlatformToolProvider toolProvider = library.getPlatformToolProvider();

                    compile.setPositionIndependentCode(true);

                    // Add a link task
                    LinkSharedLibrary link = tasks.create(names.getTaskName("link"), LinkSharedLibrary.class);
                    link.source(binary.getObjects());
                    link.lib(binary.getLinkLibraries());
                    // TODO - need to set soname
                    Provider<RegularFile> binaryFile = buildDirectory.file(providers.provider(new Callable<String>() {
                        @Override
                        public String call() {
                            return toolProvider.getSharedLibraryName("lib/" + names.getDirName() + binary.getBaseName().get());
                        }
                    }));
                    link.getBinaryFile().set(binaryFile);
                    link.setTargetPlatform(currentPlatform);
                    link.setToolChain(toolChain);
                    link.setDebuggable(binary.isDebuggable());
                    library.getLinkTask().set(link);

                    Provider<RegularFile> linkFile = link.getBinaryFile();
                    Provider<RegularFile> runtimeFile = link.getBinaryFile();
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

                    if (library.isDebuggable() && library.isOptimized() && toolChain.requiresDebugBinaryStripping()) {
                        Provider<RegularFile> symbolLocation = buildDirectory.file(providers.provider(new Callable<String>() {
                            @Override
                            public String call() {
                                return toolProvider.getLibrarySymbolFileName("lib/" + names.getDirName() + "stripped/" + binary.getBaseName().get());
                            }
                        }));
                        Provider<RegularFile> strippedLocation = buildDirectory.file(providers.provider(new Callable<String>() {
                            @Override
                            public String call() {
                                return toolProvider.getSharedLibraryName("lib/" + names.getDirName() + "stripped/"+ binary.getBaseName().get());
                            }
                        }));
                        StripSymbols stripSymbols = extractAndStripSymbols(link, names, tasks, toolChain, currentPlatform, symbolLocation, strippedLocation, lifecycleTask);
                        linkFile = stripSymbols.getOutputFile();
                        runtimeFile = stripSymbols.getOutputFile();
                    }

                    library.getLinkFile().set(linkFile);
                    library.getRuntimeFile().set(runtimeFile);
                    lifecycleTask.dependsOn(library.getRuntimeFile());
                } else if (binary instanceof CppStaticLibrary) {
                    DefaultCppStaticLibrary library = (DefaultCppStaticLibrary) binary;

                    final PlatformToolProvider toolProvider = library.getPlatformToolProvider();

                    // Add a link task
                    final CreateStaticLibrary link = tasks.create(names.getTaskName("create"), CreateStaticLibrary.class);
                    link.source(binary.getObjects());
                    Provider<RegularFile> runtimeFile = buildDirectory.file(providers.provider(new Callable<String>() {
                        @Override
                        public String call() {
                            return toolProvider.getStaticLibraryName("lib/" + names.getDirName() + binary.getBaseName().get());
                        }
                    }));
                    link.setOutputFile(runtimeFile);
                    link.setTargetPlatform(currentPlatform);
                    link.setToolChain(toolChain);

                    library.getLinkFile().set(link.getBinaryFile());
                    library.getCreateTask().set(link);
                    lifecycleTask.dependsOn(library.getLinkFile());
                }
            }

            private void configureCompile(CppCompile compile, CppBinary binary, NativePlatform currentPlatform, NativeToolChain toolChain, Callable<List<File>> systemIncludes) {
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
            }

            private StripSymbols extractAndStripSymbols(AbstractLinkTask link, Names names, TaskContainer tasks, NativeToolChain toolChain, NativePlatform currentPlatform, Provider<RegularFile> symbolLocation, Provider<RegularFile> strippedLocation, Task lifecycleTask) {
                ExtractSymbols extractSymbols = tasks.create(names.getTaskName("extractSymbols"), ExtractSymbols.class);
                extractSymbols.getBinaryFile().set(link.getBinaryFile());
                extractSymbols.getSymbolFile().set(symbolLocation);
                extractSymbols.setTargetPlatform(currentPlatform);
                extractSymbols.setToolChain(toolChain);
                lifecycleTask.dependsOn(extractSymbols);

                StripSymbols stripSymbols = tasks.create(names.getTaskName("stripSymbols"), StripSymbols.class);
                stripSymbols.getBinaryFile().set(link.getBinaryFile());
                stripSymbols.getOutputFile().set(strippedLocation);
                stripSymbols.setTargetPlatform(currentPlatform);
                stripSymbols.setToolChain(toolChain);
                lifecycleTask.dependsOn(stripSymbols);

                return stripSymbols;
            }
        });
    }
}
