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

import org.gradle.api.plugins.ExtensionAware
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

    def "nested specs and details arent extensible objects"() {
        given:
        file("a/a.txt").touch()

        buildScript """
            task copy(type: Copy) {
                assert delegate instanceof ${ExtensionAware.name}
                into "out"
                from "a", {
                    assert !(delegate instanceof ${ExtensionAware.name})
                    eachFile {
                        it.name = "rename"
                        assert !(delegate instanceof ${ExtensionAware.name})
                    }
                }
            }
        """

        when:
        succeeds "copy"

        then:
        file("out/rename").exists()
    }

    @Issue("http://issues.gradle.org/browse/GRADLE-2838")
    def "include empty dirs works when nested"() {
        given:
        file("a/a.txt") << "foo"
        file("a/dirA").createDir()
        file("b/b.txt") << "foo"
        file("b/dirB").createDir()

        buildScript """
            task copyTask(type: Copy) {
                into "out"
                from "b", {
                    includeEmptyDirs = false
                }
                from "a"
                from "c", {}
            }
        """

        when:
        succeeds "copyTask"

        then:
        ":copyTask" in nonSkippedTasks
        with(file("out")) {
            file("a.txt").exists()
            file("b.txt").exists()
            file("dirA").exists()
            !file("dirB").exists()
        }
    }

    def "include empty dirs is overridden by subsequent"() {
        given:
        file("a/a.txt") << "foo"
        file("a/dirA").createDir()
        file("b/b.txt") << "foo"
        file("b/dirB").createDir()

        buildScript """
            task copyTask(type: Copy) {
                into "out"
                from "b", {
                    includeEmptyDirs = false
                }
                from "a"
                from "c", {}
                from "b", {
                    includeEmptyDirs = true
                }
            }
        """

        when:
        succeeds "copyTask"

        then:
        ":copyTask" in nonSkippedTasks
        with(file("out")) {
            file("a.txt").exists()
            file("b.txt").exists()
            file("dirA").exists()
            file("dirB").exists()
        }
    }

}
