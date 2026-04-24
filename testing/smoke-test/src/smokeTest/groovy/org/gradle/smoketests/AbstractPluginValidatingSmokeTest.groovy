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
