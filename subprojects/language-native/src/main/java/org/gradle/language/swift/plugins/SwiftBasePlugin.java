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

package org.gradle.language.swift.plugins;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.nativeplatform.internal.Names;
import org.gradle.language.swift.SwiftBinary;
import org.gradle.language.swift.SwiftExecutable;
import org.gradle.language.swift.SwiftSharedLibrary;
import org.gradle.language.swift.internal.DefaultSwiftBinary;
import org.gradle.language.swift.internal.DefaultSwiftExecutable;
import org.gradle.language.swift.internal.DefaultSwiftSharedLibrary;
import org.gradle.language.swift.tasks.SwiftCompile;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.nativeplatform.tasks.InstallExecutable;
import org.gradle.nativeplatform.tasks.LinkExecutable;
import org.gradle.nativeplatform.tasks.LinkSharedLibrary;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.plugins.SwiftCompilerPlugin;

import java.util.concurrent.Callable;

/**
 * A common base plugin for the Swift executable and library plugins
 *
 * @since 4.1
 */
@Incubating
public class SwiftBasePlugin implements Plugin<ProjectInternal> {
    @Override
    public void apply(ProjectInternal project) {
        project.getPluginManager().apply(LifecycleBasePlugin.class);
        project.getPluginManager().apply(SwiftCompilerPlugin.class);

        // TODO - Merge with CppBasePlugin to remove code duplication

        final TaskContainerInternal tasks = project.getTasks();
        final DirectoryProperty buildDirectory = project.getLayout().getBuildDirectory();
        final ModelRegistry modelRegistry = project.getModelRegistry();
        final ProviderFactory providers = project.getProviders();

        project.getComponents().withType(SwiftBinary.class, new Action<SwiftBinary>() {
            @Override
            public void execute(final SwiftBinary binary) {
                final Names names = Names.of(binary.getName());
                SwiftCompile compile = tasks.create(names.getCompileTaskName("swift"), SwiftCompile.class);
                compile.getModules().from(binary.getCompileModules());
                compile.getSource().from(binary.getSwiftSource());
                if (binary.isDebuggable()) {
                    compile.setDebuggable(true);
                } else {
                    compile.setOptimized(true);
                }
                if (binary.isTestable()) {
                    compile.getCompilerArgs().add("-enable-testing");
                }
                compile.getModuleName().set(binary.getModule());
                compile.getObjectFileDir().set(buildDirectory.dir("obj/" + names.getDirName()));
                compile.getModuleFile().set(buildDirectory.file(providers.provider(new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        return "modules/" + names.getDirName() + binary.getModule().get() + ".swiftmodule";
                    }
                })));
                ((DefaultSwiftBinary)binary).getModuleFile().set(compile.getModuleFile());

                DefaultNativePlatform currentPlatform = new DefaultNativePlatform("current");
                compile.setTargetPlatform(currentPlatform);

                // TODO - make this lazy
                NativeToolChain toolChain = modelRegistry.realize("toolChains", NativeToolChainRegistryInternal.class).getForPlatform(currentPlatform);
                compile.setToolChain(toolChain);

                ((DefaultSwiftBinary)binary).getObjectsDir().set(compile.getObjectFileDir());

                if (binary instanceof SwiftExecutable) {
                    // Add a link task
                    LinkExecutable link = tasks.create(names.getTaskName("link"), LinkExecutable.class);
                    link.source(binary.getObjects());
                    link.lib(binary.getLinkLibraries());
                    final PlatformToolProvider toolProvider = ((NativeToolChainInternal) toolChain).select(currentPlatform);
                    Provider<RegularFile> exeLocation = buildDirectory.file(providers.provider(new Callable<String>() {
                        @Override
                        public String call() {
                            return toolProvider.getExecutableName("exe/" + names.getDirName() + binary.getModule().get());
                        }
                    }));
                    link.setOutputFile(exeLocation);
                    link.setTargetPlatform(currentPlatform);
                    link.setToolChain(toolChain);
                    link.setDebuggable(binary.isDebuggable());

                    ((DefaultSwiftExecutable)binary).getExecutableFile().set(link.getBinaryFile());

                    // Add an install task
                    // TODO - maybe not for all executables
                    final InstallExecutable install = tasks.create(names.getTaskName("install"), InstallExecutable.class);
                    install.setPlatform(link.getTargetPlatform());
                    install.setToolChain(link.getToolChain());
                    install.getInstallDirectory().set(buildDirectory.dir("install/" + names.getDirName()));
                    install.getSourceFile().set(link.getBinaryFile());
                    install.lib(binary.getRuntimeLibraries());
                    ((DefaultSwiftExecutable)binary).getInstallDirectory().set(install.getInstallDirectory());
                    ((DefaultSwiftExecutable)binary).getRunScriptFile().set(install.getRunScriptFile());
                } else if (binary instanceof SwiftSharedLibrary) {
                    // Specific compiler arguments
                    compile.getCompilerArgs().add("-parse-as-library");

                    // Add a link task
                    final LinkSharedLibrary link = tasks.create(names.getTaskName("link"), LinkSharedLibrary.class);
                    link.source(binary.getObjects());
                    link.lib(binary.getLinkLibraries());
                    // TODO - need to set soname
                    final PlatformToolProvider toolProvider = ((NativeToolChainInternal) toolChain).select(currentPlatform);
                    Provider<RegularFile> runtimeFile = buildDirectory.file(providers.provider(new Callable<String>() {
                        @Override
                        public String call() {
                            return toolProvider.getSharedLibraryName("lib/" + names.getDirName() + binary.getModule().get());
                        }
                    }));
                    link.setOutputFile(runtimeFile);
                    link.setTargetPlatform(currentPlatform);
                    link.setToolChain(toolChain);
                    link.setDebuggable(binary.isDebuggable());

                    ((DefaultSwiftSharedLibrary)binary).getRuntimeFile().set(link.getBinaryFile());
                }
            }
        });
    }
}
