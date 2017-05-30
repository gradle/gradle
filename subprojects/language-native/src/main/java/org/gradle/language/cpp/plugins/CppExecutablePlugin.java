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

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.cpp.tasks.CppCompile;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.nativeplatform.tasks.LinkExecutable;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal;
import org.gradle.nativeplatform.toolchain.internal.plugins.StandardToolChainsPlugin;

import java.util.Collections;

/**
 * A plugin that produces a native executable from C++ source.
 *
 * @since 4.1
 */
@Incubating
public class CppExecutablePlugin implements Plugin<ProjectInternal> {
    @Override
    public void apply(ProjectInternal project) {
        project.getPluginManager().apply(LifecycleBasePlugin.class);
        project.getPluginManager().apply(StandardToolChainsPlugin.class);

        // Add a compile task
        CppCompile compile = project.getTasks().create("compileCpp", CppCompile.class);

        compile.includes("src/main/headers");

        ConfigurableFileTree sourceTree = project.fileTree("src/main/cpp");
        sourceTree.include("**/*.cpp");
        sourceTree.include("**/*.c++");
        compile.source(sourceTree);

        compile.setCompilerArgs(Collections.<String>emptyList());
        compile.setMacros(Collections.<String, String>emptyMap());

        // TODO - should reflect changes to build directory
        compile.setObjectFileDir(project.file("build/main/objs"));

        DefaultNativePlatform currentPlatform = new DefaultNativePlatform("current");
        compile.setTargetPlatform(currentPlatform);

        // TODO - make this lazy
        NativeToolChain toolChain = project.getModelRegistry().realize("toolChains", NativeToolChainRegistryInternal.class).getForPlatform(currentPlatform);
        compile.setToolChain(toolChain);

        // Add a link task
        LinkExecutable link = project.getTasks().create("linkMain", LinkExecutable.class);
        // TODO - include only object files
        link.source(compile.getOutputs().getFiles().getAsFileTree());
        link.setLinkerArgs(Collections.<String>emptyList());
        // TODO - should reflect changes to build directory
        // TODO - need to set basename
        link.setOutputFile(project.file("build/exe/main"));
        link.setTargetPlatform(currentPlatform);
        link.setToolChain(toolChain);

        project.getTasks().getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).dependsOn(link);

        // TODO - add lifecycle tasks
    }
}
