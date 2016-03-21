/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.gradle.util.PreconditionVerifier
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DeleteIntegrationTest extends AbstractIntegrationTest {
    @Rule public PreconditionVerifier verifier = new PreconditionVerifier()

    @Before
    public void setup() {
        NativeServicesTestFixture.initialize();
    }

    @Test
    @Requires(TestPrecondition.SYMLINKS)
    public void willNotFollowSymlinks() {
        TestFile orig = testDirectory.createDir('test/orig')
        TestFile keep = orig.createFile('keep')

        TestFile subject = testDirectory.createDir('test/subject')
        TestFile remove = subject.createFile("remove")
        TestFile link = subject.file('link')
        link.createLink(orig)

        Assert.assertTrue(orig.isDirectory())
        Assert.assertTrue(orig.exists())
        Assert.assertTrue(subject.isDirectory())
        Assert.assertTrue(subject.exists())
        Assert.assertTrue(keep.exists())
        Assert.assertTrue(remove.exists())
        Assert.assertTrue(link.exists())

        testFile('build.gradle') << '''
            task delete(type: Delete) {
                delete 'test/subject'
                followSymlinks = false
            }
'''

        inTestDirectory().withTasks('delete').run()
        Assert.assertTrue(orig.isDirectory())
        Assert.assertTrue(orig.exists())
        Assert.assertFalse(subject.isDirectory())
        Assert.assertFalse(subject.exists())
        Assert.assertTrue(keep.exists())
        Assert.assertFalse(remove.exists())
        Assert.assertFalse(link.exists())
    }

    @Test
    @Requires(TestPrecondition.SYMLINKS)
    public void willFollowSymlinks() {
        TestFile orig = testDirectory.createDir('test/orig')
        TestFile keep = orig.createFile('keep')

        TestFile subject = testDirectory.createDir('test/subject')
        TestFile remove = subject.createFile("remove")
        TestFile link = subject.file('link')
        link.createLink(orig)

        Assert.assertTrue(orig.isDirectory())
        Assert.assertTrue(orig.exists())
        Assert.assertTrue(subject.isDirectory())
        Assert.assertTrue(subject.exists())
        Assert.assertTrue(keep.exists())
        Assert.assertTrue(remove.exists())
        Assert.assertTrue(link.exists())

        testFile('build.gradle') << '''
            task delete(type: Delete) {
                delete 'test/subject'
                followSymlinks = true
            }
'''

        inTestDirectory().withTasks('delete').run()
        Assert.assertTrue(orig.exists())
        Assert.assertFalse(subject.exists())
        Assert.assertFalse(keep.exists())
        Assert.assertFalse(remove.exists())
        Assert.assertFalse(link.exists())
    }
}
