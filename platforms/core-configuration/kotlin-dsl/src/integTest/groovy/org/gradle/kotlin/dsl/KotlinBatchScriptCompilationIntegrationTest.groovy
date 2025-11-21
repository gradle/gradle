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
        executer.requireIsolatedDaemons()
    }

    def '#numberOfProjects projects (IP: #ip, batch: #batch)'() {
        given:
        numberOfProjects.times {
            def projName = "p$it"
            settingsKotlinFile << """
                include("$projName")
            """
            file("$projName/build.gradle.kts") << """
                plugins {
                    System.setProperty("$projName", "$projName")
                }
                assert(System.getProperty("$projName") == "$projName")
                tasks.register("ok")
            """
        }

        when:
        if (ip) {
            withIsolatedProjects()
        }
        run "ok", "-S", "--dry-run", "-Dorg.gradle.internal.kotlin-dsl.batch=$batch"

        then:
        numberOfProjects.times {
            def projName = "p$it"
            outputContains "$projName:ok"
        }

        where:
        numberOfProjects | ip    | batch
        5                | false | false
        5                | false | true
        10               | false | false
        10               | false | true
        50               | false | false
        50               | false | true
        100              | false | false
        100              | false | true
        500              | false | false
        500              | false | true
//        1000             | false | false
//        1000             | false | true
        5                | true  | false
        5                | true  | true
        10               | true  | false
        10               | true  | true
        50               | true  | false
        50               | true  | true
        100              | true  | false
        100              | true  | true
        500              | true  | false
        500              | true  | true
//        1000             | true  | false
//        1000             | true  | true
    }
}
