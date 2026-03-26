/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.StableConfigurationCacheDeprecations

class ClosureScopeIntegrationTest extends AbstractIntegrationSpec implements StableConfigurationCacheDeprecations {

    def "check scope when closure in ext"() {
        given:
        buildFile("closure_in_ext.gradle", """
            abstract class TaskWithClosure extends DefaultTask {
                @Input
                abstract Property<String> getClosure()
                @Input
                abstract Property<String> getClosureProvider()
                @Input
                abstract Property<String> getProjectName()

                @TaskAction
                void print() {
                    println("Closure: \${getClosure().get()} Provider: \${getClosureProvider().get()} - Configuration: \${getProjectName().get()}")
                }
            }

            allprojects {
                ext.someClosure = {
                    project.name
                }

                tasks.register("someTask", TaskWithClosure) {
                    closure.set(someClosure())
                    closureProvider.set(provider(someClosure))
                    projectName.set(project.name)
                }
            }
        """)
        buildFile """
            apply from:'closure_in_ext.gradle'
        """
        createDirs("sampleSub")
        settingsFile """
            rootProject.name = "rootProject"
            include 'sampleSub'
        """
        when:
        succeeds("someTask")

        then:
        outputContains("Closure: sampleSub Provider: sampleSub - Configuration: sampleSub")
        outputContains("Closure: rootProject Provider: rootProject - Configuration: rootProject")
    }
}
