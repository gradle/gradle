/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.jvm.toolchain

import org.apache.commons.lang.StringUtils
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ShowToolchainsIntegrationTest extends AbstractIntegrationSpec {

    def "shows toolchains only once across all projects"() {
        when:
        settingsFile << """
                include 'a', 'b', 'c'
            """
        buildFile << ""
        then:
        run("javaToolchains")

        outputContains(":javaToolchains")
        StringUtils.countMatches(result.output, ":javaToolchains") == 1
    }

    def "showsToolchains is properly visible as task"() {
        when:
        run("tasks")

        then:
        outputContains("javaToolchains - Displays the detected java toolchains.")
    }

    def "toolchains log contains progress info about installation suppliers"() {
        when:
        settingsFile << """
                include 'a'
            """
        buildFile << ""
        then:
        run("javaToolchains", "--debug")

        outputContains(":javaToolchains")
        outputContains("Discovering toolchains provided via Maven Toolchains")
    }
}
