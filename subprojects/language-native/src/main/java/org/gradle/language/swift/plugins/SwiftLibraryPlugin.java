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

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.cpp.CppBinary;
import org.gradle.language.swift.SwiftComponent;
import org.gradle.language.swift.SwiftLibrary;
import org.gradle.language.swift.SwiftSharedLibrary;
import org.gradle.language.swift.internal.DefaultSwiftLibrary;
import org.gradle.language.swift.tasks.SwiftCompile;
import org.gradle.util.GUtil;

import javax.inject.Inject;

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

        TaskContainer tasks = project.getTasks();
        ConfigurationContainer configurations = project.getConfigurations();
        ObjectFactory objectFactory = project.getObjects();

        SwiftLibrary library = project.getExtensions().create(SwiftLibrary.class, "library", DefaultSwiftLibrary.class, "main", project.getLayout(), objectFactory, fileOperations, configurations);
        project.getComponents().add(library);
        SwiftSharedLibrary debugSharedLibrary = library.getDebugSharedLibrary();
        project.getComponents().add(debugSharedLibrary);
        SwiftSharedLibrary releaseSharedLibrary = library.getReleaseSharedLibrary();
        project.getComponents().add(releaseSharedLibrary);

        // Setup component
        final Property<String> module = library.getModule();
        module.set(GUtil.toCamelCase(project.getName()));

        // Configure compile task
        final SwiftCompile compileDebug = (SwiftCompile) tasks.getByName("compileDebugSwift");
        final SwiftCompile compileRelease = (SwiftCompile) tasks.getByName("compileReleaseSwift");

        tasks.getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).dependsOn(library.getDevelopmentBinary().getRuntimeFile());

        // TODO - add lifecycle tasks
        // TODO - extract some common code to setup the configurations
        // TODO - extract common code with C++ plugins

        Configuration implementation = library.getImplementationDependencies();
        Configuration api = library.getApiDependencies();

        Configuration debugApiElements = configurations.maybeCreate("debugSwiftApiElements");
        debugApiElements.extendsFrom(api);
        debugApiElements.setCanBeResolved(false);
        debugApiElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.SWIFT_API));
        debugApiElements.getAttributes().attribute(CppBinary.DEBUGGABLE_ATTRIBUTE, true);
        debugApiElements.getOutgoing().artifact(compileDebug.getModuleFile());

        Configuration debugLinkElements = configurations.maybeCreate("debugLinkElements");
        debugLinkElements.extendsFrom(implementation);
        debugLinkElements.setCanBeResolved(false);
        debugLinkElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.NATIVE_LINK));
        debugLinkElements.getAttributes().attribute(CppBinary.DEBUGGABLE_ATTRIBUTE, true);
        // TODO - should distinguish between link-time and runtime files, we're assuming here that they are the same
        debugLinkElements.getOutgoing().artifact(debugSharedLibrary.getRuntimeFile());

        Configuration debugRuntimeElements = configurations.maybeCreate("debugRuntimeElements");
        debugRuntimeElements.extendsFrom(implementation);
        debugRuntimeElements.setCanBeResolved(false);
        debugRuntimeElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.NATIVE_RUNTIME));
        debugRuntimeElements.getAttributes().attribute(CppBinary.DEBUGGABLE_ATTRIBUTE, true);
        // TODO - should distinguish between link-time and runtime files
        debugRuntimeElements.getOutgoing().artifact(debugSharedLibrary.getRuntimeFile());

        Configuration releaseApiElements = configurations.maybeCreate("releaseSwiftApiElements");
        releaseApiElements.extendsFrom(api);
        releaseApiElements.setCanBeResolved(false);
        releaseApiElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.SWIFT_API));
        releaseApiElements.getAttributes().attribute(CppBinary.DEBUGGABLE_ATTRIBUTE, false);
        releaseApiElements.getOutgoing().artifact(compileRelease.getModuleFile());

        Configuration releaseLinkElements = configurations.maybeCreate("releaseLinkElements");
        releaseLinkElements.extendsFrom(implementation);
        releaseLinkElements.setCanBeResolved(false);
        releaseLinkElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.NATIVE_LINK));
        releaseLinkElements.getAttributes().attribute(CppBinary.DEBUGGABLE_ATTRIBUTE, false);
        // TODO - should distinguish between link-time and runtime files, we're assuming here that they are the same
        releaseLinkElements.getOutgoing().artifact(releaseSharedLibrary.getRuntimeFile());

        Configuration releaseRuntimeElements = configurations.maybeCreate("releaseRuntimeElements");
        releaseRuntimeElements.extendsFrom(implementation);
        releaseRuntimeElements.setCanBeResolved(false);
        releaseRuntimeElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.NATIVE_RUNTIME));
        releaseRuntimeElements.getAttributes().attribute(CppBinary.DEBUGGABLE_ATTRIBUTE, false);
        // TODO - should distinguish between link-time and runtime files
        releaseRuntimeElements.getOutgoing().artifact(releaseSharedLibrary.getRuntimeFile());
    }
}
