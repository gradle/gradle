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
import org.gradle.api.file.Directory;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.swift.SwiftApplication;
import org.gradle.language.swift.SwiftBinary;
import org.gradle.language.swift.SwiftExecutable;
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
    private final FileOperations fileOperations;

    /**
     * Injects a {@link FileOperations} instance.
     *
     * @since 4.2
     */
    @Inject
    public SwiftApplicationPlugin(FileOperations fileOperations) {
        this.fileOperations = fileOperations;
    }

    @Override
    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(SwiftBasePlugin.class);

        final ConfigurationContainer configurations = project.getConfigurations();

        // Add the component extension
        final DefaultSwiftApplication application = (DefaultSwiftApplication) project.getExtensions().create(SwiftApplication.class, "application", DefaultSwiftApplication.class, "main", project.getLayout(), project.getObjects(), fileOperations, configurations);
        project.getComponents().add(application);
        application.getBinaries().whenElementKnown(new Action<SwiftBinary>() {
            @Override
            public void execute(SwiftBinary binary) {
                project.getComponents().add(binary);
            }
        });

        // Setup component
        application.getModule().set(GUtil.toCamelCase(project.getName()));

        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project) {
                TaskContainer tasks = project.getTasks();
                ObjectFactory objectFactory = project.getObjects();

                SwiftExecutable debugExecutable = application.createExecutable("debug", true, false, true);
                SwiftExecutable releaseExecutable = application.createExecutable("release", true, true, false);

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

                // Configure the binaries
                application.getBinaries().realizeNow();

                // Assemble builds the debug installation
                tasks.getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).dependsOn(application.getDevelopmentBinary().map(new Transformer<Provider<Directory>, SwiftExecutable>() {
                    @Override
                    public Provider<Directory> transform(SwiftExecutable binary) {
                        return binary.getInstallDirectory();
                    }
                }));
            }
        });
    }
}
