/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.play.integtest
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class MixedPlayAndLegacyJavaProjectIntegrationTest extends AbstractIntegrationSpec {

    @Issue("GRADLE-3356")
    def "can apply both java and play plugins"() {
        settingsFile.text = "rootProject.name = 'mixedJavaAndPlay'"
        buildFile << """
            plugins {
                id 'play'
                id 'java'
            }
"""
        expect:
        succeeds "assemble"
    }
}
