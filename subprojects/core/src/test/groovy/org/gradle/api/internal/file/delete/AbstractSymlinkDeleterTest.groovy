/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.file.delete

import org.gradle.api.Action
import org.gradle.api.file.DeleteSpec
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.TestFiles
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Assume
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.api.internal.file.TestFiles.fileSystem

abstract class AbstractSymlinkDeleterTest extends Specification {
    static final boolean FOLLOW_SYMLINKS = true;

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    FileResolver resolver = TestFiles.resolver(tmpDir.testDirectory)
    Deleter delete = new Deleter(resolver, fileSystem())

    def doesNotDeleteFilesInsideSymlinkDir() {
        Assume.assumeTrue(canCreateSymbolicLinkToDirectory())

        given:
        def keepTxt = tmpDir.createFile("originalDir", "keep.txt")
        def originalDir = keepTxt.parentFile
        def link = new File(tmpDir.getTestDirectory(), "link")

        when:
        createSymbolicLink(link, originalDir)

        then:
        link.exists()

        when:
        boolean didWork = delete.delete(link)

        then:
        !link.exists()
        originalDir.assertExists()
        keepTxt.assertExists()
        didWork

        cleanup:
        link.delete()
    }

    def deletesFilesInsideSymlinkDirWhenNeeded() {
        Assume.assumeTrue(canCreateSymbolicLinkToDirectory())

        given:
        def keepTxt = tmpDir.createFile("originalDir", "keep.txt")
        def originalDir = keepTxt.parentFile
        def link = new File(tmpDir.getTestDirectory(), "link")

        when:
        createSymbolicLink(link, originalDir)

        then:
        link.exists()

        when:
        boolean didWork = delete.delete(deleteSpecActionFor(FOLLOW_SYMLINKS, link)).getDidWork()

        then:
        !link.exists()
        keepTxt.assertDoesNotExist()
        didWork

        cleanup:
        link.delete()
    }

    @Unroll
    def "does not follow symlink to a file when followSymlinks is #followSymlinks"() {
        Assume.assumeTrue(canCreateSymbolicLinkToFile())

        given:
        def originalFile = tmpDir.createFile("originalFile", "keep.txt")
        def link = new File(tmpDir.getTestDirectory(), "link")

        when:
        createSymbolicLink(link, originalFile)

        then:
        link.exists()

        when:
        boolean didWork = delete.delete(deleteSpecActionFor(followSymlinks, link)).getDidWork()

        then:
        !link.exists()
        originalFile.assertExists()
        didWork

        cleanup:
        link.delete()

        where:
        followSymlinks << [true, false]
    }

    private static Action<? super DeleteSpec> deleteSpecActionFor(final boolean followSymlinks, final Object... paths) {
        return new Action<DeleteSpec>() {
            @Override
            void execute(DeleteSpec spec) {
                spec.delete(paths).setFollowSymlinks(followSymlinks)
            }
        }
    }

    protected abstract void createSymbolicLink(File link, TestFile target)

    protected abstract boolean canCreateSymbolicLinkToFile()

    protected abstract boolean canCreateSymbolicLinkToDirectory()
}
