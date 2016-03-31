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
package org.gradle.api.internal.file.copy

import org.gradle.api.file.DeleteAction
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.TestFiles
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.internal.file.TestFiles.fileSystem

class DeleteActionImplTest extends Specification {
    static final boolean FOLLOW_SYMLINKS = true;

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    FileResolver resolver = TestFiles.resolver(tmpDir.testDirectory)

    def deletesDirectory() {
        given:
        DeleteAction delete = new DeleteActionImpl(resolver, fileSystem())
        TestFile dir = tmpDir.getTestDirectory();
        dir.file("somefile").createFile();

        when:
        boolean didWork = delete.delete(dir);

        then:
        dir.assertDoesNotExist();
        didWork
    }

    def deletesFile() {
        given:
        DeleteAction delete = new DeleteActionImpl(resolver, fileSystem())
        TestFile dir = tmpDir.getTestDirectory();
        TestFile file = dir.file("somefile");
        file.createFile();

        when:
        boolean didWork = delete.delete(file);

        then:
        file.assertDoesNotExist();
        didWork
    }

    def deletesFileByPath() {
        given:
        DeleteAction delete = new DeleteActionImpl(resolver, fileSystem())
        TestFile dir = tmpDir.getTestDirectory();
        TestFile file = dir.file("somefile");
        file.createFile();

        when:
        boolean didWork = delete.delete('somefile');

        then:
        file.assertDoesNotExist();
        didWork
    }

    def deletesMultipleTargets() {
        given:
        DeleteAction delete = new DeleteActionImpl(resolver, fileSystem())
        TestFile file = tmpDir.getTestDirectory().file("somefile").createFile();
        TestFile dir = tmpDir.getTestDirectory().file("somedir").createDir();
        dir.file("sub/child").createFile();

        when:
        boolean didWork = delete.delete(file, dir);

        then:
        file.assertDoesNotExist();
        dir.assertDoesNotExist();
        didWork
    }

    def didWorkIsFalseWhenNothingDeleted() {
        given:
        DeleteAction delete = new DeleteActionImpl(resolver, fileSystem())
        TestFile dir = tmpDir.file("unknown");
        dir.assertDoesNotExist();

        when:
        boolean didWork = delete.delete(dir);

        then:
        !didWork
    }

    @Requires([TestPrecondition.UNIX_DERIVATIVE, TestPrecondition.JDK7_OR_LATER])
    def doesNotDeleteFilesInsideSymlinkDir() {
        given:
        DeleteAction delete = new DeleteActionImpl(resolver, fileSystem())
        def keepTxt = tmpDir.createFile("originalDir", "keep.txt")
        def originalDir = keepTxt.parentFile
        def link = new File(tmpDir.getTestDirectory(), "link")

        when:
        fileSystem().createSymbolicLink(link, originalDir)

        then:
        link.exists()

        when:
        boolean didWork = delete.delete(link)

        then:
        !link.exists()
        originalDir.assertExists()
        keepTxt.assertExists()
        didWork
    }

    @Requires([TestPrecondition.UNIX_DERIVATIVE, TestPrecondition.JDK7_OR_LATER])
    def deletesFilesInsideSymlinkDirWhenNeeded() {
        given:
        DeleteAction delete = new DeleteActionImpl(resolver, fileSystem(), FOLLOW_SYMLINKS)
        def keepTxt = tmpDir.createFile("originalDir", "keep.txt")
        def originalDir = keepTxt.parentFile
        def link = new File(tmpDir.getTestDirectory(), "link")

        when:
        fileSystem().createSymbolicLink(link, originalDir)

        then:
        link.exists()

        when:
        boolean didWork = delete.delete(link)

        then:
        !link.exists()
        keepTxt.assertDoesNotExist()
        didWork
    }
}
