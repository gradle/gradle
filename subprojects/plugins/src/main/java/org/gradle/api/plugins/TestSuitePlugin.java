/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.plugins;

import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.internal.DefaultTestingExtension;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.plugins.jvm.internal.DefaultJvmTestSuite;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.testing.Test;

public class TestSuitePlugin  implements Plugin<Project> {
    public static final String DEFAULT_TEST_SUITE_NAME = "unitTest";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("org.gradle.java-base");

        ExtensiblePolymorphicDomainObjectContainer<JvmTestSuite> testSuites = project.getObjects().polymorphicDomainObjectContainer(JvmTestSuite.class);
        TestingExtension testing = project.getExtensions().create(TestingExtension.class, "testing", DefaultTestingExtension.class, testSuites);

        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        testSuites.registerFactory(JvmTestSuite.class, name -> project.getObjects().newInstance(DefaultJvmTestSuite.class, name, java.getSourceSets(), project.getConfigurations(), project.getTasks(), project.getDependencies()));

        testSuites.withType(DefaultJvmTestSuite.class).all(testSuite -> {
            testSuite.addTestTarget(java);
        });

        configureTest(project, java, testing);
    }

    private void configureTest(Project project, JavaPluginExtension javaPluginExtension, TestingExtension testing) {
        project.getTasks().withType(Test.class).configureEach(test -> {
            test.getConventionMapping().map("testClassesDirs", () -> sourceSetOf(javaPluginExtension, SourceSet.TEST_SOURCE_SET_NAME).getOutput().getClassesDirs());
            test.getConventionMapping().map("classpath", () -> sourceSetOf(javaPluginExtension, SourceSet.TEST_SOURCE_SET_NAME).getRuntimeClasspath());
            test.getModularity().getInferModulePath().convention(javaPluginExtension.getModularity().getInferModulePath());
        });

        final JvmTestSuite testSuite = testing.getTestSuites().create(DEFAULT_TEST_SUITE_NAME);
        testSuite.getTargets().configureEach(target -> {
            target.getTestTask().configure(test -> {
                test.setDescription("Runs the unit tests.");
                test.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
            });
        });

        project.getTasks().named(JavaBasePlugin.CHECK_TASK_NAME, task -> task.dependsOn(testSuite.getTargets()));
    }

    private SourceSet sourceSetOf(JavaPluginExtension pluginExtension, String mainSourceSetName) {
        return pluginExtension.getSourceSets().getByName(mainSourceSetName);
    }
}
