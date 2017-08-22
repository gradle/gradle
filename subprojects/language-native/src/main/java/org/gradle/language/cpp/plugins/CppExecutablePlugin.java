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
import org.gradle.api.Task;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.DirectoryVar;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.cpp.CppComponent;
import org.gradle.language.cpp.internal.DefaultCppComponent;
import org.gradle.language.cpp.tasks.CppCompile;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.nativeplatform.tasks.InstallExecutable;
import org.gradle.nativeplatform.tasks.LinkExecutable;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;

import javax.inject.Inject;
import java.util.Collections;
import java.util.concurrent.Callable;

/**
 * <p>A plugin that produces a native executable from C++ source.</p>
 *
 * <p>Assumes the source files are located in `src/main/cpp` and header files are located in `src/main/headers`.</p>
 *
 * <p>Adds a {@link CppComponent} extension to the project to allow configuration of the executable.</p>
 *
 * @since 4.1
 */
@Incubating
public class CppExecutablePlugin implements Plugin<ProjectInternal> {
    private final FileOperations fileOperations;

    @Inject
    public CppExecutablePlugin(FileOperations fileOperations) {
        this.fileOperations = fileOperations;
    }

    @Override
    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(CppBasePlugin.class);

        DirectoryVar buildDirectory = project.getLayout().getBuildDirectory();
        ConfigurationContainer configurations = project.getConfigurations();
        ProviderFactory providers = project.getProviders();
        TaskContainer tasks = project.getTasks();

        // Add the component extension
        final CppComponent component = project.getExtensions().create(CppComponent.class, "executable", DefaultCppComponent.class, fileOperations, providers);
        component.getBaseName().set(project.getName());

        // Wire in dependencies
        component.getCompileIncludePath().from(configurations.getByName(CppBasePlugin.CPP_INCLUDE_PATH));

        // Add a compile task
        CppCompile compile = tasks.create("compileCpp", CppCompile.class);
        compile.includes(component.getCompileIncludePath());
        compile.source(component.getCppSource());

        compile.setCompilerArgs(Collections.<String>emptyList());
        compile.setMacros(Collections.<String, String>emptyMap());
        compile.setObjectFileDir(buildDirectory.dir("main/objs"));

        DefaultNativePlatform currentPlatform = new DefaultNativePlatform("current");
        compile.setTargetPlatform(currentPlatform);

        // TODO - make this lazy
        NativeToolChain toolChain = project.getModelRegistry().realize("toolChains", NativeToolChainRegistryInternal.class).getForPlatform(currentPlatform);
        compile.setToolChain(toolChain);

        // Add a link task
        LinkExecutable link = tasks.create("linkMain", LinkExecutable.class);
        link.source(compile.getObjectFileDirectory().getAsFileTree().matching(new PatternSet().include("**/*.obj", "**/*.o")));
        link.lib(configurations.getByName(CppBasePlugin.NATIVE_LINK));
        link.setLinkerArgs(Collections.<String>emptyList());
        final PlatformToolProvider toolProvider = ((NativeToolChainInternal) toolChain).select(currentPlatform);
        link.setOutputFile(buildDirectory.file(providers.provider(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return toolProvider.getExecutableName("exe/" + component.getBaseName().get());
            }
        })));
        link.setTargetPlatform(currentPlatform);
        link.setToolChain(toolChain);

        // Add an install task
        final InstallExecutable install = tasks.create("installMain", InstallExecutable.class);
        install.setPlatform(currentPlatform);
        install.setToolChain(toolChain);
        install.setDestinationDir(buildDirectory.dir(providers.provider(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return "install/" + component.getBaseName().get();
            }
        })));
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

        // TODO - add lifecycle tasks
    }
}
