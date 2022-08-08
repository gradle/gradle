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

package org.gradle.internal.file.impl

import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.junit.Assume
import org.junit.Rule
import spock.lang.Specification

abstract class AbstractSymlinkDeleterTest extends Specification {
    static final boolean FOLLOW_SYMLINKS = true

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    DefaultDeleter deleter = new DefaultDeleter(
        { System.currentTimeMillis() },
        { NativeServicesTestFixture.getInstance().get(FileSystem).isSymlink(it) },
        false
    )

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
        boolean didWork = delete(false, link)

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
        boolean didWork = delete(FOLLOW_SYMLINKS, link)

        then:
        !link.exists()
        keepTxt.assertDoesNotExist()
        didWork

        cleanup:
        link.delete()
    }

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
        boolean didWork = delete(followSymlinks, link)

        then:
        !link.exists()
        originalFile.assertExists()
        didWork

        cleanup:
        link.delete()

        where:
        followSymlinks << [true, false]
    }

    private boolean delete(boolean followSymlinks, File path) {
        return deleter.deleteRecursively(path, followSymlinks)
    }

    protected abstract void createSymbolicLink(File link, TestFile target)

    protected abstract boolean canCreateSymbolicLinkToFile()

    protected abstract boolean canCreateSymbolicLinkToDirectory()
}
