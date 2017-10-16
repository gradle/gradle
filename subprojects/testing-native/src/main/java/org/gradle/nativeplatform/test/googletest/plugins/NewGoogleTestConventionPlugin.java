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

package org.gradle.nativeplatform.test.googletest.plugins;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.language.cpp.plugins.CppBasePlugin;
import org.gradle.language.cpp.plugins.CppExecutablePlugin;
import org.gradle.language.cpp.plugins.CppLibraryPlugin;
import org.gradle.language.cpp.tasks.CppCompile;
import org.gradle.nativeplatform.tasks.AbstractLinkTask;
import org.gradle.nativeplatform.tasks.LinkExecutable;
import org.gradle.nativeplatform.test.googletest.GoogleTestTestSuite;
import org.gradle.nativeplatform.test.googletest.internal.DefaultGoogleTestTestSuite;
import org.gradle.nativeplatform.test.tasks.RunTestExecutable;

import javax.inject.Inject;


/**
 * A plugin that sets up the infrastructure for testing native binaries with GoogleTest testing framework.
 * It also adds conventions on top of it.
 *
 * @since 4.4
 */
@Incubating
public class NewGoogleTestConventionPlugin implements Plugin<ProjectInternal> {
    private final ObjectFactory objectFactory;
    private final FileOperations fileOperations;

    @Inject
    public NewGoogleTestConventionPlugin(FileOperations fileOperations, ObjectFactory objectFactory) {
        this.fileOperations = fileOperations;
        this.objectFactory = objectFactory;
    }

    @Override
    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(CppBasePlugin.class);
        final DirectoryProperty buildDirectory = project.getLayout().getBuildDirectory();
        ConfigurationContainer configurations = project.getConfigurations();
        TaskContainer tasks = project.getTasks();

        // TODO - Reuse logic from Cpp Plugin
        // TODO - component name and extension name aren't the same
        // TODO - should use a different directory as the convention?
        // Add the component extension

        GoogleTestTestSuite component = objectFactory.newInstance(DefaultGoogleTestTestSuite.class, "test", objectFactory, fileOperations, configurations);
        // TODO: What should this name be?
        component.getBaseName().set("test");
        project.getExtensions().add(GoogleTestTestSuite.class, "googletest", component);
        project.getComponents().add(component);
        project.getComponents().add(component.getDevelopmentBinary());

        // Configure compile task
        CppCompile compile = (CppCompile) tasks.getByName("compileTestDebugCpp");
        // Add a link task
        final LinkExecutable link = (LinkExecutable) tasks.getByName("linkTestDebug");

        configureTestedComponent(project);

        final RunTestExecutable googleTest = tasks.create("googleTest", RunTestExecutable.class);
        // TODO: Do this lazily
        // googleTest.setExecutable(link.getOutputFile());
        googleTest.dependsOn(link);
        googleTest.doFirst(new Action<Task>() {
            @Override
            public void execute(Task task) {
                googleTest.setExecutable(link.getOutputFile());
            }
        });
        googleTest.setOutputDir(buildDirectory.dir("test-results").get().getAsFile());

        Task check = tasks.getByName("check");
        check.dependsOn(googleTest);
    }

    private void configureTestedComponent(final Project project) {
        Action<Plugin<ProjectInternal>> projectConfiguration = new Action<Plugin<ProjectInternal>>() {
            @Override
            public void execute(Plugin<ProjectInternal> plugin) {
                TaskContainer tasks = project.getTasks();

                CppCompile compileMain = tasks.withType(CppCompile.class).getByName("compileDebugCpp");
                CppCompile compileTest = tasks.withType(CppCompile.class).getByName("compileTestDebugCpp");
                // TODO: This should probably be just the main component's public headers?
                compileTest.includes(compileMain.getIncludes());

                AbstractLinkTask linkTest = tasks.withType(AbstractLinkTask.class).getByName("linkTestDebug");
                linkTest.source(compileMain.getObjectFileDir().getAsFileTree().matching(new PatternSet().include("**/*.obj", "**/*.o")));
            }
        };
        project.getPlugins().withType(CppLibraryPlugin.class, projectConfiguration);
        project.getPlugins().withType(CppExecutablePlugin.class, projectConfiguration);
    }
}
