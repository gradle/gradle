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
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.language.internal.NativeComponentFactory;
import org.gradle.language.nativeplatform.internal.toolchains.ToolChainSelector;
import org.gradle.language.swift.SwiftApplication;
import org.gradle.language.swift.SwiftExecutable;
import org.gradle.language.swift.SwiftPlatform;
import org.gradle.language.swift.internal.DefaultSwiftApplication;
import org.gradle.language.swift.tasks.SwiftCompile;
import org.gradle.util.GUtil;

import javax.inject.Inject;

import static org.gradle.language.cpp.CppBinary.DEBUGGABLE_ATTRIBUTE;
import static org.gradle.language.cpp.CppBinary.OPTIMIZED_ATTRIBUTE;

/**
 * <p>A plugin that produces an executable from Swift source.</p>
 *
 * <p>Adds compile, link and install tasks to build the executable. Defaults to looking for source files in `src/main/swift`.</p>
 *
 * <p>Adds a {@link SwiftApplication} extension to the project to allow configuration of the executable.</p>
 *
 * @since 4.5
 */
@Incubating
public class SwiftApplicationPlugin implements Plugin<ProjectInternal> {
    private final NativeComponentFactory componentFactory;
    private final ToolChainSelector toolChainSelector;

    /**
     * Injects a {@link FileOperations} instance.
     *
     * @since 4.2
     */
    @Inject
    public SwiftApplicationPlugin(NativeComponentFactory componentFactory, ToolChainSelector toolChainSelector) {
        this.componentFactory = componentFactory;
        this.toolChainSelector = toolChainSelector;
    }

    @Override
    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(SwiftBasePlugin.class);

        final ConfigurationContainer configurations = project.getConfigurations();

        // Add the application and extension
        final DefaultSwiftApplication application = componentFactory.newInstance(SwiftApplication.class, DefaultSwiftApplication.class, "main");
        project.getExtensions().add(SwiftApplication.class, "application", application);
        project.getComponents().add(application);

        // Setup component
        application.getModule().set(GUtil.toCamelCase(project.getName()));

        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project) {
                ObjectFactory objectFactory = project.getObjects();

                ToolChainSelector.Result<SwiftPlatform> result = toolChainSelector.select(SwiftPlatform.class);

                SwiftExecutable debugExecutable = application.addExecutable("debug", true, false, true, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());
                SwiftExecutable releaseExecutable = application.addExecutable("release", true, true, false, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());

                // Add outgoing APIs
                SwiftCompile compileDebug = debugExecutable.getCompileTask().get();
                SwiftCompile compileRelease = releaseExecutable.getCompileTask().get();

                Configuration implementation = application.getImplementationDependencies();

                Configuration debugApiElements = configurations.maybeCreate("debugSwiftApiElements");
                debugApiElements.extendsFrom(implementation);
                debugApiElements.setCanBeResolved(false);
                debugApiElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.SWIFT_API));
                debugApiElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, debugExecutable.isDebuggable());
                debugApiElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, debugExecutable.isOptimized());
                debugApiElements.getOutgoing().artifact(compileDebug.getModuleFile());

                Configuration releaseApiElements = configurations.maybeCreate("releaseSwiftApiElements");
                releaseApiElements.extendsFrom(implementation);
                releaseApiElements.setCanBeResolved(false);
                releaseApiElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.SWIFT_API));
                releaseApiElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, releaseExecutable.isDebuggable());
                releaseApiElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, releaseExecutable.isOptimized());
                releaseApiElements.getOutgoing().artifact(compileRelease.getModuleFile());

                // Use the debug variant as the development variant
                application.getDevelopmentBinary().set(debugExecutable);

                // Configure the binaries
                application.getBinaries().realizeNow();
            }
        });
    }
}
