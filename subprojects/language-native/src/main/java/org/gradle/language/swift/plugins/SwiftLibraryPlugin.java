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
import org.gradle.api.file.DirectoryVar;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.cpp.plugins.CppBasePlugin;
import org.gradle.language.swift.internal.DefaultSwiftComponent;
import org.gradle.language.swift.model.SwiftComponent;
import org.gradle.language.swift.tasks.SwiftCompile;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.nativeplatform.tasks.LinkSharedLibrary;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;

import javax.inject.Inject;
import java.util.Collections;

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
        DirectoryVar buildDirectory = project.getLayout().getBuildDirectory();
        ObjectFactory objectFactory = project.getObjects();

        SwiftComponent component = project.getExtensions().create(SwiftComponent.class, "library", DefaultSwiftComponent.class, fileOperations);

        // TODO - extract some common code to setup the compile task and conventions
        // Add a compile task
        final SwiftCompile compile = tasks.create("compileSwift", SwiftCompile.class);

        compile.includes(configurations.getByName(SwiftBasePlugin.SWIFT_IMPORT_PATH));

        FileCollection sourceTree = component.getSwiftSource();
        compile.source(sourceTree);

        compile.setCompilerArgs(Lists.newArrayList("-g"));
        compile.setMacros(Collections.<String, String>emptyMap());
        compile.setModuleName(project.getName());

        compile.setObjectFileDir(buildDirectory.dir("main/objs"));

        DefaultNativePlatform currentPlatform = new DefaultNativePlatform("current");
        compile.setTargetPlatform(currentPlatform);

        // TODO - make this lazy
        NativeToolChain toolChain = ((ProjectInternal) project).getModelRegistry().realize("toolChains", NativeToolChainRegistryInternal.class).getForPlatform(currentPlatform);
        final PlatformToolProvider platformToolChain = ((NativeToolChainInternal) toolChain).select(currentPlatform);
        compile.setToolChain(toolChain);

        // Add a link task
        final LinkSharedLibrary link = tasks.create("linkMain", LinkSharedLibrary.class);
        link.source(compile.getObjectFileDirectory().getAsFileTree().matching(new PatternSet().include("**/*.obj", "**/*.o")));
        link.lib(configurations.getByName(CppBasePlugin.NATIVE_LINK));
        link.setLinkerArgs(Collections.<String>emptyList());
        // TODO - need to set basename and soname
        Provider<RegularFile> runtimeFile = buildDirectory.file(platformToolChain.getSharedLibraryName("lib/" + project.getName()));
        link.setOutputFile(runtimeFile);
        link.setTargetPlatform(currentPlatform);
        link.setToolChain(toolChain);

        tasks.getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).dependsOn(link);

        // TODO - add lifecycle tasks
        Configuration api = configurations.getByName(SwiftBasePlugin.API);

        // TODO - extract common code with C++ plugins
        Configuration apiElements = configurations.create("swiftApiElements");
        apiElements.extendsFrom(api);
        apiElements.setCanBeResolved(false);
        apiElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.SWIFT_API));
        // TODO - should reflect changes to output file
        apiElements.getOutgoing().artifact(compile.getObjectFileDirectory());

        Configuration implementation = configurations.getByName(SwiftBasePlugin.IMPLEMENTATION);

        Configuration linkElements = configurations.create("linkElements");
        linkElements.extendsFrom(implementation);
        linkElements.setCanBeResolved(false);
        linkElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.NATIVE_LINK));
        // TODO - should reflect changes to task output file
        // TODO - should distinguish between link-time and runtime files
        linkElements.getOutgoing().artifact(link.getBinaryFile());

        Configuration runtimeElements = configurations.create("runtimeElements");
        runtimeElements.extendsFrom(implementation);
        runtimeElements.setCanBeResolved(false);
        runtimeElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.NATIVE_RUNTIME));
        // TODO - should reflect changes to task output file
        // TODO - should distinguish between link-time and runtime files
        runtimeElements.getOutgoing().artifact(link.getBinaryFile());
    }
}
