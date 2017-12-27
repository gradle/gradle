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

package org.gradle.language.plugins;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.component.SoftwareComponentContainer;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.language.ComponentWithBinaries;
import org.gradle.language.ComponentWithOutputs;
import org.gradle.language.ProductionComponent;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithExecutable;
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithSharedLibrary;
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithStaticLibrary;
import org.gradle.language.nativeplatform.internal.Names;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.tasks.AbstractLinkTask;
import org.gradle.nativeplatform.tasks.CreateStaticLibrary;
import org.gradle.nativeplatform.tasks.ExtractSymbols;
import org.gradle.nativeplatform.tasks.InstallExecutable;
import org.gradle.nativeplatform.tasks.LinkExecutable;
import org.gradle.nativeplatform.tasks.LinkSharedLibrary;
import org.gradle.nativeplatform.tasks.StripSymbols;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;

import java.util.concurrent.Callable;

/**
 * A common base plugin for the native plugins.
 *
 * <p>Expects plugins to register the native components in the {@link Project#getComponents()} container, and defines a number of rules that act on these components to configure them.</p>
 *
 * <ul>
 *
 * <li>Configures the {@value LifecycleBasePlugin#ASSEMBLE_TASK_NAME} task to build the development binary of the main component, if present. Expects the main component to be of type {@link ProductionComponent}.</li>
 *
 * <li>Adds an {@code "assemble"} task for each binary of the main component.</li>
 *
 * </ul>
 *
 * @since 4.5
 */
@Incubating
public class NativeBasePlugin implements Plugin<ProjectInternal> {
    @Override
    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(LifecycleBasePlugin.class);
        final TaskContainer tasks = project.getTasks();
        final ProviderFactory providers = project.getProviders();
        final DirectoryProperty buildDirectory = project.getLayout().getBuildDirectory();

