/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.hamcrest.Matchers

class CopyErrorIntegrationTest extends AbstractIntegrationSpec {
    def givesReasonableErrorMessageWhenPathCannotBeConverted() {
        file('src/thing.txt').createFile()

        buildFile << '''
            task copy(type: Copy) {
                from('src') {
                    into project.repositories
                }
                into 'dest'
            }
'''

        expect:
        fails "copy"
        failure.assertHasCause("""Error while evaluating property 'rootSpec\$1\$1.destPath': Cannot convert the provided notation to a String: repository container.
The following types/formats are supported:
  - String or CharSequence instances, for example 'some/path'.
  - Boolean values, for example true, Boolean.TRUE.
  - Number values, for example 42, 3.14.
  - A File instance
  - A Closure that returns any supported value.
  - A Callable that returns any supported value.""")
    }

    @Requires(TestPrecondition.SYMLINKS)
    def reportsSymLinkWhichPointsToNothing() {
        TestFile link = file('src/file')
        link.createLink(file('missing'))

        buildFile << '''
            task copy(type: Copy) {
                from 'src'
                into 'dest'
            }
'''

        expect:
        fails "copy"
        failure.assertHasCause("Could not list contents of '${link}'.")
    }

    @Requires(TestPrecondition.FILE_PERMISSIONS)
    def reportsUnreadableSourceDir() {
        TestFile dir = file('src').createDir()
        buildFile << '''
            task copy(type: Copy) {
                from 'src'
                into 'dest'
            }
'''

        def oldPermissions = dir.permissions
        dir.permissions = '-w-r--r--'

        expect:
        fails "copy"
        failure.assertThatCause(Matchers.anyOf(
            Matchers.startsWith("Could not list contents of directory '${dir}' as it is not readable."),
            Matchers.startsWith("Could not read path '${dir}'.")
        ))

        cleanup:
        dir.permissions = oldPermissions
    }
}
