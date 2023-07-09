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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.TestSuiteName;
import org.gradle.api.attributes.TestSuiteTargetName;
import org.gradle.api.attributes.TestSuiteType;
import org.gradle.api.attributes.VerificationType;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.internal.JavaPluginHelper;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.plugins.jvm.JvmTestSuiteTarget;
import org.gradle.api.plugins.jvm.internal.DefaultJvmTestSuite;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.testing.AbstractTestTask;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.testing.base.TestSuite;
import org.gradle.testing.base.TestingExtension;
import org.gradle.util.internal.TextUtil;

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
    public static final String DEFAULT_TEST_SUITE_NAME = SourceSet.TEST_SOURCE_SET_NAME;
    private static final String TEST_RESULTS_ELEMENTS_VARIANT_PREFIX = "testResultsElementsFor";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("org.gradle.test-suite-base");
        project.getPluginManager().apply("org.gradle.java-base");
        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        TestingExtension testing = project.getExtensions().getByType(TestingExtension.class);
        ExtensiblePolymorphicDomainObjectContainer<TestSuite> testSuites = testing.getSuites();
        testSuites.registerBinding(JvmTestSuite.class, DefaultJvmTestSuite.class);

        project.getTasks().withType(Test.class).configureEach(test -> {
            // The test task may have already been created but the test sourceSet may not exist yet.
            // So defer looking up the java extension and sourceSet until the convention mapping is resolved.
            // See https://github.com/gradle/gradle/issues/18622
            test.getConventionMapping().map("testClassesDirs", () -> {
                DeprecationLogger.deprecate("Relying on the convention for Test.testClassesDirs in custom Test tasks")
                    .willBeRemovedInGradle9()
                    .withUpgradeGuideSection(8, "test_task_default_classpath")
                    .nagUser();
                return JavaPluginHelper.getDefaultTestSuite(project).getSources().getOutput().getClassesDirs();
            });
            test.getConventionMapping().map("classpath", () -> {
                DeprecationLogger.deprecate("Relying on the convention for Test.classpath in custom Test tasks")
                    .willBeRemovedInGradle9()
                    .withUpgradeGuideSection(8, "test_task_default_classpath")
                    .nagUser();
                return JavaPluginHelper.getDefaultTestSuite(project).getSources().getRuntimeClasspath();
            });
            test.getModularity().getInferModulePath().convention(java.getModularity().getInferModulePath());
        });

        testSuites.withType(JvmTestSuite.class).all(testSuite -> {
            testSuite.getTestType().convention(getDefaultTestType(testSuite));
            testSuite.getTargets().all(target -> {
                target.getTestTask().configure(test -> {
                    test.getConventionMapping().map("testClassesDirs", () -> testSuite.getSources().getOutput().getClassesDirs());
                    test.getConventionMapping().map("classpath", () -> testSuite.getSources().getRuntimeClasspath());
                });
            });
        });

        configureTestDataElementsVariants((ProjectInternal) project);
    }

    private String getDefaultTestType(JvmTestSuite testSuite) {
        return DEFAULT_TEST_SUITE_NAME.equals(testSuite.getName()) ? TestSuiteType.UNIT_TEST : TextUtil.camelToKebabCase(testSuite.getName());
    }

    private void configureTestDataElementsVariants(ProjectInternal project) {
        final TestingExtension testing = project.getExtensions().getByType(TestingExtension.class);
        final ExtensiblePolymorphicDomainObjectContainer<TestSuite> testSuites = testing.getSuites();

        testSuites.withType(JvmTestSuite.class).configureEach(suite -> {
            suite.getTargets().configureEach(target -> {
                addTestResultsVariant(project, suite, target);
            });
        });
    }

    private void addTestResultsVariant(ProjectInternal project, JvmTestSuite suite, JvmTestSuiteTarget target) {
        project.getConfigurations().consumable(TEST_RESULTS_ELEMENTS_VARIANT_PREFIX + StringUtils.capitalize(target.getName()), variant -> {
            variant.setDescription("Directory containing binary results of running tests for the " + suite.getName() + " Test Suite's " + target.getName() + " target.");
            variant.setVisible(false);

            final ObjectFactory objects = project.getObjects();
            variant.attributes(attributes -> {
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.VERIFICATION));
                attributes.attribute(TestSuiteName.TEST_SUITE_NAME_ATTRIBUTE, objects.named(TestSuiteName.class, suite.getName()));
                attributes.attribute(TestSuiteTargetName.TEST_SUITE_TARGET_NAME_ATTRIBUTE, objects.named(TestSuiteTargetName.class, target.getName()));
                attributes.attributeProvider(TestSuiteType.TEST_SUITE_TYPE_ATTRIBUTE, suite.getTestType().map(tt -> project.getObjects().named(TestSuiteType.class, tt)));
                attributes.attribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE, objects.named(VerificationType.class, VerificationType.TEST_RESULTS));
            });

            variant.getOutgoing().artifact(
                target.getTestTask().flatMap(AbstractTestTask::getBinaryResultsDirectory),
                artifact -> artifact.setType(ArtifactTypeDefinition.DIRECTORY_TYPE)
            );
        });
    }

}
