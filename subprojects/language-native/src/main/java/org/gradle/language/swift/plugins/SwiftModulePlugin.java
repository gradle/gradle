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
import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.cpp.plugins.CppBasePlugin;
import org.gradle.language.swift.tasks.SwiftCompile;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.nativeplatform.tasks.LinkSharedLibrary;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal;

import java.util.Collections;

/**
 * <p>A plugin that produces a native library from Swift source.</p>
 *
 * <p>Assumes the source files are located in `src/main/swift`.</p>
 *
 * @since 4.1
 */
@Incubating
public class SwiftModulePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(SwiftBasePlugin.class);

        // TODO - extract some common code to setup the compile task and conventions
        // Add a compile task
        final SwiftCompile compile = project.getTasks().create("compileSwift", SwiftCompile.class);

        compile.includes(project.getConfigurations().getByName(SwiftBasePlugin.SWIFT_IMPORT_PATH));

        ConfigurableFileTree sourceTree = project.fileTree("src/main/swift");
        sourceTree.include("**/*.swift");
        compile.source(sourceTree);

        compile.setCompilerArgs(Collections.<String>emptyList());
        compile.setMacros(Collections.<String, String>emptyMap());
        compile.setModuleName(project.getName());

        // TODO - should reflect changes to build directory
        compile.setObjectFileDir(project.file("build/main/objs"));

        DefaultNativePlatform currentPlatform = new DefaultNativePlatform("current");
        compile.setTargetPlatform(currentPlatform);

        // TODO - make this lazy
        NativeToolChain toolChain = ((ProjectInternal) project).getModelRegistry().realize("toolChains", NativeToolChainRegistryInternal.class).getForPlatform(currentPlatform);
        compile.setToolChain(toolChain);

        // Add a link task
        final LinkSharedLibrary link = project.getTasks().create("linkMain", LinkSharedLibrary.class);
        // TODO - include only object files
        link.source(compile.getObjectFileDirectory().getAsFileTree().matching(new PatternSet().include("**/*.obj", "**/*.o")));
        link.lib(project.getConfigurations().getByName(CppBasePlugin.NATIVE_LINK));
        link.setLinkerArgs(Collections.<String>emptyList());
        // TODO - need to set basename and soname
        String libFileName = ((NativeToolChainInternal) toolChain).select(currentPlatform).getSharedLibraryName("build/lib/" + project.getName());
        link.setOutputFile(project.file(libFileName));
        link.setTargetPlatform(currentPlatform);
        link.setToolChain(toolChain);

        project.getTasks().getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).dependsOn(link);

        // TODO - add lifecycle tasks
        Configuration api = project.getConfigurations().getByName(SwiftBasePlugin.API);

        // TODO - extract common code with C++ plugins
        Configuration apiElements = project.getConfigurations().create("swiftApiElements");
        apiElements.extendsFrom(api);
        apiElements.setCanBeResolved(false);
        apiElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.SWIFT_API));
        // TODO - should be lazy and reflect changes to output file
        apiElements.getOutgoing().artifact(project.file("build/main/objs"), new Action<ConfigurablePublishArtifact>() {
            @Override
            public void execute(ConfigurablePublishArtifact artifact) {
                artifact.builtBy(compile);
            }
        });

        Configuration implementation = project.getConfigurations().getByName(SwiftBasePlugin.IMPLEMENTATION);

        Configuration linkElements = project.getConfigurations().create("linkElements");
        linkElements.extendsFrom(implementation);
        linkElements.setCanBeResolved(false);
        linkElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.NATIVE_LINK));
        // TODO - should be lazy and reflect changes to task output file
        // TODO - Libary on macOS are dylib which could change on other system (like Linux)
        linkElements.getOutgoing().artifact(link.getOutputFile(), new Action<ConfigurablePublishArtifact>() {
            @Override
            public void execute(ConfigurablePublishArtifact artifact) {
                artifact.builtBy(link);
            }
        });

        Configuration runtimeElements = project.getConfigurations().create("runtimeElements");
        runtimeElements.extendsFrom(implementation);
        runtimeElements.setCanBeResolved(false);
        runtimeElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.NATIVE_RUNTIME));
        // TODO - should be lazy and reflect changes to task output file
        runtimeElements.getOutgoing().artifact(link.getOutputFile(), new Action<ConfigurablePublishArtifact>() {
            @Override
            public void execute(ConfigurablePublishArtifact artifact) {
                artifact.builtBy(link);
            }
        });
    }
}
