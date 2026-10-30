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

package org.gradle.api.plugins.internal;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.GeneratedSubclasses;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.jvm.component.internal.JvmSoftwareComponentInternal;
import org.gradle.testing.base.TestSuite;
import org.gradle.testing.base.TestingExtension;
import org.gradle.util.internal.TextUtil;

import java.util.List;

import static org.gradle.api.internal.tasks.JvmConstants.JAVA_MAIN_COMPONENT_NAME;
import static org.gradle.api.internal.tasks.JvmConstants.TEST_TASK_NAME;
import static org.gradle.api.plugins.JvmTestSuitePlugin.DEFAULT_TEST_SUITE_NAME;

/**
 * Utility class intended for use only when the {@link org.gradle.api.plugins.JavaPlugin JavaPlugin} is applied.
 * <p>
 * This class exists to avoid adding these methods to the plugin itself,
 * and thus avoids adding these methods to the public API.
 * <p>
 * All public methods assume the Java plugin has been correctly applied.
 */
public class JavaPluginHelper {
    private JavaPluginHelper() { /* Private to prevent instantiation. */ }

    /**
     * Gets the main Java component.
     *
     * @throws GradleException If the {@code java} component does not exist.
     */
    public static JvmSoftwareComponentInternal getJavaComponent(Project project) {
        return requireJavaComponent(project);
    }

    /**
     * Gets the default test suite.
     *
     * @throws GradleException If the {@code testing} extension, or the default test suite does not exist.
     */
    public static JvmTestSuite getDefaultTestSuite(Project project) {
        TestingExtension testing = requireTestingExtension(project);
        return requireDefaultTestSuite(project, testing);
    }

    private static JvmTestSuite requireDefaultTestSuite(Project project, TestingExtension testing) {
        TestSuite defaultTestSuite = testing.getSuites().findByName(DEFAULT_TEST_SUITE_NAME);
        String jvmTestSuiteType = JvmTestSuite.class.getSimpleName();

        if (defaultTestSuite == null) {
            boolean kotlinBuildScript = isKotlinBuildScript(project);
            String namedSuite = kotlinBuildScript
                ? "testing.suites.named<" + jvmTestSuiteType + ">(\"" + DEFAULT_TEST_SUITE_NAME + "\")"
                : "testing.suites.named('" + DEFAULT_TEST_SUITE_NAME + "', " + jvmTestSuiteType + ")";

            throw new GradleException(
                "The default test suite '" + DEFAULT_TEST_SUITE_NAME + "' is required. " + TextUtil.capitalize(project.getDisplayName()) + " has no test suite named '" + DEFAULT_TEST_SUITE_NAME + "' in 'testing.suites'.",
                null,
                List.of(
                    "Don't remove the '" + DEFAULT_TEST_SUITE_NAME + "' suite from 'testing.suites'. To skip its tests, disable the '" + TEST_TASK_NAME + "' task instead.",
                    "To change the default suite, configure it with " + namedSuite + " instead of replacing it.",
                    buildDocumentationRecommendation("configuring the default test suite", "jvm_test_suite_plugin", "sec:configuring_the_built_in_test_suite")
                )
            );
        }

        if (!(defaultTestSuite instanceof JvmTestSuite)) {
            throw new GradleException(
                "The default test suite '" + DEFAULT_TEST_SUITE_NAME + "' must be a " + jvmTestSuiteType + ", but the suite named '" + DEFAULT_TEST_SUITE_NAME + "' in 'testing.suites' has type '" + GeneratedSubclasses.unpackType(defaultTestSuite).getName() + "'.",
                null,
                List.of(
                    "Rename your custom suite. The name '" + DEFAULT_TEST_SUITE_NAME + "' is reserved for the default " + jvmTestSuiteType + ".",
                    buildDocumentationRecommendation("declaring additional test suites", "jvm_test_suite_plugin", "sec:declare_an_additional_test_suite")
                )
            );
        }

        return (JvmTestSuite) defaultTestSuite;
    }

    private static TestingExtension requireTestingExtension(Project project) {
        TestingExtension testing = project.getExtensions().findByType(TestingExtension.class);
        if (testing == null) {
            boolean kotlinBuildScript = isKotlinBuildScript(project);
            String applyJavaPlugin = kotlinBuildScript ? "plugins { id(\"java\") }" : "plugins { id 'java' }";

            throw new GradleException(
                "The Java plugin must be applied to access the default test suite ('" + DEFAULT_TEST_SUITE_NAME + "').",
                null,
                List.of(
                    "Apply the 'java' plugin with " + applyJavaPlugin + ". It applies the 'jvm-test-suite' plugin and registers the default '" + DEFAULT_TEST_SUITE_NAME + "' suite.",
                    buildDocumentationRecommendation("the Java plugin", "java_plugin", "sec:java_usage")
                )
            );
        }

        return testing;
    }

    private static JvmSoftwareComponentInternal requireJavaComponent(Project project) {
        SoftwareComponent component = project.getComponents().findByName(JAVA_MAIN_COMPONENT_NAME);
        if (component == null) {
            throw new GradleException(
                "The '" + JAVA_MAIN_COMPONENT_NAME + "' component is required. " + TextUtil.capitalize(project.getDisplayName()) + " has no component named '" + JAVA_MAIN_COMPONENT_NAME + "' in 'project.components'.",
                null,
                List.of(
                    "Don't remove the '" + JAVA_MAIN_COMPONENT_NAME + "' component from 'project.components'.",
                    buildDocumentationRecommendation("the '" + JAVA_MAIN_COMPONENT_NAME + "' component", "java_plugin", "sec:java_plugin_publishing")
                )
            );
        }

        if (!(component instanceof JvmSoftwareComponentInternal)) {
            throw new GradleException(
                "The '" + JAVA_MAIN_COMPONENT_NAME + "' component must be created by the Java plugin, but the component named '" + JAVA_MAIN_COMPONENT_NAME + "' in " + project.getDisplayName() + " has type '" + GeneratedSubclasses.unpackType(component).getName() + "'.",
                null,
                List.of(
                    "Don't register a software component named '" + JAVA_MAIN_COMPONENT_NAME + "'. That name is reserved for the Java plugin.",
                    buildDocumentationRecommendation("the '" + JAVA_MAIN_COMPONENT_NAME + "' component", "java_plugin", "sec:java_plugin_publishing")
                )
            );
        }

        return (JvmSoftwareComponentInternal) component;
    }

    private static String buildDocumentationRecommendation(String topic, String id, String section) {
        return new DocumentationRegistry().getDocumentationRecommendationFor("information on " + topic, id, section);
    }

    private static boolean isKotlinBuildScript(Project project) {
        return project.getBuildFile().getName().endsWith(".kts");
    }
}
