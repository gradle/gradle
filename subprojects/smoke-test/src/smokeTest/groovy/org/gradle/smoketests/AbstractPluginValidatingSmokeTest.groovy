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
import org.gradle.internal.reflect.TypeValidationContext
import org.gradle.plugin.devel.tasks.TaskValidationReportFixture

@SelfType(AbstractSmokeTest)
abstract class AbstractPluginValidatingSmokeTest extends AbstractSmokeTest {

    private AllPluginsValidation allPlugins = new AllPluginsValidation()

    abstract Map<String, Versions> getPluginsToValidate()

    Map<String, String> getExtraPluginsRequiredForValidation() {
        [:]
    }

    String getBuildScriptConfigurationForValidation() {
        ""
    }

    void configureValidation(String testedPluginId, String version) {
        allPlugins.alwaysPasses = true
    }

    void validatePlugins(@DelegatesTo(value = AllPluginsValidation, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        spec.delegate = allPlugins
        spec.resolveStrategy = Closure.DELEGATE_FIRST
        spec()
    }

    protected void performValidation(String pluginId, String version) {
        configureValidation(pluginId, version)
        allPlugins.performValidation()
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
        def extraPluginsBlock = extraPluginsRequiredForValidation.collect { pluginId, pluginVersion ->
            "                id '$pluginId'" + (pluginVersion ? "version '$pluginVersion'" : "")
        }.join('\n')

        given:
        buildFile << """
            plugins {
                $extraPluginsBlock
                id '$id' version '$version'
                id 'validate-external-gradle-plugin'
            }

            $buildScriptConfigurationForValidation
        """

        expect:
        performValidation(id, version)

        where:
        iterations << iterations()
        (id, version) = iterations
    }

    class AllPluginsValidation {
        final List<PluginValidation> validations = []
        boolean alwaysPasses

        void alwaysPasses() {
            alwaysPasses = true
        }

        void onPlugin(String id, @DelegatesTo(value = PluginValidation, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
            def validation = new PluginValidation(id)
            validations << validation
            spec.delegate = validation
            spec.resolveStrategy = Closure.DELEGATE_FIRST
            spec()
        }

        void onPlugins(List<String> someIds, @DelegatesTo(value = PluginValidation, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
            someIds.each {
                onPlugin(it, spec)
            }
        }

        private void performValidation() {
            def failsValidation = validations.any { !it.messages.isEmpty() }
            def validation = runner("validateExternalPlugins", "--continue")
            def result
            if (failsValidation) {
                result = validation.buildAndFail()
            } else {
                result = validation.build()
            }
            def allPluginIds = result.tasks
                .findAll { it.path.startsWith(':validatePluginWithId_') }
                .collect { it.path - ':validatePluginWithId_' } as Set
            validations.each {
                it.verify()
                if (!it.tested) {
                    throw new IllegalStateException("Incomplete specification for plugin '$it.pluginId': you must verify that validation either fails or passes")
                }
            }
            def notValidated = (allPluginIds - validations*.reportId).collect { it.replace('_', '.') }
            if (notValidated && !alwaysPasses) {
                throw new IllegalStateException("The following plugins were validated but you didn't explicitly check the validation outcome: ${notValidated}")
            }
        }
    }

    class PluginValidation {
        private final String pluginId
        private final File reportFile

        private Map<String, TypeValidationContext.Severity> messages = [:]

        boolean skipped
        boolean tested

        PluginValidation(String id) {
            this.pluginId = id
            this.reportFile = file("build/reports/plugins/validation-report-for-${reportId}.txt")
        }

        String getReportId() {
            pluginId.replace('.', '_')
        }

        private void verify() {
            tested = true
            if (skipped) {
                return
            }
            assert reportFile.exists()
            def report = new TaskValidationReportFixture(reportFile)
            report.verify(messages)
        }

        /**
         * Allows skipping validation, for example when a plugin
         * is only available for some versions of the plugin under
         * test
         */
        void skip() {
            skipped = true
        }

        void passes() {
            messages = [:]
        }

        void failsWith(Map<String, TypeValidationContext.Severity> messages) {
            this.messages = messages
        }

        void failsWith(String singleMessage, TypeValidationContext.Severity severity) {
            failsWith([(singleMessage): severity])
        }
    }
}
