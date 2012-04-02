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
import org.gradle.util.TestPrecondition

@Requires(TestPrecondition.FILE_PERMISSIONS)
class CopyPermissionsIntegrationTest extends AbstractIntegrationSpec {


    def "file permissions of a file are preserved in copy action"() {
        given:
        def testSourceFile = file("reference.txt") << 'test file"'
        testSourceFile.permissions = mode
        and:
        buildFile << """
        import static java.lang.Integer.toOctalString
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

    def "fileMode can be modified in copy task"() {
        given:

        file("reference.txt") << 'test file"'
        and:
        buildFile << """
             import static java.lang.Integer.toOctalString
             task copy(type: Copy) {
                 from "reference.txt"
                 into ("build/tmp")
                 fileMode = $mode
             }

            ${verifyPermissionsTask(mode)}
            """

        when:
        run "verifyPermissions"

        then:
        noExceptionThrown()

        where:
        mode << [0755, 0777]

    }





    def "fileMode can be modified in copy action"() {
        given:
        file("reference.txt") << 'test file"'

        and:
        buildFile << """
            import static java.lang.Integer.toOctalString
            task copy << {
                copy {
                    from 'reference.txt'
                    into 'build/tmp'
                    fileMode = $mode
                }
            }

            ${verifyPermissionsTask(mode)}
            """

        when:
        run "verifyPermissions"

        then:
        noExceptionThrown()

        where:
        mode << [0755, 0777]

    }

    String verifyPermissionsTask(int mode) {
        """task verifyPermissions(dependsOn: copy) << {
                fileTree("build/tmp").visit{
                    assert toOctalString($mode) == toOctalString(it.mode)
                }
           }
        """
    }
}