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

    def createTaskForCustomDistribution() {
        when:
        buildFile << """
            apply plugin:'distribution'


            distributions {
    custom
}
            distZip{

            }
            """
        then:
        succeeds('customDistZip')
    }

    def createTaskForCustomDistributionWithCustomName() {
        when:
        buildFile << """
            apply plugin:'distribution'


            distributions {
    custom{
        name=customName
    }
}
            distZip{

            }
            """
        then:
        succeeds('customNameDistZip')
    }


    def createTaskForCustomDistributionWithEmptyCustomName() {
        when:
        buildFile << """
            apply plugin:'distribution'


            distributions {
    custom{
        name=''
    }
}
            distZip{

            }
            """
        then:
        runAndFail('DistZip')
        failure.assertThatDescription(containsString("Distribution name must not be null or empty ! Check your configuration of the distribution plugin."))
    }

 }
