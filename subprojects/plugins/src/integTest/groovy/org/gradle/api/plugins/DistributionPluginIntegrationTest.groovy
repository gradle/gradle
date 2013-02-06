/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins

import org.gradle.integtests.fixtures.WellBehavedPluginTest

import static org.hamcrest.Matchers.containsString

class DistributionPluginIntegrationTest extends WellBehavedPluginTest {

    @Override
    String getPluginId() {
        "distribution"
    }

    String getMainTask() {
        return "distZip"
    }

    def setup() {
        file("settings.gradle").text = "rootProject.name='TestProject'"
        file("someFile").createFile()
    }

    def createTaskForCustomDistribution() {
        when:
        buildFile << """
            apply plugin:'distribution'

            distributions {
                custom
            }

            customDistZip{
                from "someFile"
            }
            """
        then:
        succeeds('customDistZip')
        and:
        file('TestProject-custom.zip').usingNativeTools().unzipTo(file("unzip"))
        file("unzip/someFile").assertIsFile()
    }

    def createTaskForCustomDistributionWithCustomName() {
        when:
        buildFile << """
            apply plugin:'distribution'

            distributions {
                custom{
                    baseName = customName
                }
            }
            customNameDistZip{
                from "someFile"
            }
            """
        then:
        succeeds('customNameDistZip')
        and:
        file('TestProject-customName.zip').usingNativeTools().unzipTo(file("unzip"))
        file("unzip/someFile").assertIsFile()
    }


    def createTaskForCustomDistributionWithEmptyCustomName() {
        when:
        buildFile << """
            apply plugin:'distribution'
            distributions {
                custom{
                    baseName = ''
                }
            }
            customDistZip{
                from "someFile"
            }
            """
        then:
        runAndFail('customDistZip')
        failure.assertThatDescription(containsString("Distribution baseName must not be null or empty! Check your configuration of the distribution plugin."))
    }

}
