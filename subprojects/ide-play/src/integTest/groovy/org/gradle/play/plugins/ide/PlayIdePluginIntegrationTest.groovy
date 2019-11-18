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

package org.gradle.play.plugins.ide

import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.play.integtest.fixtures.PlayMultiVersionApplicationIntegrationTest

abstract class PlayIdePluginIntegrationTest extends PlayMultiVersionApplicationIntegrationTest {
    abstract String getIdePlugin()
    abstract String getIdeTask()
    abstract List<File> getIdeFiles()
    abstract String[] getBuildTasks()

    @ToBeFixedForInstantExecution
    def "generates IDE configuration"() {
        applyIdePlugin()
        when:
        succeeds(ideTask)
        then:
        result.assertTasksExecuted(buildTasks)
        ideFiles.each {
            file(it).assertExists()
        }
    }

    @ToBeFixedForInstantExecution
    def "does not blow up when no IDE plugin is applied"() {
        expect:
        succeeds("tasks")
    }

    protected void applyIdePlugin() {
        buildFile << """
    allprojects {
        apply plugin: "${idePlugin}"
    }
"""
    }
}
