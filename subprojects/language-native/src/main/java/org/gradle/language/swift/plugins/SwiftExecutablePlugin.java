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
import org.gradle.api.Task;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.DirectoryVar;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.cpp.plugins.CppBasePlugin;
import org.gradle.language.swift.internal.DefaultSwiftComponent;
import org.gradle.language.swift.model.SwiftComponent;
import org.gradle.language.swift.tasks.SwiftCompile;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.nativeplatform.tasks.InstallExecutable;
import org.gradle.nativeplatform.tasks.LinkExecutable;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;

import javax.inject.Inject;
import java.util.Collections;

/**
 * <p>A plugin that produces an executable from Swift source.</p>
 *
 * <p>Adds compile, link and install tasks to build the executable. Defaults to looking for source files in `src/main/swift`.</p>
 *
 * <p>Adds a {@link SwiftComponent} extension to the project to allow configuration of the executable.</p>
 *
 * @since 4.1
 */
@Incubating
public class SwiftExecutablePlugin implements Plugin<ProjectInternal> {
    private final FileOperations fileOperations;

    @Inject
    public SwiftExecutablePlugin(FileOperations fileOperations) {
        this.fileOperations = fileOperations;
    }

    @Override
    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(SwiftBasePlugin.class);

        DirectoryVar buildDirectory = project.getLayout().getBuildDirectory();
        ConfigurationContainer configurations = project.getConfigurations();
        TaskContainer tasks = project.getTasks();

        // Add the component extension
        SwiftComponent component = project.getExtensions().create(SwiftComponent.class, "executable", DefaultSwiftComponent.class, fileOperations);

        // Add a compile task
        SwiftCompile compile = tasks.create("compileSwift", SwiftCompile.class);

        compile.includes(configurations.getByName(SwiftBasePlugin.SWIFT_IMPORT_PATH));

        FileCollection sourceFiles = component.getSwiftSource();
        compile.source(sourceFiles);

        compile.setCompilerArgs(Lists.newArrayList("-g"));
        compile.setMacros(Collections.<String, String>emptyMap());
        compile.setModuleName(project.getName());

        compile.setObjectFileDir(buildDirectory.dir("main/objs"));

        DefaultNativePlatform currentPlatform = new DefaultNativePlatform("current");
        compile.setTargetPlatform(currentPlatform);

        // TODO - make this lazy
        NativeToolChain toolChain = project.getModelRegistry().realize("toolChains", NativeToolChainRegistryInternal.class).getForPlatform(currentPlatform);
        compile.setToolChain(toolChain);

        // Add a link task
        LinkExecutable link = tasks.create("linkMain", LinkExecutable.class);
        // TODO - need to set basename
        link.source(compile.getObjectFileDirectory().getAsFileTree().matching(new PatternSet().include("**/*.obj", "**/*.o")));
        link.lib(configurations.getByName(CppBasePlugin.NATIVE_LINK));
        link.setLinkerArgs(Collections.<String>emptyList());
        PlatformToolProvider toolProvider = ((NativeToolChainInternal) toolChain).select(currentPlatform);
        Provider<RegularFile> exeLocation = buildDirectory.file(toolProvider.getExecutableName("exe/" + project.getName()));
        link.setOutputFile(exeLocation);
        link.setTargetPlatform(currentPlatform);
        link.setToolChain(toolChain);

        // Add an install task
        final InstallExecutable install = tasks.create("installMain", InstallExecutable.class);
        install.setPlatform(currentPlatform);
        install.setToolChain(toolChain);
        // TODO - need to set basename
        install.setDestinationDir(buildDirectory.dir("install/" + project.getName()));
        install.setExecutable(link.getBinaryFile());
        // TODO - infer this
        install.onlyIf(new Spec<Task>() {
            @Override
            public boolean isSatisfiedBy(Task element) {
                return install.getExecutable().exists();
            }
        });
        install.lib(configurations.getByName(CppBasePlugin.NATIVE_RUNTIME));

        tasks.getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).dependsOn(install);
    }
}
