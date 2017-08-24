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
import org.gradle.language.cpp.plugins.CppBasePlugin;
import org.gradle.language.nativeplatform.internal.Names;
import org.gradle.language.swift.SwiftBinary;
import org.gradle.language.swift.SwiftExecutable;
import org.gradle.language.swift.SwiftSharedLibrary;
import org.gradle.language.swift.tasks.SwiftCompile;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.nativeplatform.tasks.LinkExecutable;
import org.gradle.nativeplatform.tasks.LinkSharedLibrary;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.plugins.SwiftCompilerPlugin;

import java.util.Collections;
import java.util.concurrent.Callable;

/**
 * A common base plugin for the Swift executable and library plugins
 *
 * @since 4.1
 */
@Incubating
public class SwiftBasePlugin implements Plugin<ProjectInternal> {
    /**
     * The name of the implementation configuration.
     */
    public static final String IMPLEMENTATION = "implementation";

    /**
     * The name of the api configuration.
     */
    public static final String API = "api";

    /**
     * The name of the Swift compile import path configuration.
     */
    public static final String SWIFT_IMPORT_PATH = "swiftImportPath";

    /**
     * The name of the test implementation configuration.
     *
     * @since 4.2
     */
    public static final String TEST_IMPLEMENTATION = "testImplementation";

    /**
     * The name of the Swift test compile import path configuration.
     *
     * @since 4.2
     */
    public static final String SWIFT_TEST_IMPORT_PATH = "swiftTestImportPath";

    @Override
    public void apply(ProjectInternal project) {
        project.getPluginManager().apply(LifecycleBasePlugin.class);
        project.getPluginManager().apply(SwiftCompilerPlugin.class);

        // TODO - Merge with CppBasePlugin to remove code duplication
        Configuration api = project.getConfigurations().create(API);
        api.setCanBeConsumed(false);
        api.setCanBeResolved(false);

        Configuration implementation = project.getConfigurations().create(IMPLEMENTATION);
        implementation.extendsFrom(api);
        implementation.setCanBeConsumed(false);
        implementation.setCanBeResolved(false);

        Configuration importPath = project.getConfigurations().create(SWIFT_IMPORT_PATH);
        importPath.extendsFrom(implementation);
        importPath.setCanBeConsumed(false);
        importPath.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.SWIFT_API));

        Configuration nativeLink = project.getConfigurations().create(CppBasePlugin.NATIVE_LINK);
        nativeLink.extendsFrom(implementation);
        nativeLink.setCanBeConsumed(false);
        nativeLink.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.NATIVE_LINK));

        Configuration nativeRuntime = project.getConfigurations().create(CppBasePlugin.NATIVE_RUNTIME);
        nativeRuntime.extendsFrom(implementation);
        nativeRuntime.setCanBeConsumed(false);
        nativeRuntime.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.NATIVE_RUNTIME));

        Configuration testImplementation = project.getConfigurations().create(TEST_IMPLEMENTATION);
        testImplementation.extendsFrom(implementation);
        testImplementation.setCanBeConsumed(false);
        testImplementation.setCanBeResolved(false);

        Configuration testImportPath = project.getConfigurations().create(SWIFT_TEST_IMPORT_PATH);
        testImportPath.extendsFrom(testImplementation);
        testImportPath.setCanBeConsumed(false);
        testImportPath.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.SWIFT_API));

        Configuration testNativeLink = project.getConfigurations().create(CppBasePlugin.NATIVE_TEST_LINK);
        testNativeLink.extendsFrom(testImplementation);
        testNativeLink.setCanBeConsumed(false);
        testNativeLink.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.NATIVE_LINK));

        Configuration testNativeRuntime = project.getConfigurations().create(CppBasePlugin.NATIVE_TEST_RUNTIME);
        testNativeRuntime.extendsFrom(testImplementation);
        testNativeRuntime.setCanBeConsumed(false);
        testNativeRuntime.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.NATIVE_RUNTIME));

        final TaskContainerInternal tasks = project.getTasks();
        final DirectoryVar buildDirectory = project.getLayout().getBuildDirectory();
        final ModelRegistry modelRegistry = project.getModelRegistry();
        final ProviderFactory providers = project.getProviders();

        project.getComponents().withType(SwiftBinary.class, new Action<SwiftBinary>() {
            @Override
            public void execute(final SwiftBinary component) {
                final Names names = Names.of(component.getName());
                SwiftCompile compile = tasks.create(names.getCompileTaskName("swift"), SwiftCompile.class);
                compile.includes(component.getCompileImportPath());
                compile.source(component.getSwiftSource());
                compile.setMacros(Collections.<String, String>emptyMap());
                compile.setModuleName(component.getModule());
                compile.setObjectFileDir(buildDirectory.dir("obj/" + names.getDirName()));

                DefaultNativePlatform currentPlatform = new DefaultNativePlatform("current");
                compile.setTargetPlatform(currentPlatform);

                // TODO - make this lazy
                NativeToolChain toolChain = modelRegistry.realize("toolChains", NativeToolChainRegistryInternal.class).getForPlatform(currentPlatform);
                compile.setToolChain(toolChain);

                if (component instanceof SwiftExecutable) {
                    // Add a link task
                    LinkExecutable link = tasks.create(names.getTaskName("link"), LinkExecutable.class);
                    link.source(compile.getObjectFileDirectory().getAsFileTree().matching(new PatternSet().include("**/*.obj", "**/*.o")));
                    link.lib(component.getLinkLibraries());
                    link.setLinkerArgs(Collections.<String>emptyList());
                    final PlatformToolProvider toolProvider = ((NativeToolChainInternal) toolChain).select(currentPlatform);
                    Provider<RegularFile> exeLocation = buildDirectory.file(providers.provider(new Callable<String>() {
                        @Override
                        public String call() {
                            return toolProvider.getExecutableName("exe/" + names.getDirName() + component.getModule().get());
                        }
                    }));
                    link.setOutputFile(exeLocation);
                    link.setTargetPlatform(currentPlatform);
                    link.setToolChain(toolChain);
                } else if (component instanceof SwiftSharedLibrary) {
                    // Add a link task
                    final LinkSharedLibrary link = tasks.create(names.getTaskName("link"), LinkSharedLibrary.class);
                    link.source(compile.getObjectFileDirectory().getAsFileTree().matching(new PatternSet().include("**/*.obj", "**/*.o")));
                    link.lib(component.getLinkLibraries());
                    link.setLinkerArgs(Collections.<String>emptyList());
                    // TODO - need to set soname
                    final PlatformToolProvider toolProvider = ((NativeToolChainInternal) toolChain).select(currentPlatform);
                    Provider<RegularFile> runtimeFile = buildDirectory.file(providers.provider(new Callable<String>() {
                        @Override
                        public String call() {
                            return toolProvider.getSharedLibraryName("lib/" + names.getDirName() + component.getModule().get());
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
