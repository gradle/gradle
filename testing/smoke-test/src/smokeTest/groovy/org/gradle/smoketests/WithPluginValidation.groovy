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
import org.gradle.api.problems.Severity
import org.gradle.plugin.devel.tasks.TaskValidationReportFixture
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testkit.runner.TaskOutcome

@SelfType(AbstractSmokeTest)
trait WithPluginValidation {
    final AllPluginsValidation allPlugins = new AllPluginsValidation(this)

    void validatePlugins(@DelegatesTo(value = AllPluginsValidation, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        spec.delegate = allPlugins
        spec.resolveStrategy = Closure.DELEGATE_FIRST
        spec()
    }

    static interface ProjectBuildDirLocator {
        TestFile getBuildDir(String projectPath, TestFile projectRoot)
    }

    static class AllPluginsValidation {
        final AbstractSmokeTest owner
        final List<PluginValidation> validations = []
        ProjectBuildDirLocator projectPathToBuildDir = (String projectPath, TestFile projectRoot) -> {
            projectRoot.file("${projectPath.split(':').join('/')}build")
        }
        Closure<Boolean> passingPluginsPredicate = { false }
        boolean alwaysPasses

        AllPluginsValidation(AbstractSmokeTest owner) {
            this.owner = owner
        }

        void alwaysPasses() {
            alwaysPasses = true
        }

        /**
         * Allows passing a predicate which tells if a plugin is supposed to
         * pass or not.
         * Because a plugin validation is independent of _which_ project it is
         * applied to, it doesn't care about the project path.
         */
        void passing(Closure<Boolean> pluginIdPredicate) {
            passingPluginsPredicate = pluginIdPredicate
        }

        void onPlugin(String id, String projectPath = ":", @DelegatesTo(value = PluginValidation, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
            TestFile projectRootFile = owner.file(".")
            def validation = new PluginValidation(id, projectPathToBuildDir.getBuildDir(projectPath, projectRootFile), owner)
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

        protected void performValidation(List<String> extraParameters = []) {
            owner.file("validate-external-gradle-plugin.gradle.kts") << getClass().getResource("validate-external-gradle-plugin.gradle.kts").text

            def failsValidation = validations.any { !it.messages.isEmpty() }
            def validation = owner.runner([
                "--init-script", "validate-external-gradle-plugin.gradle.kts",
                "validateExternalPlugins",
                "--continue",
                *extraParameters] as String[])
            validation.ignoreDeprecationWarnings("We are only checking type validation problems here")

            def result
            if (failsValidation) {
                result = validation.buildAndFail()
            } else {
                result = validation.build()
            }

            def taskPattern = ':validatePluginWithId_'
            def pluginsWithProjectPath = result.tasks
                .findAll {
                    !(it.outcome in [
                        TaskOutcome.NO_SOURCE,
                        TaskOutcome.SKIPPED
                    ]) && it.path.contains(taskPattern) && !it.path.startsWith(':plugins:') // ignore plugins project from previous version, it doesn't exist anymore (TODO: remove this check)
                }
                .collect {
                    def idx = it.path.indexOf(taskPattern)
                    def pluginId = it.path.substring(idx + taskPattern.length())
                    def projectPath = it.path.substring(0, it.path.lastIndexOf(':'))
                    if (projectPath == '') {
                        projectPath = ':'
                    }
                    [projectPath, pluginId]
                }
            pluginsWithProjectPath.each { projectPath, id ->
                def dottedId = id.replace('_', '.')
                boolean pluginValidationIsExpectedToPass = passingPluginsPredicate(dottedId)
                if (pluginValidationIsExpectedToPass) {
                    onPlugin(dottedId, projectPath) {
                        passes()
                    }
                }
            }
            def allPluginIds = pluginsWithProjectPath.collect { it[1] } as Set
            validations.each {
                it.verify()
                if (!it.tested) {
                    throw new IllegalStateException("Incomplete specification for plugin '$it.pluginId': you must verify that validation either fails or passes")
                }
            }
            def notValidated = (allPluginIds - validations*.reportId)
                .collect { it.replace('_', '.') }
                .findAll { !passingPluginsPredicate(it) }
            if (notValidated && !alwaysPasses) {
                throw new IllegalStateException("The following plugins were validated but you didn't explicitly check the validation outcome: ${notValidated}")
            }
        }
    }

    static class PluginValidation {
        private final AbstractSmokeTest owner
        private final String pluginId
        private final File reportFile

        private Map<String, Severity> messages = [:]

        boolean skipped
        boolean tested

        PluginValidation(String id, TestFile buildDir, AbstractSmokeTest owner) {
            this.pluginId = id
            this.owner = owner
            this.reportFile = buildDir.file("reports/plugins/validation-report-for-${reportId}.txt")
        }

        String getReportId() {
            pluginId.replace('.', '_')
        }

        void verify() {
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

        void failsWith(Map<String, Severity> messages) {
            this.messages = messages
        }

        void failsWith(String singleMessage, Severity severity) {
            failsWith([(singleMessage): severity])
        }
    }

}
