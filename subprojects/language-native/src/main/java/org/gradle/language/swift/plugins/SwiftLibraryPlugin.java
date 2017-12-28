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
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.language.internal.NativeComponentFactory;
import org.gradle.language.nativeplatform.internal.toolchains.ToolChainSelector;
import org.gradle.language.swift.SwiftComponent;
import org.gradle.language.swift.SwiftLibrary;
import org.gradle.language.swift.SwiftPlatform;
import org.gradle.language.swift.SwiftSharedLibrary;
import org.gradle.language.swift.SwiftStaticLibrary;
import org.gradle.language.swift.internal.DefaultSwiftLibrary;
import org.gradle.language.swift.tasks.SwiftCompile;
import org.gradle.nativeplatform.Linkage;
import org.gradle.util.GUtil;

import javax.inject.Inject;

import static org.gradle.language.cpp.CppBinary.DEBUGGABLE_ATTRIBUTE;
import static org.gradle.language.cpp.CppBinary.OPTIMIZED_ATTRIBUTE;

/**
 * <p>A plugin that produces a shared library from Swift source.</p>
 *
 * <p>Adds compile, link and install tasks to build the shared library. Defaults to looking for source files in `src/main/swift`.</p>
 *
 * <p>Adds a {@link SwiftComponent} extension to the project to allow configuration of the library.</p>
 *
 * @since 4.2
 */
@Incubating
public class SwiftLibraryPlugin implements Plugin<Project> {
    private final NativeComponentFactory componentFactory;
    private final ToolChainSelector toolChainSelector;

    @Inject
    public SwiftLibraryPlugin(NativeComponentFactory componentFactory, ToolChainSelector toolChainSelector) {
        this.componentFactory = componentFactory;
        this.toolChainSelector = toolChainSelector;
    }

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(SwiftBasePlugin.class);

        final ConfigurationContainer configurations = project.getConfigurations();
        final ObjectFactory objectFactory = project.getObjects();

        final DefaultSwiftLibrary library = componentFactory.newInstance(SwiftLibrary.class, DefaultSwiftLibrary.class, "main");
        project.getExtensions().add(SwiftLibrary.class, "library", library);
        project.getComponents().add(library);

        // Setup component
        final Property<String> module = library.getModule();
        module.set(GUtil.toCamelCase(project.getName()));

        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project) {
                library.getLinkage().lockNow();
                if (library.getLinkage().get().isEmpty()) {
                    throw new IllegalArgumentException("A linkage needs to be specified for the library.");
                }

                boolean sharedLibs = library.getLinkage().get().contains(Linkage.SHARED);
                boolean staticLibs = library.getLinkage().get().contains(Linkage.STATIC);

                ToolChainSelector.Result<SwiftPlatform> result = toolChainSelector.select(SwiftPlatform.class);

                SwiftSharedLibrary debugSharedLibrary = null;
                if (sharedLibs) {
                    String linkageNameSuffix = staticLibs ? "Shared" : "";
                    debugSharedLibrary = library.addSharedLibrary("debug" + linkageNameSuffix, true, false, true, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());
                    SwiftSharedLibrary releaseSharedLibrary = library.addSharedLibrary("release" + linkageNameSuffix, true, true, false, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());

                    // Add publications
                    SwiftCompile compileDebug = debugSharedLibrary.getCompileTask().get();
                    SwiftCompile compileRelease = releaseSharedLibrary.getCompileTask().get();

                    // TODO - add lifecycle tasks
                    // TODO - extract some common code to setup the configurations
                    // TODO - extract common code with C++ plugins

                    Configuration api = library.getApiDependencies();

                    Configuration debugApiElements = configurations.create("debugSwiftApiElements");
                    debugApiElements.extendsFrom(api);
                    debugApiElements.setCanBeResolved(false);
                    debugApiElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.SWIFT_API));
                    debugApiElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, debugSharedLibrary.isDebuggable());
                    debugApiElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, debugSharedLibrary.isOptimized());
                    debugApiElements.getOutgoing().artifact(compileDebug.getModuleFile());

                    Configuration releaseApiElements = configurations.create("releaseSwiftApiElements");
                    releaseApiElements.extendsFrom(api);
                    releaseApiElements.setCanBeResolved(false);
                    releaseApiElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.SWIFT_API));
                    releaseApiElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, releaseSharedLibrary.isDebuggable());
                    releaseApiElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, releaseSharedLibrary.isOptimized());
                    releaseApiElements.getOutgoing().artifact(compileRelease.getModuleFile());
                }

                SwiftStaticLibrary debugStaticLibrary = null;
                if (staticLibs){
                    String linkageNameSuffix = sharedLibs ? "Static" : "";
                    debugStaticLibrary = library.addStaticLibrary("debug" + linkageNameSuffix, true, false, true, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());
                    SwiftStaticLibrary releaseStaticLibrary = library.addStaticLibrary("release" + linkageNameSuffix, true, true, false, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());

                    if (!sharedLibs) {
                        // Add publications
                        SwiftCompile compileDebug = debugStaticLibrary.getCompileTask().get();
                        SwiftCompile compileRelease = releaseStaticLibrary.getCompileTask().get();

                        Configuration api = library.getApiDependencies();

                        Configuration debugApiElements = configurations.create("debugStaticSwiftApiElements");
                        debugApiElements.extendsFrom(api);
                        debugApiElements.setCanBeResolved(false);
                        debugApiElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.SWIFT_API));
                        debugApiElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, debugStaticLibrary.isDebuggable());
                        debugApiElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, debugStaticLibrary.isOptimized());
                        debugApiElements.getOutgoing().artifact(compileDebug.getModuleFile());

                        Configuration releaseApiElements = configurations.create("releaseStaticSwiftApiElements");
                        releaseApiElements.extendsFrom(api);
                        releaseApiElements.setCanBeResolved(false);
                        releaseApiElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.SWIFT_API));
                        releaseApiElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, releaseStaticLibrary.isDebuggable());
                        releaseApiElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, releaseStaticLibrary.isOptimized());
                        releaseApiElements.getOutgoing().artifact(compileRelease.getModuleFile());
                    }
                }

                if (sharedLibs) {
                    library.getDevelopmentBinary().set(debugSharedLibrary);
                } else {
                    library.getDevelopmentBinary().set(debugStaticLibrary);
                }

                library.getBinaries().realizeNow();
            }
        });
    }
}
