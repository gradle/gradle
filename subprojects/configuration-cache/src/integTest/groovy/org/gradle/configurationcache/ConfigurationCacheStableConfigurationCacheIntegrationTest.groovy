/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.configurationcache

import org.gradle.configurationcache.fixtures.ExternalProcessFixture

class ConfigurationCacheStableConfigurationCacheIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def setup() {
        settingsFile '''
            enableFeaturePreview 'STABLE_CONFIGURATION_CACHE'
        '''
    }

    def 'external processes at configuration time are reported as problems'() {
        given:
        def snippets = ExternalProcessFixture.processBuilder().groovy.newSnippets(new ExternalProcessFixture(testDirectory))

        buildFile """
            ${snippets.imports}
            ${snippets.body}
        """

        when:
        configurationCacheFails ":help"

        then:
        problems.assertFailureHasProblems(failure) {
            withProblem "Build file 'build.gradle': external process started"
        }
    }

    def 'project access at execution time is either a problem or a deprecation'() {
        given:
        buildFile '''
            tasks.register('problematic') { doLast { println project.name } }
        '''

        when:
        if (withCC) {
            configurationCacheFails ':problematic'
        } else {
            executer.expectDocumentedDeprecationWarning "Invocation of Task.project at execution time has been deprecated. This will fail with an error in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#task_project"
            succeeds ':problematic'
        }

        then:
        if (withCC) {
            problems.assertFailureHasProblems(failure) {
                withProblem "Build file 'build.gradle': line 2: invocation of 'Task.project' at execution time is unsupported."
            }
        }

        where:
        withCC << [true, false]
    }
}
