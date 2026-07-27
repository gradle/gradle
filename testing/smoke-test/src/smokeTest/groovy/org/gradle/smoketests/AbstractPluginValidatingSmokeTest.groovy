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

package org.gradle.smoketests

import groovy.transform.SelfType
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import org.junit.Assume

@SelfType(AbstractSmokeTest)
abstract class AbstractPluginValidatingSmokeTest extends AbstractSmokeTest implements WithPluginValidation {

    abstract Map<String, Versions> getPluginsToValidate()

    Map<String, String> getExtraPluginsRequiredForValidation() {
        [:]
    }

    Map<String, String> getExtraPluginsRequiredForValidation(String testedPluginId, String version) {
        extraPluginsRequiredForValidation
    }

    String getBuildScriptConfigurationForValidation() {
        ""
    }

    /**
     * Override to opt a plugin into the "configures plugin from subproject" smoke test. Return the
     * DSL snippet that should go into {@code child/build.gradle} — typically a reference to the
     * plugin's project extension (e.g. {@code spotless { }}). Returning {@code null} (default) causes
     * the iteration to be skipped (reported as skipped, not passed) for that plugin/version combination.
     *
     * <p>The purpose is to catch plugins that resolve their DSL extension through the deprecated
     * implicit parent-project property/method lookup when configured from a subproject — the same
     * failure mode as the Develocity 3.x case.
     */
    String getSubprojectExtensionAccess(String testedPluginId, String version) {
        null
    }

    /**
     * Override to declare deprecation warnings that the "configures plugin from subproject" test
     * should expect for a given plugin/version. The returned warnings are passed to
     * {@link SmokeTestGradleRunner#expectLegacyDeprecationWarning(String)} — appropriate for
     * third-party plugin issues that are out of our control.
     *
     * <p>Use {@link #parentMethodInvocationDeprecation(String)} or
     * {@link #parentPropertyAccessDeprecation(String)} to build the parent-walk deprecation
     * message for a plugin that resolves its DSL extension from subprojects.
     */
    List<String> getSubprojectExtensionDeprecations(String testedPluginId, String version) {
        []
    }

    protected static String parentMethodInvocationDeprecation(String methodName) {
        "Implicit lookup of methods in parent projects has been deprecated. " +
            "This will fail with an error in Gradle 10. " +
            "Method '$methodName' was not declared in project ':child' and was resolved from root project 'test'. " +
            "This lookup was initiated by a dynamic invocation in the build script. " +
            "Consult the upgrading guide for further information: " +
            "${DEPRECATED_PARENT_PROPERTY_ACCESS_URL}"
    }

    protected static String parentPropertyAccessDeprecation(String propertyName) {
        "Implicit lookup of properties in parent projects has been deprecated. " +
            "This will fail with an error in Gradle 10. " +
            "Property '$propertyName' was not declared in project ':child' and was resolved from root project 'test'. " +
            "This lookup was initiated by a dynamic invocation in the build script. " +
            "Consult the upgrading guide for further information: " +
            "${DEPRECATED_PARENT_PROPERTY_ACCESS_URL}"
    }

    private List<List<String>> iterations() {
        List<List<String>> result = []
        pluginsToValidate.each { id, versions ->
            if (versions instanceof Versions) {
                versions.each { v ->
                    result << [id, v]
                }
            } else {
                result << [id, versions as String]
            }
        }
        result
    }

    def "performs static analysis of plugin #id version #version"(String id, String version) {
        def extraPluginsBlock = getExtraPluginsRequiredForValidation(id, version).collect { pluginId, pluginVersion ->
            "                id '$pluginId'" + (pluginVersion ? "version '$pluginVersion'" : "")
        }.join('\n')

        given:
        buildFile << """
            plugins {
                $extraPluginsBlock
                id '$id'${version ? " version '$version'" : ""}
            }

            $buildScriptConfigurationForValidation
        """
        configureValidation(id, version)

        expect:
        performValidation(version)

        where:
        iterations << iterations()
        (id, version) = iterations
    }

    @Requires(value = TestExecutionPreconditions.NotIsolatedProjects, reason = "Under Isolated Projects the parent walk is disabled, so the plugin's extension is not reachable from a subproject — the scenario this test exercises (firing the parent-walk deprecation) is Vintage-specific")
    def "configures plugin #id version #version from subproject"(String id, String version) {
        def childConfig = getSubprojectExtensionAccess(id, version)
        Assume.assumeTrue("Plugin $id:$version does not opt into subproject extension access", childConfig != null)

        given:
        def extraPluginsBlock = getExtraPluginsRequiredForValidation(id, version).collect { pluginId, pluginVersion ->
            "                id '$pluginId'" + (pluginVersion ? "version '$pluginVersion'" : "")
        }.join('\n')
        buildFile << """
            plugins {
                $extraPluginsBlock
                id '$id'${version ? " version '$version'" : ""}
            }

            $buildScriptConfigurationForValidation
        """
        settingsFile << """
            rootProject.name = "test"
            include("child")
        """
        file("child/build.gradle") << childConfig
        SmokeTestGradleRunner testRunner = runner("--stacktrace", "help")
        getSubprojectExtensionDeprecations(id, version).each { testRunner.expectLegacyDeprecationWarning(it) }

        expect:
        testRunner.build() != null

        where:
        iterations << iterations()
        (id, version) = iterations
    }

    void configureValidation(String testedPluginId, String version) {
        allPlugins.alwaysPasses = true
    }

    void performValidation(String version) {
        allPlugins.performValidation(getValidationExtraParameters(version))
    }

    protected List<String> getValidationExtraParameters(String version) {
        return []
    }
}
