/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.plugins.jvm.internal.DefaultJvmTestSuite;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.testing.base.TestingExtension;
import org.gradle.testing.base.plugins.TestSuiteBasePlugin;

/**
 * A {@link org.gradle.api.Plugin} that adds extensions for declaring, compiling and running {@link JvmTestSuite}s.
 * <p>
 * This plugin provides conventions for several things:
 * <ul>
 *     <li>All other {@code JvmTestSuite} will use the JUnit Jupiter testing framework unless specified otherwise.</li>
 *     <li>A single test suite target is added to each {@code JvmTestSuite}.</li>
 *
 * </ul>
 *
 * @since 7.3
 * @see <a href="https://docs.gradle.org/current/userguide/jvm_test_suite_plugin.html">Test Suite plugin reference</a>
 */
@Incubating
public abstract class JvmTestSuitePlugin implements Plugin<Project> {
    public static final String DEFAULT_TEST_SUITE_NAME = "test";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(TestSuiteBasePlugin.class);
        project.getPluginManager().apply(JavaBasePlugin.class);

        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);

        TestingExtension testing = project.getExtensions().getByType(TestingExtension.class);
        testing.getSuites().registerBinding(JvmTestSuite.class, DefaultJvmTestSuite.class);

        project.getTasks().withType(Test.class).configureEach(test -> {
            // The test task may have already been created but the test sourceSet may not exist yet.
            // So defer looking up the java extension and sourceSet until the convention mapping is resolved.
            // See https://github.com/gradle/gradle/issues/18622
            test.getConventionMapping().map("testClassesDirs", () -> {
                DeprecationLogger.deprecate("Relying on the convention for Test.testClassesDirs in custom Test tasks")
                    .willBeRemovedInGradle9()
                    .withUpgradeGuideSection(8, "test_task_default_classpath")
                    .nagUser();

                return ((JvmTestSuite) testing.getSuites().findByName(JvmTestSuitePlugin.DEFAULT_TEST_SUITE_NAME)).getSources().getOutput().getClassesDirs();
            });
            test.getConventionMapping().map("classpath", () -> {
                DeprecationLogger.deprecate("Relying on the convention for Test.classpath in custom Test tasks")
                    .willBeRemovedInGradle9()
                    .withUpgradeGuideSection(8, "test_task_default_classpath")
                    .nagUser();
                return ((JvmTestSuite) testing.getSuites().findByName(JvmTestSuitePlugin.DEFAULT_TEST_SUITE_NAME)).getSources().getRuntimeClasspath();
            });
            test.getModularity().getInferModulePath().convention(java.getModularity().getInferModulePath());
        });

        testing.getSuites().withType(JvmTestSuite.class).all(testSuite -> {
            testSuite.getTargets().all(target -> {
                target.getTestTask().configure(test -> {
                    test.getConventionMapping().map("testClassesDirs", () -> testSuite.getSources().getOutput().getClassesDirs());
                    test.getConventionMapping().map("classpath", () -> testSuite.getSources().getRuntimeClasspath());
                });
                target.getBinaryResultsDirectory().convention(target.getTestTask().flatMap(Test::getBinaryResultsDirectory));
            });
        });
    }

}
