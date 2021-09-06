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
import org.gradle.api.Incubating;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.internal.DefaultTestingExtension;
import org.gradle.api.plugins.jvm.JunitPlatformTestingFramework;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.plugins.jvm.JvmTestingFramework;
import org.gradle.api.plugins.jvm.internal.DefaultJvmTestSuite;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.testing.Test;

/**
 * <p>A {@link org.gradle.api.Plugin} which allows for defining, compiling and running groups of Java tests against (potentially)
 * various different target environments.</p>
 *
 * @see <a href="https://docs.gradle.org/current/userguide/test_suite_plugin.html">Test Suite plugin reference</a>
 *
 * @since 7.3
 */
@Incubating
public class TestSuitePlugin  implements Plugin<Project> {
    public static final String DEFAULT_TEST_SUITE_NAME = "unitTest";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("org.gradle.java-base");

        ExtensiblePolymorphicDomainObjectContainer<JvmTestSuite> testSuites = project.getObjects().polymorphicDomainObjectContainer(JvmTestSuite.class);
        TestingExtension testing = project.getExtensions().create(TestingExtension.class, "testing", DefaultTestingExtension.class, testSuites);

        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        testSuites.registerFactory(JvmTestSuite.class, name -> project.getObjects().newInstance(DefaultJvmTestSuite.class, name, project, java));

        // TODO: Deprecate this behavior?
        // Why would any Test task created need to use the test source set's classes?
        project.getTasks().withType(Test.class).configureEach(test -> {
            SourceSet testSourceSet = java.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME);
            test.getConventionMapping().map("testClassesDirs", () -> testSourceSet.getOutput().getClassesDirs());
            test.getConventionMapping().map("classpath", () -> testSourceSet.getRuntimeClasspath());
            test.getModularity().getInferModulePath().convention(java.getModularity().getInferModulePath());
        });

        testSuites.withType(DefaultJvmTestSuite.class).all(testSuite -> {
            testSuite.addTestTarget(java);
            JvmTestingFramework testingFramework = project.getObjects().newInstance(JunitPlatformTestingFramework.class);
            testSuite.getTestingFramework().convention(testingFramework);
            testingFramework.getVersion().convention("5.7.1");

            testSuite.getTargets().all(target -> {
                target.getTestingFramework().convention(testSuite.getTestingFramework());

                target.getTestTask().configure(test -> {
                    test.getConventionMapping().map("testClassesDirs", () -> testSuite.getSources().getOutput().getClassesDirs());
                    test.getConventionMapping().map("classpath", () -> testSuite.getSources().getRuntimeClasspath());
                });
            });
        });

        configureBuiltInTest(project, java, testing);
    }

    private void configureBuiltInTest(Project project, JavaPluginExtension javaPluginExtension, TestingExtension testing) {
        final NamedDomainObjectProvider<JvmTestSuite> testSuite = testing.getSuites().register(DEFAULT_TEST_SUITE_NAME, JvmTestSuite::useJUnit);
        // Force the realization of this test suite, targets and task
        testSuite.get();
        project.getTasks().named(JavaBasePlugin.CHECK_TASK_NAME, task -> task.dependsOn(testSuite));
    }
}