        final SoftwareComponentContainer components = project.getComponents();
        components.withType(ComponentWithBinaries.class, new Action<ComponentWithBinaries>() {
            @Override
            public void execute(final ComponentWithBinaries component) {
                // Register each child of each component
                component.getBinaries().whenElementKnown(new Action<SoftwareComponent>() {
                    @Override
                    public void execute(SoftwareComponent binary) {
                        components.add(binary);
                    }
                });
                if (component instanceof ProductionComponent) {
                    // Add an assemble task for each binary and also wire the development binary in to the `assemble` task
                    component.getBinaries().whenElementFinalized(ComponentWithOutputs.class, new Action<ComponentWithOutputs>() {
                        @Override
                        public void execute(ComponentWithOutputs binary) {
                            // Determine which output to produce at development time.
                            FileCollection outputs = binary.getOutputs();
                            Task lifecycleTask = tasks.create(Names.of(binary.getName()).getTaskName("assemble"));
                            lifecycleTask.dependsOn(outputs);
                            if (binary == ((ProductionComponent) component).getDevelopmentBinary().get()) {
                                tasks.getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).dependsOn(outputs);
                            }
                        }
                    });
                }
            }
        });
        components.withType(ConfigurableComponentWithExecutable.class, new Action<ConfigurableComponentWithExecutable>() {
            @Override
            public void execute(final ConfigurableComponentWithExecutable executable) {
                final Names names = Names.of(executable.getName());
                NativeToolChain toolChain = executable.getToolChain();
                NativePlatform targetPlatform = executable.getTargetPlatform();

                // Add a link task
                LinkExecutable link = tasks.create(names.getTaskName("link"), LinkExecutable.class);
                link.source(executable.getObjects());
                link.lib(executable.getLinkLibraries());
                final PlatformToolProvider toolProvider = executable.getPlatformToolProvider();
                link.setOutputFile(buildDirectory.file(providers.provider(new Callable<String>() {
                    @Override
                    public String call() {
                        return toolProvider.getExecutableName("exe/" + names.getDirName() + executable.getBaseName().get());
                    }
                })));
                link.setTargetPlatform(targetPlatform);
                link.setToolChain(toolChain);
                link.setDebuggable(executable.isDebuggable());

                executable.getLinkTask().set(link);
                executable.getDebuggerExecutableFile().set(link.getBinaryFile());

                if (executable.isDebuggable() && executable.isOptimized() && toolProvider.requiresDebugBinaryStripping()) {
                    Provider<RegularFile> symbolLocation = buildDirectory.file(providers.provider(new Callable<String>() {
                        @Override
                        public String call() {
                            return toolProvider.getExecutableSymbolFileName("exe/" + names.getDirName() + "stripped/" + executable.getBaseName().get());
                        }
                    }));
                    Provider<RegularFile> strippedLocation = buildDirectory.file(providers.provider(new Callable<String>() {
                        @Override
                        public String call() {
                            return toolProvider.getExecutableName("exe/" + names.getDirName() + "stripped/" + executable.getBaseName().get());
                        }
                    }));
                    StripSymbols stripSymbols = stripSymbols(link, names, tasks, toolChain, targetPlatform, strippedLocation);
                    executable.getExecutableFile().set(stripSymbols.getOutputFile());
                    ExtractSymbols extractSymbols = extractSymbols(link, names, tasks, toolChain, targetPlatform, symbolLocation);
                    executable.getOutputs().from(extractSymbols.getSymbolFile());
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
                install.lib(executable.getRuntimeLibraries());

                executable.getInstallTask().set(install);
                executable.getInstallDirectory().set(install.getInstallDirectory());

                executable.getOutputs().from(executable.getInstallDirectory());
            }
        });
        components.withType(ConfigurableComponentWithSharedLibrary.class, new Action<ConfigurableComponentWithSharedLibrary>() {
            @Override
            public void execute(final ConfigurableComponentWithSharedLibrary library) {
                final Names names = Names.of(library.getName());
                NativePlatform targetPlatform = library.getTargetPlatform();
                NativeToolChain toolChain = library.getToolChain();

                // Add a link task
                final LinkSharedLibrary link = tasks.create(names.getTaskName("link"), LinkSharedLibrary.class);
                link.source(library.getObjects());
                link.lib(library.getLinkLibraries());
                // TODO - need to set soname
                final PlatformToolProvider toolProvider = library.getPlatformToolProvider();
                Provider<RegularFile> runtimeFile = buildDirectory.file(providers.provider(new Callable<String>() {
                    @Override
                    public String call() {
                        return toolProvider.getSharedLibraryName("lib/" + names.getDirName() + library.getBaseName().get());
                    }
                }));
                link.setOutputFile(runtimeFile);
                link.setTargetPlatform(targetPlatform);
                link.setToolChain(toolChain);
                link.setDebuggable(library.isDebuggable());

                Provider<RegularFile> linkFile = link.getBinaryFile();
                runtimeFile = link.getBinaryFile();

                if (toolProvider.producesImportLibrary()) {
                    Provider<RegularFile> importLibrary = buildDirectory.file(providers.provider(new Callable<String>() {
                        @Override
                        public String call() {
                            return toolProvider.getImportLibraryName("lib/" + names.getDirName() + library.getBaseName().get());
                        }
                    }));
                    link.getImportLibrary().set(importLibrary);
                    linkFile = link.getImportLibrary();
                }

                if (library.isDebuggable() && library.isOptimized() && toolProvider.requiresDebugBinaryStripping()) {
                    Provider<RegularFile> symbolLocation = buildDirectory.file(providers.provider(new Callable<String>() {
                        @Override
                        public String call() {
                            return toolProvider.getLibrarySymbolFileName("lib/" + names.getDirName() + "stripped/" + library.getBaseName().get());
                        }
                    }));
                    Provider<RegularFile> strippedLocation = buildDirectory.file(providers.provider(new Callable<String>() {
                        @Override
                        public String call() {
                            return toolProvider.getSharedLibraryName("lib/" + names.getDirName() + "stripped/" + library.getBaseName().get());
                        }
                    }));
                    StripSymbols stripSymbols = stripSymbols(link, names, tasks, toolChain, targetPlatform, strippedLocation);
                    runtimeFile = stripSymbols.getOutputFile();
                    linkFile = stripSymbols.getOutputFile();

                    ExtractSymbols extractSymbols = extractSymbols(link, names, tasks, toolChain, targetPlatform, symbolLocation);
                    library.getOutputs().from(extractSymbols.getSymbolFile());
                }
                library.getLinkTask().set(link);
                library.getLinkFile().set(linkFile);
                library.getRuntimeFile().set(runtimeFile);
                library.getOutputs().from(library.getLinkFile());
                library.getOutputs().from(library.getRuntimeFile());
            }
        });
        components.withType(ConfigurableComponentWithStaticLibrary.class, new Action<ConfigurableComponentWithStaticLibrary>() {
            @Override
            public void execute(final ConfigurableComponentWithStaticLibrary library) {
                final Names names = Names.of(library.getName());

                // Add a create task
                final CreateStaticLibrary createTask = tasks.create(names.getTaskName("create"), CreateStaticLibrary.class);
                createTask.source(library.getObjects());
                final PlatformToolProvider toolProvider = library.getPlatformToolProvider();
                Provider<RegularFile> runtimeFile = buildDirectory.file(providers.provider(new Callable<String>() {
                    @Override
                    public String call() {
                        return toolProvider.getStaticLibraryName("lib/" + names.getDirName() + library.getBaseName().get());
                    }
                }));
                createTask.setOutputFile(runtimeFile);
                createTask.setTargetPlatform(library.getTargetPlatform());
                createTask.setToolChain(library.getToolChain());

                // Wire the task into the library model
                library.getLinkFile().set(createTask.getBinaryFile());
                library.getCreateTask().set(createTask);
                library.getOutputs().from(library.getLinkFile());
            }
        });
    }

    private StripSymbols stripSymbols(AbstractLinkTask link, Names names, TaskContainer tasks, NativeToolChain toolChain, NativePlatform currentPlatform, Provider<RegularFile> strippedLocation) {
        StripSymbols stripSymbols = tasks.create(names.getTaskName("stripSymbols"), StripSymbols.class);
        stripSymbols.getBinaryFile().set(link.getBinaryFile());
        stripSymbols.getOutputFile().set(strippedLocation);
        stripSymbols.setTargetPlatform(currentPlatform);
        stripSymbols.setToolChain(toolChain);

        return stripSymbols;
    }

    private ExtractSymbols extractSymbols(AbstractLinkTask link, Names names, TaskContainer tasks, NativeToolChain toolChain, NativePlatform currentPlatform, Provider<RegularFile> symbolLocation) {
        ExtractSymbols extractSymbols = tasks.create(names.getTaskName("extractSymbols"), ExtractSymbols.class);
        extractSymbols.getBinaryFile().set(link.getBinaryFile());
        extractSymbols.getSymbolFile().set(symbolLocation);
        extractSymbols.setTargetPlatform(currentPlatform);
        extractSymbols.setToolChain(toolChain);

        return extractSymbols;
    }
}
