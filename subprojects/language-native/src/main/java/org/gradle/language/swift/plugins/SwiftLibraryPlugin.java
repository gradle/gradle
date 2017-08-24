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

import com.google.common.collect.Lists;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.PropertyState;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.cpp.plugins.CppBasePlugin;
import org.gradle.language.swift.SwiftComponent;
import org.gradle.language.swift.SwiftLibrary;
import org.gradle.language.swift.internal.DefaultSwiftLibrary;
import org.gradle.language.swift.tasks.SwiftCompile;
import org.gradle.nativeplatform.tasks.LinkSharedLibrary;
import org.gradle.util.GUtil;

import javax.inject.Inject;

/**
 * <p>A plugin that produces a shared library from Swift source.</p>
 *
 * <p>Adds compile, link and install tasks to build the shared library. Defaults to looking for source files in `src/main/swift`.</p>
 *
 * <p>Adds a {@link SwiftComponent} extension to the project to allow configuration of the library.</p>
 *
 * @since 4.1
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

        SwiftLibrary library = project.getExtensions().create(SwiftLibrary.class, "library", DefaultSwiftLibrary.class, "main", fileOperations, project.getProviders());
        project.getComponents().add(library);
        project.getComponents().add(library.getDebugSharedLibrary());
        project.getComponents().add(library.getReleaseSharedLibrary());

        // Setup component
        final PropertyState<String> module = library.getModule();
        module.set(GUtil.toCamelCase(project.getName()));
        library.getCompileImportPath().from(configurations.getByName(SwiftBasePlugin.SWIFT_IMPORT_PATH));
        library.getLinkLibraries().from(configurations.getByName(CppBasePlugin.NATIVE_LINK));

        // Configure compile task
        SwiftCompile compile = (SwiftCompile) tasks.getByName("compileSwift");
        compile.setCompilerArgs(Lists.newArrayList("-g", "-enable-testing"));

        // Add a link task
        LinkSharedLibrary link = (LinkSharedLibrary) tasks.getByName("linkMain");

        tasks.getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).dependsOn(link);

        // TODO - add lifecycle tasks
        Configuration api = configurations.getByName(SwiftBasePlugin.API);

        // TODO - extract common code with C++ plugins
        Configuration apiElements = configurations.create("swiftApiElements");
        apiElements.extendsFrom(api);
        apiElements.setCanBeResolved(false);
        apiElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.SWIFT_API));
        apiElements.getOutgoing().artifact(compile.getObjectFileDirectory());

        Configuration implementation = configurations.getByName(SwiftBasePlugin.IMPLEMENTATION);

        Configuration linkElements = configurations.create("linkElements");
        linkElements.extendsFrom(implementation);
        linkElements.setCanBeResolved(false);
        linkElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.NATIVE_LINK));
        // TODO - should distinguish between link-time and runtime files
        linkElements.getOutgoing().artifact(link.getBinaryFile());

        Configuration runtimeElements = configurations.create("runtimeElements");
        runtimeElements.extendsFrom(implementation);
        runtimeElements.setCanBeResolved(false);
        runtimeElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.NATIVE_RUNTIME));
        // TODO - should distinguish between link-time and runtime files
        runtimeElements.getOutgoing().artifact(link.getBinaryFile());
    }
}
