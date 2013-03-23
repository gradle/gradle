/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.buildsetup.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class SetupBuildIntegrationTest extends AbstractIntegrationSpec {
    def "can be executed without existing pom"() {
        given:
        file("build.gradle") << "apply plugin: 'build-setup'"

        when:
        run 'setupBuild'

        then:
        //not yet implemented:
        // file("build.gradle").text == ""
        file("gradlew").assertExists()
        file("gradlew.bat").assertExists()
        file("gradle/wrapper/gradle-wrapper.jar").assertExists()
        file("gradle/wrapper/gradle-wrapper.properties").assertExists()
    }
}
