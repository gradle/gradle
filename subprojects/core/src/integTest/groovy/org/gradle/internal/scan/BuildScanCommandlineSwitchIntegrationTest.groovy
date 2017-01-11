/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.scan

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class BuildScanCommandlineSwitchIntegrationTest extends AbstractIntegrationSpec {


    def "running gradle with --scan flag marks BuildScanRequest as requested"() {
        when:
        withDummyBuildScanPlugin()
        buildFile << """
            task assertBuildScanRequest {
                doLast {
                    assert project.services.get(org.gradle.internal.scan.BuildScanRequest).collectRequested() == true
                    
                }
            }
        """
        then:
        succeeds("assertBuildScanRequest", "--scan")
    }

    def "running gradle with --scan without plugin applied results in error message"() {
        when:
        buildFile << """
            task someTask
        """
        then:
        fails("someTask", "--scan")
        and:
        errorOutput.contains("Build scan cannot be requested as build scan plugin is not applied.\n"
            + "For more information, please visit: https://gradle.com/get-started")
    }


    def withDummyBuildScanPlugin() {
        buildFile << """
        class DummyBuildScanPlugin implements Plugin<Project> {
            void apply(Project project){
            }
        }
        apply plugin:DummyBuildScanPlugin
        """
    }
}
