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
import org.gradle.util.Requires
import org.gradle.util.TemporaryFolder
import org.gradle.util.TestFile
import org.gradle.util.TestPrecondition
import org.junit.Rule

@Requires(TestPrecondition.FILE_PERMISSIONS)
class CopyPermissionsIntegrationTest extends AbstractIntegrationSpec {

    @Rule TemporaryFolder temporaryFolder

    def "file permissions are preserved in copy action"() {
        given:
        def testSourceFile = file("reference.txt") << 'test file"'
        testSourceFile.permissions = mode
        and:
        buildFile << """
        task copy(type: Copy) {
            from "reference.txt"
            into ("build/tmp")
        }
        """

        when:
        run "copy"
        then:
        file("build/tmp/reference.txt").permissions == mode
        where:
        mode << ['rwxr--r-x']
    }

    def "directory permissions are preserved in copy action"() {
        given:
        TestFile parent = getTestDir().createDir("testparent")
        TestFile child = parent.createDir("testchild")
        child.file("reference.txt") << "test file"

        child.permissions = mode
        and:
        buildFile << """
            task copy(type: Copy) {
                from "testparent"
                into ("build/tmp")
            }
            """
        when:
        run "copy"
        then:
        file("build/tmp/testchild").permissions == mode
        where:
        mode << ['rwxr-xr-x', 'rwxrwxrw-']
    }

    def "fileMode can be modified in copy task"() {
        given:

        file("reference.txt") << 'test file"'
        file("reference.txt").setPermissions("rwxrwxrwx")
        and:
        buildFile << """
             task copy(type: Copy) {
                 from "reference.txt"
                 into ("build/tmp")
                 fileMode = $mode
             }
            """
        when:
        run "copy"

        then:
        file("build/tmp/reference.txt").permissions == permissionString

        where:
        mode | permissionString
        0755 | 'rwxr-xr-x'
        0776 | 'rwxrwxrw-'
    }

    def "fileMode can be modified in copy action"() {
        given:
        file("reference.txt") << 'test file"'

        and:
        buildFile << """
            task copy << {
                copy {
                    from 'reference.txt'
                    into 'build/tmp'
                    fileMode = $mode
                }
            }
            """

        when:
        run "copy"

        then:
        file("build/tmp/reference.txt").permissions == permissionString
        where:
        mode | permissionString
        0755 | 'rwxr-xr-x'
        0777 | 'rwxrwxrwx'
    }

    def "dirMode can be modified in copy task"() {
        given:
        TestFile parent = getTestDir().createDir("testparent")
        TestFile child = parent.createDir("testchild")
        child.file("reference.txt") << "test file"

        child.permissions = "rwxrwxrwx"
        and:
        buildFile << """
                task copy(type: Copy) {
                    from "testparent"
                    into ("build/tmp")
                    dirMode = $mode
                }
                """
        when:
        run "copy"
        then:
        file("build/tmp/testchild").permissions == permissionString
        where:
        mode | permissionString
        0755 | 'rwxr-xr-x'
        0776 | 'rwxrwxrw-'
    }
}