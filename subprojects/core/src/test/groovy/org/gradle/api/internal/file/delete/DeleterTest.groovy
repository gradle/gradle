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
package org.gradle.api.internal.file.delete

import org.gradle.api.Action
import org.gradle.api.file.DeleteSpec
import org.gradle.api.file.UnableToDeleteFileException
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.TestFiles
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.api.internal.file.TestFiles.fileSystem

class DeleterTest extends Specification {
    static final boolean FOLLOW_SYMLINKS = true;

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    FileResolver resolver = TestFiles.resolver(tmpDir.testDirectory)
    Deleter delete = new Deleter(resolver, fileSystem())

    def deletesDirectory() {
        given:
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
        TestFile dir = tmpDir.file("unknown");
        dir.assertDoesNotExist();

        when:
        boolean didWork = delete.delete(dir);

        then:
        !didWork
    }

    @Requires([TestPrecondition.UNIX_DERIVATIVE])
    def doesNotDeleteFilesInsideSymlinkDir() {
        given:
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

    @Requires([TestPrecondition.UNIX_DERIVATIVE])
    def deletesFilesInsideSymlinkDirWhenNeeded() {
        given:
        def keepTxt = tmpDir.createFile("originalDir", "keep.txt")
        def originalDir = keepTxt.parentFile
        def link = new File(tmpDir.getTestDirectory(), "link")

        when:
        fileSystem().createSymbolicLink(link, originalDir)

        then:
        link.exists()

        when:
        boolean didWork = delete.delete(deleteAction(FOLLOW_SYMLINKS, link)).getDidWork()

        then:
        !link.exists()
        keepTxt.assertDoesNotExist()
        didWork
    }

    @Unroll
    def "does not follow symlink to a file when followSymlinks is #followSymlinks"() {
        given:
        def originalFile = tmpDir.createFile("originalFile", "keep.txt")
        def link = new File(tmpDir.getTestDirectory(), "link")

        when:
        fileSystem().createSymbolicLink(link, originalFile)

        then:
        link.exists()

        when:
        boolean didWork = delete.delete(deleteAction(followSymlinks, link)).getDidWork()

        then:
        !link.exists()
        originalFile.assertExists()
        didWork

        where:
        followSymlinks << [true, false]
    }

    @Unroll
    def "report reasonable help message when failing to delete single #description"() {

        given:
        delete = new Deleter(resolver, fileSystem()) {
            @Override
            protected boolean deleteFile(File file) {
                return false
            }
        }

        and:
        def target = isDirectory ? tmpDir.createDir("target") : tmpDir.createFile("target")
        target = isSymlink ? tmpDir.file("link").tap { fileSystem().createSymbolicLink(delegate, target) } : target

        when:
        delete.delete(target)

        then:
        def ex = thrown UnableToDeleteFileException
        ex.message == "Unable to delete $description '$target'"

        where:
        description            | isDirectory | isSymlink
        "file"                 | false       | false
        "directory"            | true        | false
        "symlink to file"      | false       | true
        "symlink to directory" | true        | true
    }

    def "report failed to delete child paths after failure to delete directory"() {
        given:
        def targetDir = tmpDir.createDir("target")
        def nonDeletable = targetDir.createFile("delete.no")

        and:
        delete = new Deleter(resolver, fileSystem()) {
            @Override
            protected boolean deleteFile(File file) {
                if (file.canonicalFile == nonDeletable.canonicalFile) {
                    return false
                }
                return super.deleteFile(file)
            }
        }

        when:
        delete.delete(targetDir)

        then:
        targetDir.assertIsDir()
        nonDeletable.assertIsFile()

        and:
        def ex = thrown UnableToDeleteFileException
        ex.message == """
            Unable to delete directory '$targetDir'
              Child paths failed to delete! Is something holding files in the target directory?
              - $nonDeletable
        """.stripIndent().trim()
    }

    def "report new child paths after failure to delete directory"() {
        given:
        def targetDir = tmpDir.createDir("target")
        def triggerFile = targetDir.createFile("zzz.txt")
        def newFile = targetDir.file("aaa.txt")

        and:
        delete = new Deleter(resolver, fileSystem()) {
            @Override
            protected boolean deleteFile(File file) {
                if (file.canonicalFile == triggerFile.canonicalFile) {
                    newFile.text = ""
                }
                return super.deleteFile(file)
            }
        }

        when:
        delete.delete(targetDir)

        then:
        targetDir.assertIsDir()
        triggerFile.assertDoesNotExist()
        newFile.assertIsFile()

        and:
        def ex = thrown UnableToDeleteFileException
        ex.message == """
            Unable to delete directory '$targetDir'
              More files were found after failure! Is something concurrently writing into the target directory?
              - $newFile
        """.stripIndent().trim()
    }

    def "report both failed to delete child paths and new child paths after failure to delete directory, deduplicated"() {
        given:
        def targetDir = tmpDir.createDir("target")
        def nonDeletable = targetDir.createFile("delete.no")
        def newFile = targetDir.file("aaa.txt")

        and:
        delete = new Deleter(resolver, fileSystem()) {
            @Override
            protected boolean deleteFile(File file) {
                if (file.canonicalFile == nonDeletable.canonicalFile) {
                    newFile.text = ""
                    return false
                }
                return super.deleteFile(file)
            }
        }

        when:
        delete.delete(targetDir)

        then:
        targetDir.assertIsDir()
        nonDeletable.assertIsFile()
        newFile.assertIsFile()

        and:
        def ex = thrown UnableToDeleteFileException
        ex.message == """
            Unable to delete directory '$targetDir'
              Child paths failed to delete! Is something holding files in the target directory?
              - $nonDeletable
              More files were found after failure! Is something concurrently writing into the target directory?
              - $newFile
        """.stripIndent().trim()
    }

    def Action<? super DeleteSpec> deleteAction(final boolean followSymlinks, final Object... paths) {
        return new Action<DeleteSpec>() {
            @Override
            void execute(DeleteSpec spec) {
                spec.delete(paths).setFollowSymlinks(followSymlinks)
            }
        }
    }
}
