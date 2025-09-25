/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.caching.configuration

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class BuildCachePushPropertyIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        settingsFile << """
            buildCache {
                local {
                    directory = file("build-cache")
                }
            }
        """

        buildFile << """
            import org.gradle.api.DefaultTask
            import org.gradle.api.tasks.CacheableTask
            import org.gradle.api.tasks.OutputFile
            import org.gradle.api.tasks.TaskAction

            @CacheableTask
            class MyTask extends DefaultTask {
                @OutputFile
                File outputFile = new File(project.buildDir, "output.txt")

                @TaskAction
                void generate() {
                    outputFile.text = "output"
                }
            }

            task myTask(type: MyTask)
        """
    }

    def "can configure push property with a provider"() {
        given:
        settingsFile.append("""
            buildCache.local.getPush().set(true)
        """)

        when:
        run("myTask", "--build-cache")

        then:
        file("build-cache").list().length > 0
    }
}
