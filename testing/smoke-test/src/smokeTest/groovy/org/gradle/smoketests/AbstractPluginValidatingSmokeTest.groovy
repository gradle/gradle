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
     * Override to opt a plugin into the "configure from subproject" smoke test. Return the
     * content that should go into {@code child/build.gradle} — typically a DSL snippet that
     * references the plugin's project extension (e.g. {@code spotless { }}). Return
     * {@code null} to skip the test for this plugin/version.
     *
     * <p>The purpose is to catch plugins that rely on the deprecated implicit parent-project
     * property/method lookup to resolve their DSL extension from subprojects — the same
     * failure mode as the Develocity 3.x case. See the
     * deprecated_accessing_parent_project_properties spec for context.
     */
    String getChildProjectConfiguration(String testedPluginId, String version) {
        null
    }

    /**
     * Override to declare deprecation warnings that the "configure from subproject" test
     * should expect for a given plugin/version. The returned warnings are passed to
     * {@link SmokeTestGradleRunner#expectLegacyDeprecationWarning(String)} — appropriate
     * for third-party plugin issues that are out of our control.
     *
     * <p>Use {@link #parentMethodInvocationDeprecation(String)} to build the parent-walking
     * deprecation message for a plugin that resolves its DSL extension from subprojects.
     */
    List<String> getChildProjectExpectedDeprecations(String testedPluginId, String version) {
        []
    }

    protected static String parentMethodInvocationDeprecation(String methodName) {
        "Dynamically invoking parent method from a child project has been deprecated. " +
            "This will fail with an error in Gradle 10. " +
            "Cannot dynamically invoke method '$methodName' on root project 'test' from project ':child'. " +
            "Consult the upgrading guide for further information: " +
            "${ DEPRECATED_PARENT_PROPERTY_ACCESS_URL}"
    }

    protected static String parentPropertyAccessDeprecation(String propertyName) {
        "Accessing a property from a parent project has been deprecated. " +
            "This will fail with an error in Gradle 10. " +
            "Property '$propertyName' was not found in project ':child' and was dynamically resolved from root project 'test'. " +
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
        setupMinimalChildProject()
        configureValidation(id, version)

        expect:
        performValidation(version)

        where:
        iterations << iterations()
        (id, version) = iterations
    }

    def "configures plugin #id version #version from subproject"(String id, String version) {
        def childConfig = getChildProjectConfiguration(id, version)

        given:
        def extraPluginsBlock = getExtraPluginsRequiredForValidation(id, version).collect { pluginId, pluginVersion ->
            "                id '$pluginId'" + (pluginVersion ? "version '$pluginVersion'" : "")
        }.join('\n')
        SmokeTestGradleRunner testRunner = null
        if (childConfig != null) {
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
            testRunner = runner("help")
            getChildProjectExpectedDeprecations(id, version).each { testRunner.expectLegacyDeprecationWarning(it) }
        }

        expect:
        childConfig == null || testRunner.build() != null

        where:
        iterations << iterations()
        (id, version) = iterations
    }

    /**
     * Adds a minimal empty child project so that validation exercises the parent/child
     * project scenario in addition to the root-only case. This catches issues where a
     * plugin's dynamic property/method resolution on the root project walks into child
     * projects (or vice versa) unintentionally.
     *
     * The child is intentionally empty — we're validating that the plugin applied to the
     * root doesn't trigger parent-property-lookup deprecations via its own dynamic
     * resolution paths.
     *
     * See the deprecated_accessing_parent_project_properties spec for context.
     */
    private void setupMinimalChildProject() {
        settingsFile << """
            include("child")
        """
        file("child/build.gradle") << ""
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
