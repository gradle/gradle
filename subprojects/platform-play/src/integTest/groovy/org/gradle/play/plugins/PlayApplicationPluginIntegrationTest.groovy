/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.plugins

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.TextUtil

class PlayApplicationPluginIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
        plugins {
            id 'play-application'
        }

        model {
            components {
                myApp(PlayApplicationSpec)
            }
        }
"""
    }

    def "can register PlayApplicationSpec component"() {
        def javaVersion = JavaVersion.current();
        when:
        succeeds "components"
        then:
        output.contains(TextUtil.toPlatformLineSeparators("""
DefaultPlayApplicationSpec 'myApp'
----------------------------------

Source sets
    No source sets.

Binaries
    DefaultPlayApplicationBinarySpec 'myAppBinary'
        build using task: :myAppBinary
        platform: java${javaVersion.majorVersion}
        tool chain: Play Framework 2.11-2.3.5 (JDK ${javaVersion.majorVersion} (${javaVersion.toString()})"""))
    }

    def "builds play binary"() {
        when:
        succeeds("assemble")
        then:
        output.contains(TextUtil.toPlatformLineSeparators(""":routesCompileMyAppBinary
:twirlCompileMyAppBinary UP-TO-DATE
:createMyAppBinaryJar
:myAppBinary
:assemble"""));
        and:
        file("build/jars/myApp/myAppBinary.jar").exists()
    }
}