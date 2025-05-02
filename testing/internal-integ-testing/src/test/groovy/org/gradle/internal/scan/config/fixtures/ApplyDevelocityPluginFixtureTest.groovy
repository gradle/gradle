/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.scan.config.fixtures

import spock.lang.Specification

import static org.gradle.plugin.management.internal.autoapply.AutoAppliedDevelocityPlugin.VERSION

class ApplyDevelocityPluginFixtureTest extends Specification {

    def "no special blocks"() {
        given:
        File file = File.createTempFile("test_script", ".tmp")
        file.write("includeBuild '../lib'")
        file.deleteOnExit()

        when:
        ApplyDevelocityPluginFixture.applyDevelocityPlugin(file)


        then:
        file.text =="""plugins {
            |    id("com.gradle.develocity") version("${VERSION}")
            |}
            |
            |includeBuild '../lib'""".stripMargin()
    }

    def "plugin management block blocks"() {
        given:
        File file = File.createTempFile("test_script", ".tmp")
        file.write("""pluginManagement {
            |    repositories {
            |        gradlePluginPortal()
            |    }
            |}
            |
            |includeBuild '../lib'""".stripMargin())
        file.deleteOnExit()

        when:
        ApplyDevelocityPluginFixture.applyDevelocityPlugin(file)


        then:
        file.text == """pluginManagement {
            |    repositories {
            |        gradlePluginPortal()
            |    }
            |}
            |
            |plugins {
            |    id("com.gradle.develocity") version("${VERSION}")
            |}
            |
            |includeBuild '../lib'""".stripMargin()
    }

}
