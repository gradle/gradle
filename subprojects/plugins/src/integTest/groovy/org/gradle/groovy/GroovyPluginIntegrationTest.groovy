/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.groovy

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class GroovyPluginIntegrationTest extends AbstractIntegrationSpec {
    // TODO: This isn't done yet, we still realize many tasks
    // Eventually, this should only realize "help"
    def "does not realize all possible tasks"() {
        buildFile << """
            apply plugin: 'groovy'
            
            def configuredTasks = []
            tasks.configureEachLater {
                configuredTasks << it
            }
            
            gradle.buildFinished {
                assert configuredTasks.size() == 3
                def configuredTaskPaths = configuredTasks*.path
                // This should be the only task configured
                assert ":help" in configuredTaskPaths
                
                // This task needs to be able to register publications lazily
                assert ":jar" in configuredTaskPaths
                
                // This task is eagerly configured with configureEachLater
                assert ":test" in configuredTaskPaths
            }
        """
        expect:
        succeeds("help")
    }
}
