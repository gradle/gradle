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
package org.gradle.integtests

import org.gradle.integtests.fixtures.ExecutionFailure
import org.gradle.util.OperatingSystem
import org.gradle.util.TestFile
import org.junit.Assert
import org.junit.Test
import org.gradle.integtests.fixtures.internal.AbstractIntegrationTest

class CopyErrorIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void reportsSymLinkWhichPointsToNothing() {
        if (OperatingSystem.current().isWindows()) {
            return
        }

        TestFile link = testFile('src/file')
        link.linkTo(testFile('missing'))

        Assert.assertFalse(link.isDirectory())
        Assert.assertFalse(link.isFile())
        Assert.assertFalse(link.exists())

        testFile('build.gradle') << '''
            task copy(type: Copy) {
                from 'src'
                into 'dest'
            }
'''

        ExecutionFailure failure = inTestDirectory().withTasks('copy').runWithFailure()
        failure.assertHasDescription("Could not list contents of '${link}'.")
    }

    @Test
    public void reportsUnreadableSourceDir() {
        if (OperatingSystem.current().isWindows()) {
            return
        }

        TestFile dir = testFile('src').createDir()
        dir.permissions = '-w-r--r--'

        Assert.assertTrue(dir.isDirectory())
        Assert.assertTrue(dir.exists())
        Assert.assertFalse(dir.canRead())
        Assert.assertTrue(dir.canWrite())

        testFile('build.gradle') << '''
            task copy(type: Copy) {
                from 'src'
                into 'dest'
            }
'''

        ExecutionFailure failure = inTestDirectory().withTasks('copy').runWithFailure()
        failure.assertHasDescription("Could not list contents of directory '${dir}' as it is not readable.")

        dir.permissions = 'rwxr--r--'
    }
}
