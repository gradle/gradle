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

package org.gradle.kotlin.dsl

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.configurationcache.isolated.IsolatedProjectsExecuterFixture

class KotlinBatchScriptCompilationIntegrationTest extends AbstractIntegrationSpec implements IsolatedProjectsExecuterFixture {

    def setup() {
        requireOwnGradleUserHomeDir 'to avoid reusing the script cache'
    }

    def '#numberOfProjects projects (IP: #ip)'() {
        given:
        numberOfProjects.times {
            def projName = "p$it"
            settingsKotlinFile << """
                include("$projName")
            """
            file("$projName/build.gradle.kts") << """
                plugins {
                    println("$projName!")
                }
                tasks.register("ok")
            """
        }

        when:
        if (ip) {
            withIsolatedProjects()
        }
        run "ok", "-S"

        then:
        numberOfProjects.times {
            def projName = "p$it"
            outputContains "$projName!"
        }

        where:
        numberOfProjects | ip
        5                | false
//        10               | false
//        50               | false
//        100              | false
//        500              | false
//        1000             | false
//        5                | true
//        10               | true
//        50               | true
//        100              | true
//        500              | true
//        1000             | true
    }
}
