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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testfixtures.internal.NativeServicesTestFixture

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

abstract class DeleteIntegrationTest extends AbstractIntegrationSpec {
    private TestFile orig
    private TestFile keep
    private TestFile subject
    private TestFile remove
    private TestFile link

    def setup() {
        NativeServicesTestFixture.initialize();
    }

    def "will not follow symlinks"() {
        given:
        setupSymlinks()

        and:
        buildFile << '''
            task delete(type: Delete) {
                delete 'test/subject'
                followSymlinks = false
            }
        '''

        expect:
        succeeds("delete")
        assertTrue(orig.isDirectory())
        assertTrue(orig.exists())
        assertFalse(subject.isDirectory())
        assertFalse(subject.exists())
        assertTrue(keep.exists())
        assertFalse(remove.exists())
        assertFalse(link.exists())
    }

    def "will follow symlinks"() {
        given:
        setupSymlinks()

        and:
        buildFile << '''
            task delete(type: Delete) {
                delete 'test/subject'
                followSymlinks = true
            }
        '''

        expect:
        succeeds("delete")
        assertTrue(orig.exists())
        assertFalse(subject.exists())
        assertFalse(keep.exists())
        assertFalse(remove.exists())
        assertFalse(link.exists())
    }

    def "will follow symlinks from project"() {
        given:
        setupSymlinks()

        and:
        buildFile << '''
            task delete(type: DefaultTask) {
                project.delete {
                    delete 'test/subject'
                    followSymlinks = true
                }
            }
        '''

        expect:
        succeeds("delete")
        assertTrue(orig.exists())
        assertFalse(subject.exists())
        assertFalse(keep.exists())
        assertFalse(remove.exists())
        assertFalse(link.exists())
    }

    protected void setupSymlinks() {
        orig = testDirectory.createDir('test/orig')
        keep = orig.createFile('keep')

        subject = testDirectory.createDir('test/subject')
        remove = subject.createFile("remove")
        link = subject.file('link')
        createSymbolicLink(link, orig)

        assert orig.isDirectory()
        assert orig.exists()
        assert subject.isDirectory()
        assert subject.exists()
        assert keep.exists()
        assert remove.exists()
        assert link.exists()
    }

    protected abstract void createSymbolicLink(TestFile link, TestFile target)
}
