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

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.NonNullApi;
import org.gradle.api.Plugin;
import org.gradle.api.file.DirectoryVar;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.component.ComponentAwareRepository;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.cpp.CppBinary;
import org.gradle.language.cpp.CppExecutable;
import org.gradle.language.cpp.CppSharedLibrary;
import org.gradle.language.cpp.tasks.CppCompile;
import org.gradle.language.nativeplatform.internal.Names;
import org.gradle.language.nativeplatform.plugins.DiscoveredInputsPlugin;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.nativeplatform.tasks.InstallExecutable;
import org.gradle.nativeplatform.tasks.LinkExecutable;
import org.gradle.nativeplatform.tasks.LinkSharedLibrary;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.plugins.StandardToolChainsPlugin;

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
    public void apply(ProjectInternal project) {
        project.getPluginManager().apply(LifecycleBasePlugin.class);
        project.getPluginManager().apply(StandardToolChainsPlugin.class);
        project.getPluginManager().apply(DiscoveredInputsPlugin.class);

        final TaskContainerInternal tasks = project.getTasks();
        final DirectoryVar buildDirectory = project.getLayout().getBuildDirectory();
        final ModelRegistry modelRegistry = project.getModelRegistry();
        final ProviderFactory providers = project.getProviders();

        // Enable the use of Gradle metadata. This is a temporary opt-in switch until available by default
        project.getRepositories().withType(ComponentAwareRepository.class, new Action<ComponentAwareRepository>() {
            @Override
            public void execute(ComponentAwareRepository componentAwareRepository) {
                componentAwareRepository.useGradleMetadata();
            }
        });

        // Create the tasks for each C++ binary that is registered
        project.getComponents().withType(CppBinary.class, new Action<CppBinary>() {
            @Override
            public void execute(final CppBinary binary) {
                final Names names = Names.of(binary.getName());

                CppCompile compile = tasks.create(names.getCompileTaskName("cpp"), CppCompile.class);
                compile.includes(binary.getCompileIncludePath());
                compile.source(binary.getCppSource());
                if (binary.isDebuggable()) {
                    compile.setDebuggable(true);
                } else {
                    compile.setOptimized(true);
                }
                compile.getObjectFileDir().set(buildDirectory.dir("obj/" + names.getDirName()));

                DefaultNativePlatform currentPlatform = new DefaultNativePlatform("current");
                compile.setTargetPlatform(currentPlatform);

                // TODO - make this lazy
                NativeToolChain toolChain = modelRegistry.realize("toolChains", NativeToolChainRegistryInternal.class).getForPlatform(currentPlatform);
                compile.setToolChain(toolChain);

                if (binary instanceof CppExecutable) {
                    // Add a link task
                    LinkExecutable link = tasks.create(names.getTaskName("link"), LinkExecutable.class);
                    link.source(compile.getObjectFileDir().getAsFileTree().matching(new PatternSet().include("**/*.obj", "**/*.o")));
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
                    install.setDestinationDir(buildDirectory.dir("install/" + names.getDirName()));
                    install.setExecutable(link.getBinaryFile());
                    install.lib(binary.getRuntimeLibraries());
                } else if (binary instanceof CppSharedLibrary) {
                    final PlatformToolProvider toolProvider = ((NativeToolChainInternal) toolChain).select(currentPlatform);

                    compile.setPositionIndependentCode(true);

                    // Add a link task
                    LinkSharedLibrary link = tasks.create(names.getTaskName("link"), LinkSharedLibrary.class);
                    link.source(compile.getObjectFileDir().getAsFileTree().matching(new PatternSet().include("**/*.obj", "**/*.o")));
                    link.lib(binary.getLinkLibraries());
                    // TODO - need to set soname
                    Provider<RegularFile> runtimeFile = buildDirectory.file(providers.provider(new Callable<String>() {
                        @Override
                        public String call() throws Exception {
                            return toolProvider.getSharedLibraryName("lib/" + names.getDirName() + binary.getBaseName().get());
                        }
                    }));
                    link.setOutputFile(runtimeFile);
                    link.setTargetPlatform(currentPlatform);
                    link.setToolChain(toolChain);
                    link.setDebuggable(binary.isDebuggable());
                }
            }
        });
    }

}
