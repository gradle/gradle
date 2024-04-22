/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.enterprise

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class DevelocityBuildLifecycleServiceIntegrationTest extends AbstractIntegrationSpec {

    def "can use lifecycle service to apply logic to all projects"() {
        settingsFile << """
            include 'foo', 'bar'
            includeBuild 'included'

            gradle.services.get(${DevelocityBuildLifecycleService.name}).beforeProject {
                def projectPath = it.buildTreePath
                println "Configuring '\$projectPath'"
                it.tasks.register("myTask") {
                }
            }
        """
        ['foo', 'bar', 'included/sub1', 'included/sub2'].each { file(it).mkdirs() }
        file('included/settings.gradle') << """
            include 'sub1', 'sub2'
        """
        file('included/build.gradle') << """
            tasks.register("myTask") {
            }
        """

        when:
        run "myTask", "included:myTask"
        then:
        outputContains("Configuring ':foo'")
        outputContains("Configuring ':'")
        outputContains("Configuring ':bar'")
        outputDoesNotContain("Configuring ':included")
    }

    def "can use lifecycle service in included build"() {
        settingsFile << """
            include 'foo', 'bar'
            includeBuild 'included'
        """
        ['foo', 'bar', 'included/sub1', 'included/sub2'].each { file(it).mkdirs() }
        file('included/settings.gradle') << """
            include 'sub1', 'sub2'

            gradle.services.get(${DevelocityBuildLifecycleService.name}).beforeProject {
                def projectPath = it.buildTreePath
                println "Configuring '\$projectPath'"
                it.tasks.register("myTask") {
                }
            }
        """

        when:
        run "included:myTask", "included:sub1:myTask", "included:sub2:myTask"
        then:
        outputContains("Configuring ':included'")
        outputContains("Configuring ':included:sub1'")
        outputContains("Configuring ':included:sub2'")
        outputDoesNotContain("Configuring ':'")
        outputDoesNotContain("Configuring ':foo")
        outputDoesNotContain("Configuring ':bar")
    }
}
