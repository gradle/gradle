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
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.swift.SwiftBinary;
import org.gradle.language.swift.SwiftComponent;
import org.gradle.language.swift.SwiftLibrary;
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
    private final FileOperations fileOperations;

    @Inject
    public SwiftLibraryPlugin(FileOperations fileOperations) {
        this.fileOperations = fileOperations;
    }

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(SwiftBasePlugin.class);

        final TaskContainer tasks = project.getTasks();
        final ConfigurationContainer configurations = project.getConfigurations();
        final ObjectFactory objectFactory = project.getObjects();

        final DefaultSwiftLibrary library = (DefaultSwiftLibrary) project.getExtensions().create(SwiftLibrary.class, "library", DefaultSwiftLibrary.class, "main", project.getLayout(), objectFactory, fileOperations, configurations);
        project.getComponents().add(library);
        library.getBinaries().whenElementKnown(new Action<SwiftBinary>() {
            @Override
            public void execute(SwiftBinary binary) {
                project.getComponents().add(binary);
            }
        });

        // Setup component
        final Property<String> module = library.getModule();
        module.set(GUtil.toCamelCase(project.getName()));

        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project) {
                if (library.getLinkage().get().isEmpty()) {
                    throw new IllegalArgumentException("A linkage needs to be specified for the library.");
                }

                boolean sharedLibs = library.getLinkage().get().contains(Linkage.SHARED);
                boolean staticLibs = library.getLinkage().get().contains(Linkage.STATIC);

                if (sharedLibs) {
                    SwiftSharedLibrary debugSharedLibrary = library.createSharedLibrary("debug", true, false, true);
                    SwiftSharedLibrary releaseSharedLibrary = library.createSharedLibrary("release", true, true, false);

                    // Add publications
                    SwiftCompile compileDebug = debugSharedLibrary.getCompileTask().get();
                    SwiftCompile compileRelease = releaseSharedLibrary.getCompileTask().get();

                    // TODO - add lifecycle tasks
                    // TODO - extract some common code to setup the configurations
                    // TODO - extract common code with C++ plugins

                    Configuration implementation = library.getImplementationDependencies();
                    Configuration api = library.getApiDependencies();

                    Configuration debugApiElements = configurations.maybeCreate("debugSwiftApiElements");
                    debugApiElements.extendsFrom(api);
                    debugApiElements.setCanBeResolved(false);
                    debugApiElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.SWIFT_API));
                    debugApiElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, debugSharedLibrary.isDebuggable());
                    debugApiElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, debugSharedLibrary.isOptimized());
                    debugApiElements.getOutgoing().artifact(compileDebug.getModuleFile());

                    Configuration debugLinkElements = configurations.maybeCreate("debugLinkElements");
                    debugLinkElements.extendsFrom(implementation);
                    debugLinkElements.setCanBeResolved(false);
                    debugLinkElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.NATIVE_LINK));
                    debugLinkElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, debugSharedLibrary.isDebuggable());
                    debugLinkElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, debugSharedLibrary.isOptimized());
                    // TODO - should distinguish between link-time and runtime files, we're assuming here that they are the same
                    debugLinkElements.getOutgoing().artifact(debugSharedLibrary.getRuntimeFile());

                    Configuration debugRuntimeElements = configurations.maybeCreate("debugRuntimeElements");
                    debugRuntimeElements.extendsFrom(implementation);
                    debugRuntimeElements.setCanBeResolved(false);
                    debugRuntimeElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.NATIVE_RUNTIME));
                    debugRuntimeElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, debugSharedLibrary.isDebuggable());
                    debugRuntimeElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, debugSharedLibrary.isOptimized());
                    // TODO - should distinguish between link-time and runtime files
                    debugRuntimeElements.getOutgoing().artifact(debugSharedLibrary.getRuntimeFile());

                    Configuration releaseApiElements = configurations.maybeCreate("releaseSwiftApiElements");
                    releaseApiElements.extendsFrom(api);
                    releaseApiElements.setCanBeResolved(false);
                    releaseApiElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.SWIFT_API));
                    releaseApiElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, releaseSharedLibrary.isDebuggable());
                    releaseApiElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, releaseSharedLibrary.isOptimized());
                    releaseApiElements.getOutgoing().artifact(compileRelease.getModuleFile());

                    Configuration releaseLinkElements = configurations.maybeCreate("releaseLinkElements");
                    releaseLinkElements.extendsFrom(implementation);
                    releaseLinkElements.setCanBeResolved(false);
                    releaseLinkElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.NATIVE_LINK));
                    releaseLinkElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, releaseSharedLibrary.isDebuggable());
                    releaseLinkElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, releaseSharedLibrary.isOptimized());
                    // TODO - should distinguish between link-time and runtime files, we're assuming here that they are the same
                    releaseLinkElements.getOutgoing().artifact(releaseSharedLibrary.getRuntimeFile());

                    Configuration releaseRuntimeElements = configurations.maybeCreate("releaseRuntimeElements");
                    releaseRuntimeElements.extendsFrom(implementation);
                    releaseRuntimeElements.setCanBeResolved(false);
                    releaseRuntimeElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.NATIVE_RUNTIME));
                    releaseRuntimeElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, releaseSharedLibrary.isDebuggable());
                    releaseRuntimeElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, releaseSharedLibrary.isOptimized());
                    // TODO - should distinguish between link-time and runtime files
                    releaseRuntimeElements.getOutgoing().artifact(releaseSharedLibrary.getRuntimeFile());
                }

                SwiftStaticLibrary debugStaticLibrary = null;
                if (staticLibs){
                    debugStaticLibrary = library.createStaticLibrary("debugStatic", true, false, true);
                    SwiftStaticLibrary releaseStaticLibrary = library.createStaticLibrary("releaseStatic", true, true, false);

                    if (!sharedLibs) {
                        // Add publications
                        SwiftCompile compileDebug = debugStaticLibrary.getCompileTask().get();
                        SwiftCompile compileRelease = releaseStaticLibrary.getCompileTask().get();

                        Configuration implementation = library.getImplementationDependencies();
                        Configuration api = library.getApiDependencies();

                        Configuration debugApiElements = configurations.maybeCreate("debugStaticSwiftApiElements");
                        debugApiElements.extendsFrom(api);
                        debugApiElements.setCanBeResolved(false);
                        debugApiElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.SWIFT_API));
                        debugApiElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, debugStaticLibrary.isDebuggable());
                        debugApiElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, debugStaticLibrary.isOptimized());
                        debugApiElements.getOutgoing().artifact(compileDebug.getModuleFile());

                        Configuration debugLinkElements = configurations.maybeCreate("debugStaticLinkElements");
                        debugLinkElements.extendsFrom(implementation);
                        debugLinkElements.setCanBeResolved(false);
                        debugLinkElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.NATIVE_LINK));
                        debugLinkElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, debugStaticLibrary.isDebuggable());
                        debugLinkElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, debugStaticLibrary.isOptimized());
                        debugLinkElements.getOutgoing().artifact(debugStaticLibrary.getLinkFile());

                        Configuration debugRuntimeElements = configurations.maybeCreate("debugStaticRuntimeElements");
                        debugRuntimeElements.extendsFrom(implementation);
                        debugRuntimeElements.setCanBeResolved(false);
                        debugRuntimeElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.NATIVE_RUNTIME));
                        debugRuntimeElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, debugStaticLibrary.isDebuggable());
                        debugRuntimeElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, debugStaticLibrary.isOptimized());

                        Configuration releaseApiElements = configurations.maybeCreate("releaseStaticSwiftApiElements");
                        releaseApiElements.extendsFrom(api);
                        releaseApiElements.setCanBeResolved(false);
                        releaseApiElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.SWIFT_API));
                        releaseApiElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, releaseStaticLibrary.isDebuggable());
                        releaseApiElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, releaseStaticLibrary.isOptimized());
                        releaseApiElements.getOutgoing().artifact(compileRelease.getModuleFile());

                        Configuration releaseLinkElements = configurations.maybeCreate("releaseStaticLinkElements");
                        releaseLinkElements.extendsFrom(implementation);
                        releaseLinkElements.setCanBeResolved(false);
                        releaseLinkElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.NATIVE_LINK));
                        releaseLinkElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, releaseStaticLibrary.isDebuggable());
                        releaseLinkElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, releaseStaticLibrary.isOptimized());
                        releaseLinkElements.getOutgoing().artifact(releaseStaticLibrary.getLinkFile());

                        Configuration releaseRuntimeElements = configurations.maybeCreate("releaseStaticRuntimeElements");
                        releaseRuntimeElements.extendsFrom(implementation);
                        releaseRuntimeElements.setCanBeResolved(false);
                        releaseRuntimeElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.NATIVE_RUNTIME));
                        releaseRuntimeElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, releaseStaticLibrary.isDebuggable());
                        releaseRuntimeElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, releaseStaticLibrary.isOptimized());
                    }
                }

                library.getBinaries().realizeNow();

                if (sharedLibs) {
                    tasks.getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).dependsOn(library.getDevelopmentBinary().map(new Transformer<Provider<RegularFile>, SwiftSharedLibrary>() {
                        @Override
                        public Provider<RegularFile> transform(SwiftSharedLibrary binary) {
                            return binary.getRuntimeFile();
                        }
                    }));
                } else {
                    // Should use the development binary as well
                    tasks.getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).dependsOn(debugStaticLibrary.getLinkFile());
                }
            }
        });
    }
}
