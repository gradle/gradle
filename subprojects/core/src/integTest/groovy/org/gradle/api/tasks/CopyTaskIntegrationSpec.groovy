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
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Unroll

import java.nio.charset.Charset

class CopyTaskIntegrationSpec extends AbstractIntegrationSpec {

    @Rule
    public final TestResources resources = new TestResources(testDirectoryProvider, "copyTestResources")

    @Issue("https://issues.gradle.org/browse/GRADLE-2181")
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

    @Issue("https://issues.gradle.org/browse/GRADLE-2181")
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

    @Issue("https://issues.gradle.org/browse/GRADLE-2838")
    @IgnoreIf({GradleContextualExecuter.parallel})
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
        def destinationDir = file("out")
        destinationDir.assertHasDescendants("a.txt", "b.txt")
        destinationDir.listFiles().findAll { it.directory }*.name.toSet() == ["dirA"].toSet()
    }

    @IgnoreIf({GradleContextualExecuter.parallel})
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

        def destinationDir = file("out")
        destinationDir.assertHasDescendants("a.txt", "b.txt")
        destinationDir.listFiles().findAll { it.directory }*.name.toSet() == ["dirA", "dirB"].toSet()
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2902")
    def "internal copy spec methods are not visible to users"() {
        when:
        file("res/foo.txt") << "bar"

        buildScript """
            task copyAction {
                ext.source = 'res'
                doLast {
                    copy {
                        from source
                        into 'action'
                    }
                }
            }
            task copyTask(type: Copy) {
                ext.children = 'res'
                into "task"
                into "dir", {
                    from children
                }
            }
        """

        then:
        succeeds "copyAction", "copyTask"

        and:
        file("action/foo.txt").exists()
        file("task/dir/foo.txt").exists()
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-3022")
    def "filesMatching must match against sourcePath"() {
        given:
        file("a/b.txt") << "\$foo"

        when:
        buildScript """
           task c(type: Copy) {
               from("a") {
                   filesMatching("b.txt") {
                       expand foo: "bar"
                   }
                   into "nested"
               }
               into "out"
           }
        """

        then:
        succeeds "c"

        and:
        file("out/nested/b.txt").text == "bar"
    }

    @Issue("GRADLE-3418")
    @Unroll
    def "can copy files with #filePath in path when excluding #pattern"() {
        given:
        file("test/${filePath}/a.txt").touch()

        buildScript """
            task copy(type: Copy) {
                into "out"
                from "test"
                exclude "$pattern"
            }
        """

        when:
        succeeds "copy"

        then:
        file("out/${filePath}/a.txt").exists()

        where:
        pattern      | filePath
        "**/#*#"     | "#"
        "**/%*%"     | "%"
        "**/abc*abc" | "abc"
    }

    @Unroll
    def "can copy files with #operationName #operationKind using #charsetDescription charset when filteringCharset is #isSetDescription"() {
        given:
        buildScript executionScript

        when:
        if(platformDefaultCharset) {
            executer.withDefaultCharacterEncoding(platformDefaultCharset)
        }
        executer.withTasks(executionName)
        executer.run()

        then:
        file('dest/accents.c').readLines(readCharset)[0] == expected

        where:
        // UTF8 is the actual encoding of the file accents.c.
        // Any byte sequence of the file accents.c is a valid ISO-8859-1 character sequence,
        // so we can read and write it with that encoding as well.
        operationName | operationKind | platformDefaultCharset | filteringCharset | expected
        // platform default charset is honored
        'Copy'        | 'task'        | 'UTF-8'                | null             | 'éàüî 1'
        'Copy'        | 'method'      | 'UTF-8'                | null             | 'éàüî 1'
        'Sync'        | 'task'        | 'UTF-8'                | null             | 'éàüî 1'
        'Sync'        | 'method'      | 'UTF-8'                | null             | 'éàüî 1'
        // filtering charset is honored
        'Copy'        | 'task'        | null                   | 'UTF-8'          | 'éàüî 1'
        'Copy'        | 'task'        | null                   | 'ISO-8859-1'     | new String('éàüî 1'.getBytes('UTF-8'), 'ISO-8859-1')
        'Copy'        | 'method'      | null                   | 'UTF-8'          | 'éàüî 1'
        'Copy'        | 'method'      | null                   | 'ISO-8859-1'     | new String('éàüî 1'.getBytes('UTF-8'), 'ISO-8859-1')
        'Sync'        | 'task'        | null                   | 'UTF-8'          | 'éàüî 1'
        'Sync'        | 'task'        | null                   | 'ISO-8859-1'     | new String('éàüî 1'.getBytes('UTF-8'), 'ISO-8859-1')
        'Sync'        | 'method'      | null                   | 'UTF-8'          | 'éàüî 1'
        'Sync'        | 'method'      | null                   | 'ISO-8859-1'     | new String('éàüî 1'.getBytes('UTF-8'), 'ISO-8859-1')
        // derived data
        charsetDescription = filteringCharset ?: "platform default ${platformDefaultCharset ?: Charset.defaultCharset().name()}"
        isSetDescription = filteringCharset ? 'set' : 'unset'
        readCharset = filteringCharset ?: platformDefaultCharset
        executionName = operationName.toLowerCase(Locale.US)
        executionScript = operationKind == 'task' ? filteringCharsetTask(executionName, operationName, filteringCharset) : filteringCharsetProjectMethod(executionName, executionName, filteringCharset)
    }

    def filteringCharsetTask(taskName, taskType, filteringCharset) {
        """
            task ($taskName, type:$taskType) {
               from 'src'
               into 'dest'
               expand(one: 1)
               ${filteringCharset ? "filteringCharset = '$filteringCharset'" : ''}
            }
        """.stripIndent()
    }

    def filteringCharsetProjectMethod(taskName, methodName, filteringCharset) {
        """
            task ($taskName) {
                doLast {
                    project.$methodName {
                       from 'src'
                       into 'dest'
                       expand(one: 1)
                       ${filteringCharset ? "filteringCharset = '$filteringCharset'" : ''}
                    }
                }
            }
        """.stripIndent()
    }
}
