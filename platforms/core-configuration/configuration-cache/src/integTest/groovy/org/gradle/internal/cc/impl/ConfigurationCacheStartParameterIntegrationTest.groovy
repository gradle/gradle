/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.cc.impl

import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture

class ConfigurationCacheStartParameterIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    ConfigurationCacheFixture fixture = new ConfigurationCacheFixture(this)

    def "resolved default task names are restored on the build start parameter for a configuration cache hit"() {
        given:
        // On the store run the scheduler resolves the configured default tasks into
        // startParameter.taskNames. On a cache hit the scheduler does not run, so without restoring the
        // captured names an execution-time read of the build's start parameter would observe an empty
        // list. The task reads the live build start parameter (injected, not via project access).
        buildFile """
            abstract class PrintRequestedTasks extends DefaultTask {
                @Inject abstract StartParameter getStartParameter()
                @TaskAction void printIt() {
                    println("REQUESTED=" + getStartParameter().taskNames)
                }
            }

            defaultTasks 'printRequestedTasks'
            tasks.register('printRequestedTasks', PrintRequestedTasks)
        """

        when: "store run with no tasks on the command line, so default tasks apply"
        configurationCacheRun()

        then:
        fixture.assertStateStored()
        outputContains("REQUESTED=[printRequestedTasks]")

        when: "cache hit"
        configurationCacheRun()

        then:
        fixture.assertStateLoaded()
        outputContains("REQUESTED=[printRequestedTasks]")
    }
}
