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
import org.gradle.api.Plugin;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.DirectoryVar;
import org.gradle.api.file.RegularFile;
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
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.nativeplatform.tasks.LinkExecutable;
import org.gradle.nativeplatform.tasks.LinkSharedLibrary;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.plugins.StandardToolChainsPlugin;

import java.util.Collections;
import java.util.concurrent.Callable;

/**
 * A common base plugin for the C++ executable and library plugins
 *
 * @since 4.1
 */
@Incubating
public class CppBasePlugin implements Plugin<ProjectInternal> {
    /**
     * The name of the implementation configuration.
     */
    public static final String IMPLEMENTATION = "implementation";

    /**
     * The name of the C++ compile classpath configuration.
     */
    public static final String CPP_INCLUDE_PATH = "cppCompileIncludePath";

    /**
     * The name of the native link files configuration.
     */
    public static final String NATIVE_LINK = "nativeLink";

    /**
     * The name of the native runtime files configuration.
     */
    public static final String NATIVE_RUNTIME = "nativeRuntime";

    /**
     * The name of the native test link files configuration.
     *
     * @since 4.2
     */
    public static final String NATIVE_TEST_LINK = "nativeTestLink";

    /**
     * The name of the native test runtime files configuration.
     *
     * @since 4.2
     */
    public static final String NATIVE_TEST_RUNTIME = "nativeTestRuntime";

    @Override
    public void apply(ProjectInternal project) {
        project.getPluginManager().apply(LifecycleBasePlugin.class);
        project.getPluginManager().apply(StandardToolChainsPlugin.class);

        Configuration implementation = project.getConfigurations().create(IMPLEMENTATION);
        implementation.setCanBeConsumed(false);
        implementation.setCanBeResolved(false);

        Configuration includePath = project.getConfigurations().create(CPP_INCLUDE_PATH);
        includePath.extendsFrom(implementation);
        includePath.setCanBeConsumed(false);
        includePath.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.C_PLUS_PLUS_API));

        Configuration nativeLink = project.getConfigurations().create(NATIVE_LINK);
        nativeLink.extendsFrom(implementation);
        nativeLink.setCanBeConsumed(false);
        nativeLink.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.NATIVE_LINK));

        Configuration nativeRuntime = project.getConfigurations().create(NATIVE_RUNTIME);
        nativeRuntime.extendsFrom(implementation);
        nativeRuntime.setCanBeConsumed(false);
        nativeRuntime.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.NATIVE_RUNTIME));

        final TaskContainerInternal tasks = project.getTasks();
        final DirectoryVar buildDirectory = project.getLayout().getBuildDirectory();
        final ModelRegistry modelRegistry = project.getModelRegistry();
        final ProviderFactory providers = project.getProviders();

        project.getComponents().withType(CppBinary.class, new Action<CppBinary>() {
            @Override
            public void execute(final CppBinary binary) {
                final Names names = Names.of(binary.getName());
                CppCompile compile = tasks.create(names.getCompileTaskName("cpp"), CppCompile.class);
                compile.includes(binary.getCompileIncludePath());
                compile.source(binary.getCppSource());

                compile.setCompilerArgs(Collections.<String>emptyList());
                compile.setMacros(Collections.<String, String>emptyMap());
                compile.setObjectFileDir(buildDirectory.dir("obj/" + names.getDirName()));

                DefaultNativePlatform currentPlatform = new DefaultNativePlatform("current");
                compile.setTargetPlatform(currentPlatform);

                // TODO - make this lazy
                NativeToolChain toolChain = modelRegistry.realize("toolChains", NativeToolChainRegistryInternal.class).getForPlatform(currentPlatform);
                compile.setToolChain(toolChain);

                if (binary instanceof CppExecutable) {
                    // Add a link task
                    LinkExecutable link = tasks.create(names.getTaskName("link"), LinkExecutable.class);
                    link.source(compile.getObjectFileDirectory().getAsFileTree().matching(new PatternSet().include("**/*.obj", "**/*.o")));
                    link.lib(binary.getLinkLibraries());
                    link.setLinkerArgs(Collections.<String>emptyList());
                    final PlatformToolProvider toolProvider = ((NativeToolChainInternal) toolChain).select(currentPlatform);
                    link.setOutputFile(buildDirectory.file(providers.provider(new Callable<String>() {
                        @Override
                        public String call() throws Exception {
                            return toolProvider.getExecutableName("exe/" + names.getDirName() + binary.getBaseName().get());
                        }
                    })));
                    link.setTargetPlatform(currentPlatform);
                    link.setToolChain(toolChain);
                } else if (binary instanceof CppSharedLibrary) {
                    final PlatformToolProvider toolProvider = ((NativeToolChainInternal) toolChain).select(currentPlatform);

                    // Add a link task
                    LinkSharedLibrary link = tasks.create(names.getTaskName("link"), LinkSharedLibrary.class);
                    link.source(compile.getObjectFileDirectory().getAsFileTree().matching(new PatternSet().include("**/*.obj", "**/*.o")));
                    link.lib(binary.getLinkLibraries());
                    link.setLinkerArgs(Collections.<String>emptyList());
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
                }
            }
        });
    }

}
