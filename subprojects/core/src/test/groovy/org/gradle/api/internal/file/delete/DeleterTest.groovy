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


import org.gradle.api.file.UnableToDeleteFileException
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.time.Clock
import org.gradle.internal.time.Time
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

import java.util.function.Function

import static org.gradle.api.internal.file.TestFiles.fileSystem
import static org.gradle.util.TextUtil.normaliseLineSeparators
import static org.junit.Assume.assumeTrue

class DeleterTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    FileResolver resolver = TestFiles.resolver(tmpDir.testDirectory)
    Deleter delete = new Deleter(resolver, fileSystem(), Time.clock())

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

    @Unroll
    def "reports reasonable help message when failing to delete single #description"() {

        if (isSymlink) {
            assumeTrue(TestPrecondition.SYMLINKS.isFulfilled())
        }

        given:
        delete = FileTime.deleterWithDeletionAction(resolver) { file ->
            return DeletionAction.FAILURE
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

    def "reports failed to delete child files after failure to delete directory"() {

        given:
        def targetDir = tmpDir.createDir("target")
        def deletable = targetDir.createFile("delete.yes")
        def nonDeletable = targetDir.createFile("delete.no")

        and:
        delete = FileTime.deleterWithDeletionAction(resolver) { file ->
            if (file.canonicalFile == nonDeletable.canonicalFile) {
                return DeletionAction.FAILURE
            }
            return DeletionAction.CONTINUE
        }

        when:
        delete.delete(targetDir)

        then:
        targetDir.assertIsDir()
        deletable.assertDoesNotExist()
        nonDeletable.assertIsFile()

        and:
        def ex = thrown UnableToDeleteFileException
        normaliseLineSeparators(ex.message) == """
            Unable to delete directory '$targetDir'
              ${Deleter.HELP_FAILED_DELETE_CHILDREN}
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
        delete = FileTime.deleterWithDeletionAction(resolver) { file ->
            if (file.canonicalFile == triggerFile.canonicalFile) {
                FileTime.createNewFile(newFile)
            }
            return DeletionAction.CONTINUE
        }

        when:
        delete.delete(targetDir)

        then:
        targetDir.assertIsDir()
        triggerFile.assertDoesNotExist()
        newFile.assertIsFile()

        and:
        def ex = thrown UnableToDeleteFileException
        normaliseLineSeparators(ex.message) == """
            Unable to delete directory '$targetDir'
              ${Deleter.HELP_NEW_CHILDREN}
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
        delete = FileTime.deleterWithDeletionAction(resolver) { file ->
            if (file.canonicalFile == nonDeletable.canonicalFile) {
                FileTime.createNewFile(newFile)
                return DeletionAction.FAILURE
            }
            return DeletionAction.CONTINUE
        }

        when:
        delete.delete(targetDir)

        then:
        targetDir.assertIsDir()
        nonDeletable.assertIsFile()
        newFile.assertIsFile()

        and:
        def ex = thrown UnableToDeleteFileException
        normaliseLineSeparators(ex.message) == """
            Unable to delete directory '$targetDir'
              ${Deleter.HELP_FAILED_DELETE_CHILDREN}
              - $nonDeletable
              ${Deleter.HELP_NEW_CHILDREN}
              - $newFile
        """.stripIndent().trim()
    }

    def "fails fast and reports a reasonable number of paths after failure to delete directory"() {

        given: 'more existing files than the cap'
        def targetDir = tmpDir.createDir("target")
        def tooManyRange = (1..(Deleter.MAX_REPORTED_PATHS + 10))
        def nonDeletableFiles = tooManyRange.collect { targetDir.createFile("zzz-${it}-zzz.txt") }
        FileTime.makeOld(nonDeletableFiles + targetDir)

        and: 'a deleter that cannot delete, records deletion requests and creates new files'
        def triedToDelete = [] as Set<File>
        def newFiles = tooManyRange.collect { targetDir.file("aaa-${it}-aaa.txt") }
        delete = FileTime.deleterWithDeletionAction(resolver) { file ->
            triedToDelete << file
            newFiles.each { FileTime.createNewFile(it) }
            return DeletionAction.FAILURE
        }

        when:
        delete.delete(targetDir)

        then: 'nothing gets deleted'
        targetDir.assertIsDir()
        nonDeletableFiles.each { it.assertIsFile() }
        newFiles.each { it.assertIsFile() }

        and: 'it failed fast'
        triedToDelete.size() == Deleter.MAX_REPORTED_PATHS

        and: 'the report size is capped'
        def ex = thrown UnableToDeleteFileException
        def normalizedMessage = normaliseLineSeparators(ex.message)
        normalizedMessage.startsWith("""
            Unable to delete directory '$targetDir'
              ${Deleter.HELP_FAILED_DELETE_CHILDREN}
              - $targetDir${File.separator}zzz-
        """.stripIndent().trim())
        normalizedMessage.contains("-zzz.txt\n  " + """
              - and more ...
              ${Deleter.HELP_NEW_CHILDREN}
              - $targetDir${File.separator}aaa-
        """.stripIndent(12).trim())
        normalizedMessage.endsWith("-aaa.txt\n  - and more ...")
        normalizedMessage.readLines().size() == Deleter.MAX_REPORTED_PATHS * 2 + 5
    }

    class FileTime {

        static int oldTime = 1000
        static int startTime = oldTime + 2000
        static int newTime = startTime + 2000

        static Clock clock = new Clock() {
            @Override
            long getCurrentTime() {
                return startTime
            }
        }

        static Deleter deleterWithDeletionAction(FileResolver resolver, Function<File, DeletionAction> deletionAction) {
            new Deleter(resolver, fileSystem(), clock) {
                @Override
                protected boolean deleteFile(File file) {
                    switch (deletionAction.apply(file)) {
                        case DeletionAction.SUCCESS:
                            return true
                        case DeletionAction.FAILURE:
                            return false
                        case DeletionAction.CONTINUE:
                            return super.deleteFile(file)
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
        FAILURE, SUCCESS, CONTINUE
    }
}
