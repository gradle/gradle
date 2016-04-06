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
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static org.junit.Assert.assertTrue
import static org.junit.Assert.assertFalse

class DeleteIntegrationTest extends AbstractIntegrationTest {
    @Rule public PreconditionVerifier verifier = new PreconditionVerifier()

    private TestFile orig;
    private TestFile keep;
    private TestFile subject;
    private TestFile remove;
    private TestFile link;

    @Before
    public void setup() {
        NativeServicesTestFixture.initialize();
    }

    @Test
    @Requires(TestPrecondition.SYMLINKS)
    public void willNotFollowSymlinks() {
        setupSymlinks()

        testFile('build.gradle') << '''
            task delete(type: Delete) {
                delete 'test/subject'
                followSymlinks = false
            }
'''

        inTestDirectory().withTasks('delete').run()
        assertTrue(orig.isDirectory())
        assertTrue(orig.exists())
        assertFalse(subject.isDirectory())
        assertFalse(subject.exists())
        assertTrue(keep.exists())
        assertFalse(remove.exists())
        assertFalse(link.exists())
    }

    @Test
    @Requires(TestPrecondition.SYMLINKS)
    public void willFollowSymlinks() {
        setupSymlinks()

        testFile('build.gradle') << '''
            task delete(type: Delete) {
                delete 'test/subject'
                followSymlinks = true
            }
'''

        inTestDirectory().withTasks('delete').run()
        assertTrue(orig.exists())
        assertFalse(subject.exists())
        assertFalse(keep.exists())
        assertFalse(remove.exists())
        assertFalse(link.exists())
    }

    @Test
    @Requires(TestPrecondition.SYMLINKS)
    public void willFollowSymlinksFromProject() {
        setupSymlinks()

        testFile('build.gradle') << '''
            task delete(type: DefaultTask) {
                project.delete {
                    delete 'test/subject'
                    followSymlinks = true
                }
            }
'''

        inTestDirectory().withTasks('delete').run()
        assertTrue(orig.exists())
        assertFalse(subject.exists())
        assertFalse(keep.exists())
        assertFalse(remove.exists())
        assertFalse(link.exists())
    }

    def void setupSymlinks() {
        orig = testDirectory.createDir('test/orig')
        keep = orig.createFile('keep')

        subject = testDirectory.createDir('test/subject')
        remove = subject.createFile("remove")
        link = subject.file('link')
        link.createLink(orig)

        assertTrue(orig.isDirectory())
        assertTrue(orig.exists())
        assertTrue(subject.isDirectory())
        assertTrue(subject.exists())
        assertTrue(keep.exists())
        assertTrue(remove.exists())
        assertTrue(link.exists())
    }
}
