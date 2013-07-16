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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class CopyTaskIntegrationSpec extends AbstractIntegrationSpec {

    @Issue("http://issues.gradle.org/browse/GRADLE-2181")
    def "can copy files with unicode characters in name with non-unicode platform encoding"() {
        given:
        def weirdFileName = "القيادة والسيطرة - الإدارة.lnk"

        buildFile << """
            task copyFiles << {
                copy {
                    from 'res'
                    into 'build/resources'
                }
            }
        """

        file("res", weirdFileName) << "foo"

        when:
        executer.withDefaultCharacterEncoding("ISO-8859-1").withTasks("copyFiles")
        executer.run()

        then:
        file("build/resources", weirdFileName).exists()
    }

    @Issue("http://issues.gradle.org/browse/GRADLE-2181")
    def "can copy files with unicode characters in name with default platform encoding"() {
        given:
        def weirdFileName = "القيادة والسيطرة - الإدارة.lnk"

        buildFile << """
            task copyFiles << {
                copy {
                    from 'res'
                    into 'build/resources'
                }
            }
        """

        file("res", weirdFileName) << "foo"

        when:
        executer.withTasks("copyFiles").run()

        then:
        file("build/resources", weirdFileName).exists()
    }

    @Issue("http://issues.gradle.org/browse/GRADLE-2825")
    def "no deprecation warning on duplicate with include strategy"() {
        given:
        file("a/1.txt").touch()
        file("a/2.txt").touch()
        file("a").copyTo(file("b"))

        when:
        buildScript """
            apply plugin: "base"
            task c(type: Copy) {
                into "out"
                from "a"
                from "b"
            }
        """
        args "-i"

        then:
        succeeds "c"

        and:
        output.contains("Duplicate file at 1.txt")
        output.contains("Duplicate file at 2.txt")

        when:
        buildFile << "c.duplicatesStrategy = 'include'"
        succeeds "clean", "c"

        then:
        !output.contains("Duplicate file at 1.txt")
        !output.contains("Duplicate file at 2.txt")
    }
}
