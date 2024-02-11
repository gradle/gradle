/*
 * Copyright 2019 the original author or authors.
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

class ConfigurationCacheProjectReportIntegTest extends AbstractConfigurationCacheIntegrationTest {

    def setup() {
        buildFile << """
            apply plugin: 'project-report'
        """
    }

    def "configuration cache for Project Report plugin task '#task' on empty project"() {
        given:
        configurationCacheRun(task, *options)
        def firstRunOutput = removeVfsLogOutput(result.normalizedOutput)
            .replaceAll(/Calculating task graph as no cached configuration is available for tasks: ${task}.*\n/, '')
            .replaceAll(/Configuration cache entry stored.\n/, '')

        when:
        configurationCacheRun(task, *options)
        def secondRunOutput = removeVfsLogOutput(result.normalizedOutput)
            .replaceAll(/Reusing configuration cache.\n/, '')
            .replaceAll(/Configuration cache entry reused.\n/, '')

        then:
        firstRunOutput == secondRunOutput

        where:
        task                    | options
        "dependencyReport"      | []
        "taskReport"            | []
        "propertyReport"        | []
        "htmlDependencyReport"  | []
        // projectReport depends on the other ones, and task order may not be preserved,
        // causing equality comparison between first and second outputs to fail
        //"projectReport"         | []
    }
}
