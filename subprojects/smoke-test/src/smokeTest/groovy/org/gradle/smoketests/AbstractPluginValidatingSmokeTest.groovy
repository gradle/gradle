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
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

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
            versions.each { v ->
                result << [id, v]
            }
        }
        result
    }

    @UnsupportedWithConfigurationCache(
        because = "some plugins are not compatible with the configuration cache but it doesn't really matter because we get the results with the regular test suite"
    )
    def "performs static analysis of plugin #id version #version"() {
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
        performValidation()

        where:
        iterations << iterations()
        (id, version) = iterations
    }

    void configureValidation(String testedPluginId, String version) {
        allPlugins.alwaysPasses = true
    }

    void performValidation() {
        allPlugins.performValidation()
    }

}
