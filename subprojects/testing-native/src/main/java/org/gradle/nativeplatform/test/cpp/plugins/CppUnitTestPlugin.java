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

package org.gradle.nativeplatform.test.cpp.plugins;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.cpp.CppComponent;
import org.gradle.language.cpp.plugins.CppBasePlugin;
import org.gradle.language.cpp.plugins.CppExecutablePlugin;
import org.gradle.language.cpp.plugins.CppLibraryPlugin;
import org.gradle.nativeplatform.tasks.AbstractLinkTask;
import org.gradle.nativeplatform.tasks.InstallExecutable;
import org.gradle.nativeplatform.test.cpp.CppTestSuite;
import org.gradle.nativeplatform.test.cpp.internal.DefaultCppTestSuite;
import org.gradle.nativeplatform.test.tasks.RunTestExecutable;
import org.gradle.testing.base.plugins.TestingBasePlugin;

import javax.inject.Inject;


/**
 * A plugin that sets up the infrastructure for testing C++ binaries using a simple test executable.
 *
 * Gradle will create a {@link RunTestExecutable} task that relies on the exit code of the binary.
 *
 * @since 4.4
 */
@Incubating
public class CppUnitTestPlugin implements Plugin<ProjectInternal> {
    private final ObjectFactory objectFactory;
    private final FileOperations fileOperations;

    @Inject
    public CppUnitTestPlugin(FileOperations fileOperations, ObjectFactory objectFactory) {
        this.fileOperations = fileOperations;
        this.objectFactory = objectFactory;
    }

    @Override
    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(CppBasePlugin.class);
        project.getPluginManager().apply(TestingBasePlugin.class);

        final ConfigurationContainer configurations = project.getConfigurations();

        final CppTestSuite testComponent = objectFactory.newInstance(DefaultCppTestSuite.class, "unitTest", project.getLayout(), objectFactory, fileOperations, configurations);
        // Register components created for the test Component and test binaries
        project.getComponents().add(testComponent);
        project.getComponents().add(testComponent.getTestExecutable());
        project.getExtensions().add(CppTestSuite.class, "unitTest", testComponent);

        Action<Plugin<ProjectInternal>> projectConfiguration = new Action<Plugin<ProjectInternal>>() {
            @Override
            public void execute(Plugin<ProjectInternal> plugin) {
                final TaskContainer tasks = project.getTasks();
                CppComponent mainComponent = project.getComponents().withType(CppComponent.class).findByName("main");
                ((DefaultCppTestSuite)testComponent).getTestedComponent().set(mainComponent);

                // TODO: This should be modeled as a kind of dependency vs wiring tasks together directly.
                AbstractLinkTask linkTest = tasks.withType(AbstractLinkTask.class).getByName("linkUnitTest");
                linkTest.source(mainComponent.getDevelopmentBinary().getObjects());

                // TODO: Replace with native test task
                final RunTestExecutable testTask = tasks.create("runUnitTest", RunTestExecutable.class, new Action<RunTestExecutable>() {
                    @Override
                    public void execute(RunTestExecutable testTask) {
                        testTask.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
                        testTask.setDescription("Executes C++ unit tests.");

                        final InstallExecutable installTask = (InstallExecutable) tasks.getByName("installUnitTest");
                        testTask.setExecutable(installTask.getRunScript());
                        testTask.dependsOn(testComponent.getTestExecutable().getInstallDirectory());
                        // TODO: Honor changes to build directory
                        testTask.setOutputDir(project.getLayout().getBuildDirectory().dir("test-results/unitTest").get().getAsFile());
                    }
                });

                tasks.getByName("check").dependsOn(testTask);

            }
        };

        project.getPlugins().withType(CppLibraryPlugin.class, projectConfiguration);
        // TODO: We will get symbol conflicts with executables since they already have a main()
        project.getPlugins().withType(CppExecutablePlugin.class, projectConfiguration);
    }
}
