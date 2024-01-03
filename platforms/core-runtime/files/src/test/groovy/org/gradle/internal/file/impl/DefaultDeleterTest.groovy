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
package org.gradle.internal.file.impl

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.precondition.TestPrecondition
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule
import spock.lang.Specification

import java.nio.file.Files
import java.util.function.Function

import static org.gradle.util.internal.TextUtil.normaliseLineSeparators
import static org.junit.Assume.assumeTrue

class DefaultDeleterTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    DefaultDeleter deleter = new DefaultDeleter(
        { System.currentTimeMillis() },
        { File file -> Files.isSymbolicLink(file.toPath()) },
        false
    )

    def "deletes directory"() {
        given:
        TestFile dir = tmpDir.getTestDirectory()
        dir.file("someFile").createFile()

        when:
        boolean didWork = deleter.deleteRecursively(dir)

        then:
        dir.assertDoesNotExist()
        didWork
    }

    def "deletes file"() {
        given:
        TestFile dir = tmpDir.getTestDirectory()
        TestFile file = dir.file("someFile")
        file.createFile()

        when:
        boolean didWork = deleter.deleteRecursively(file)

        then:
        file.assertDoesNotExist()
        didWork
    }

    def "cleans non-empty directory"() {
        given:
        TestFile dir = tmpDir.getTestDirectory()
        dir.file("someFile").createFile()

        when:
        boolean didWork = deleter.ensureEmptyDirectory(dir)

        then:
        dir.assertIsEmptyDir()
        didWork
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "cleans symlinked target directory"() {
        def linked = tmpDir.createDir("linked")
        def content = linked.createFile("content.txt")
        def target = tmpDir.file("target").tap { Files.createSymbolicLink(it.toPath(), linked.toPath()) }

        when:
        deleter.ensureEmptyDirectory(target, true)

        then:
        target.assertIsEmptyDir()
        linked.assertIsEmptyDir()
        Files.readSymbolicLink(target.toPath()) == linked.toPath()
        content.assertDoesNotExist()
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "recreates target directory when symlink is found, leaving linked content untouched"() {
        def linked = tmpDir.createDir("linked")
        def content = linked.createFile("content.txt")
        def target = tmpDir.file("target").tap { Files.createSymbolicLink(it.toPath(), linked.toPath()) }

        when:
        deleter.ensureEmptyDirectory(target, false)

        then:
        target.assertIsEmptyDir()
        !Files.isSymbolicLink(target.toPath())
        linked.assertHasDescendants(content.getName())
        content.assertIsFile()
    }

    def "creates directory in place of file"() {
        given:
        TestFile dir = tmpDir.getTestDirectory()
        def file = dir.file("someFile").createFile()

        when:
        boolean didWork = deleter.ensureEmptyDirectory(file)

        then:
        file.assertIsDir()
        didWork
    }

    def "creates directory if nothing existed before"() {
        given:
        TestFile dir = tmpDir.getTestDirectory()
        def file = dir.file("someFile")

        when:
        boolean didWork = deleter.ensureEmptyDirectory(file)

        then:
        file.assertIsDir()
        didWork
    }

    def "didWork is false when nothing has been deleted"() {
        given:
        TestFile dir = tmpDir.file("unknown")
        dir.assertDoesNotExist()

        when:
        boolean didWork = deleter.deleteRecursively(dir)

        then:
        !didWork
    }

    def "didWork is false when nothing has been cleaned"() {
        given:
        TestFile emptyDir = tmpDir.getTestDirectory()

        when:
        boolean didWork = deleter.ensureEmptyDirectory(emptyDir)

        then:
        !didWork
    }

    def "reports reasonable help message when failing to delete single #description"() {
        if (isSymlink) {
            assumeTrue(TestPrecondition.satisfied(UnitTestPreconditions.Symlinks))
        }

        given:
        deleter = FileTime.deleterWithDeletionAction() { file ->
            return DeletionAction.FAILURE
        }

        and:
        def target = isDirectory ? tmpDir.createDir("target") : tmpDir.createFile("target")
        target = isSymlink ? tmpDir.file("link").tap { Files.createSymbolicLink(delegate.toPath(), target.toPath()) } : target

        when:
        deleter.deleteRecursively(target)

        then:
        def ex = thrown IOException
        ex.message == "Unable to delete $description '$target'"

        where:
        description            | isDirectory | isSymlink
        "file"                 | false       | false
        "directory"            | true        | false
        "symlink to file"      | false       | true
        "symlink to directory" | true        | true
    }

    def "reports root cause when failing to delete a file"() {
        given:
        deleter = FileTime.deleterWithDeletionAction() { file ->
            return DeletionAction.EXCEPTION
        }

        and:
        def target = tmpDir.createFile("target")

        when:
        deleter.deleteRecursively(target)

        then:
        def ex = thrown IOException
        ex.message == "Unable to delete file '$target'"
        ex.suppressed.collect { it.message } == ["ROOT CAUSE"]
    }

    def "reports failed to delete child files and reports a reasonable number of retries after failure to delete directory"() {

        given:
        def targetDir = tmpDir.createDir("target")
        def deletable = targetDir.createFile("delete.yes")
        def nonDeletable = targetDir.createFile("delete.no")

        and:
        def failedAttempts = 0
        deleter = FileTime.deleterWithDeletionAction() { file ->
            if (file.canonicalFile == nonDeletable.canonicalFile) {
                failedAttempts++
                return DeletionAction.FAILURE
            }
            return DeletionAction.CONTINUE
        }

        when:
        deleter.deleteRecursively(targetDir)

        then:
        targetDir.assertIsDir()
        deletable.assertDoesNotExist()
        nonDeletable.assertIsFile()

        and:
        failedAttempts == DefaultDeleter.EMPTY_DIRECTORY_DELETION_ATTEMPTS

        and:
        def ex = thrown IOException
        normaliseLineSeparators(ex.message) == """
            Unable to delete directory '$targetDir'
              ${DefaultDeleter.HELP_FAILED_DELETE_CHILDREN}
              - $nonDeletable
        """.stripIndent().trim()
    }

    def "reports failed to delete child files after failure to delete directory"() {

        given:
        def targetDir = tmpDir.createDir("target")
        def deletable = targetDir.createFile("delete.yes")
        def nonDeletable = targetDir.createFile("delete.no")

        and:
        deleter = FileTime.deleterWithDeletionAction() { file ->
            file.canonicalFile == nonDeletable.canonicalFile
                ? DeletionAction.FAILURE
                : DeletionAction.CONTINUE
        }

        when:
        deleter.deleteRecursively(targetDir)

        then:
        targetDir.assertIsDir()
        deletable.assertDoesNotExist()
        nonDeletable.assertIsFile()

        and:
        def ex = thrown IOException
        normaliseLineSeparators(ex.message) == """
            Unable to delete directory '$targetDir'
              ${DefaultDeleter.HELP_FAILED_DELETE_CHILDREN}
              - $nonDeletable
        """.stripIndent().trim()
    }

    def "reports new child files after failure to delete directory"() {

        given:
        def targetDir = tmpDir.createDir("target")
        def triggerFile = targetDir.createFile("zzz.txt")
        FileTime.makeOld(targetDir, triggerFile)

        and:
        def newFile = targetDir.file("aaa.txt")
        deleter = FileTime.deleterWithDeletionAction() { file ->
            if (file.canonicalFile == triggerFile.canonicalFile) {
                FileTime.createNewFile(newFile)
            }
            return DeletionAction.CONTINUE
        }

        when:
        deleter.deleteRecursively(targetDir)

        then:
        targetDir.assertIsDir()
        triggerFile.assertDoesNotExist()
        newFile.assertIsFile()

        and:
        def ex = thrown IOException
        normaliseLineSeparators(ex.message) == """
            Unable to delete directory '$targetDir'
              ${DefaultDeleter.HELP_NEW_CHILDREN}
              - $newFile
        """.stripIndent().trim()
    }

    def "reports both failed to delete and new child files after failure to delete directory"() {
        given:
        def targetDir = tmpDir.createDir("target")
        def nonDeletable = targetDir.createFile("delete.no")
        FileTime.makeOld(targetDir, nonDeletable)

        and:
        def newFile = targetDir.file("aaa.txt")
        deleter = FileTime.deleterWithDeletionAction() { file ->
            if (file.canonicalFile == nonDeletable.canonicalFile) {
                FileTime.createNewFile(newFile)
                return DeletionAction.FAILURE
            }
            return DeletionAction.CONTINUE
        }

        when:
        deleter.deleteRecursively(targetDir)

        then:
        targetDir.assertIsDir()
        nonDeletable.assertIsFile()
        newFile.assertIsFile()

        and:
        def ex = thrown IOException
        normaliseLineSeparators(ex.message) == """
            Unable to delete directory '$targetDir'
              ${DefaultDeleter.HELP_FAILED_DELETE_CHILDREN}
              - $nonDeletable
              ${DefaultDeleter.HELP_NEW_CHILDREN}
              - $newFile
        """.stripIndent().trim()
    }

    def "fails fast and reports a reasonable number of paths after failure to delete directory"() {

        given: 'more existing files than the cap'
        def targetDir = tmpDir.createDir("target")
        def tooManyRange = (1..(DefaultDeleter.MAX_REPORTED_PATHS + 10))
        def nonDeletableFiles = tooManyRange.collect { targetDir.createFile("zzz-${it}-zzz.txt") }
        FileTime.makeOld(nonDeletableFiles + targetDir)

        and: 'a deleter that cannot delete, records deletion requests and creates new files'
        def triedToDelete = [] as Set<File>
        def newFiles = tooManyRange.collect { targetDir.file("aaa-${it}-aaa.txt") }
        deleter = FileTime.deleterWithDeletionAction() { file ->
            triedToDelete << file
            newFiles.each { FileTime.createNewFile(it) }
            return DeletionAction.FAILURE
        }

        when:
        deleter.deleteRecursively(targetDir)

        then: 'nothing gets deleted'
        targetDir.assertIsDir()
        nonDeletableFiles.each { it.assertIsFile() }
        newFiles.each { it.assertIsFile() }

        and: 'it failed fast'
        triedToDelete.size() == DefaultDeleter.MAX_REPORTED_PATHS

        and: 'the report size is capped'
        def ex = thrown IOException
        def normalizedMessage = normaliseLineSeparators(ex.message)
        normalizedMessage.startsWith("""
            Unable to delete directory '$targetDir'
              ${DefaultDeleter.HELP_FAILED_DELETE_CHILDREN}
              - $targetDir${File.separator}zzz-
        """.stripIndent().trim())
        normalizedMessage.contains("-zzz.txt\n  " + """
              - and more ...
              ${DefaultDeleter.HELP_NEW_CHILDREN}
              - $targetDir${File.separator}aaa-
        """.stripIndent(12).trim())
        normalizedMessage.endsWith("-aaa.txt\n  - and more ...")
        normalizedMessage.readLines().size() == DefaultDeleter.MAX_REPORTED_PATHS * 2 + 5
    }

    class FileTime {

        static long oldTime = 1000
        static long startTime = oldTime + 2000
        static long newTime = startTime + 2000

        static DefaultDeleter deleterWithDeletionAction(Function<File, DeletionAction> deletionAction) {
            new DefaultDeleter(
                { startTime },
                { File file -> Files.isSymbolicLink(file.toPath()) },
                false
            ) {
                @Override
                protected DefaultDeleter.FileDeletionResult deleteFile(File file) {
                    switch (deletionAction.apply(file)) {
                        case DeletionAction.EXCEPTION:
                            return DefaultDeleter.FileDeletionResult.withException(new Exception("ROOT CAUSE"))
                        case DeletionAction.FAILURE:
                            return DefaultDeleter.FileDeletionResult.withoutException(false)
                        case DeletionAction.CONTINUE:
                            return super.deleteFile(file)
                        default:
                            throw new AssertionError()
                    }
                }
            }
        }

        static void makeOld(Iterable<File> files) {
            makeOld(files as File[])
        }

        static void makeOld(File... files) {
            files.each { it.setLastModified(oldTime) }
        }

        static void createNewFile(File file) {
            file.tap {
                text = ""
                setLastModified(newTime)
            }
        }
    }

    private static enum DeletionAction {
        EXCEPTION, FAILURE, CONTINUE
    }
}
