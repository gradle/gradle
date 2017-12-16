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
package org.gradle.testing.junit5.plugins;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.testing.junit5.tasks.JUnitPlatformTest;

import java.io.File;
import java.util.concurrent.Callable;

/**
 * This plugin adds support for execution of tests using the JUnit Platform for projects using the {@code java} plugin.
 */
public class JUnitPlatformPlugin implements Plugin<Project> {
    private static final String TEST_TASK_NAME = "junit";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("java");
        JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
        configureTest(project, javaConvention);
    }

    private void configureTest(final Project project, final JavaPluginConvention pluginConvention) {
        project.getTasks().withType(JUnitPlatformTest.class, new Action<JUnitPlatformTest>() {
            @Override
            public void execute(JUnitPlatformTest test) {
                DslObject htmlReport = new DslObject(test.getReports().getHtml());
                DslObject xmlReport = new DslObject(test.getReports().getJunitXml());

                xmlReport.getConventionMapping().map("destination", new Callable<Object>() {
                    public Object call() throws Exception {
                        return new File(pluginConvention.getTestResultsDir(), test.getName());
                    }
                });
                htmlReport.getConventionMapping().map("destination", new Callable<Object>() {
                    public Object call() throws Exception {
                        return new File(pluginConvention.getTestReportDir(), test.getName());
                    }
                });
                test.getConventionMapping().map("binResultsDir", new Callable<Object>() {
                    public Object call() throws Exception {
                        return new File(pluginConvention.getTestResultsDir(), test.getName() + "/binary");
                    }
                });
                test.workingDir(project.getProjectDir());

                SourceSet sourceSet = pluginConvention.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME);
                test.classpathRoots.from(sourceSet.getOutput());
                test.classpath.from(sourceSet.getRuntimeClasspath());
                test.dependsOn(sourceSet.getClassesTaskName());
            }
        });
        JUnitPlatformTest test = project.getTasks().create(TEST_TASK_NAME, JUnitPlatformTest.class);
        project.getTasks().getByName(JavaBasePlugin.CHECK_TASK_NAME).dependsOn(test);
        test.setDescription("Runs the tests with JUnit Platform.");
        test.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
    }
}
