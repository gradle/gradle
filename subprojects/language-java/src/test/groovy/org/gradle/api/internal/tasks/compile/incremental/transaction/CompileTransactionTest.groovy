/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.transaction

import org.gradle.api.internal.file.TestFiles
import org.gradle.api.tasks.WorkResults
import org.gradle.api.tasks.util.PatternSet
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ExecutionException
import java.util.stream.Stream

import static com.google.common.base.Preconditions.checkNotNull

class CompileTransactionTest extends Specification {

    private static final DID_WORK = WorkResults.didWork(true)

    @TempDir
    File temporaryFolder
    File transactionDir
    CompileTransaction transaction

    def setup() {
        transactionDir = new File(temporaryFolder, "transactionDir")
        transactionDir.mkdir()
        transaction = CompileTransaction.newTransaction(transactionDir, TestFiles.fileOperations(temporaryFolder), TestFiles.deleter())
    }

    def "transaction base directory is cleared before execution"() {
        fileInTransactionDir("some-dummy-file.txt").createNewFile()
        fileInTransactionDir("sub-dir").mkdir()

        when:
        assert !isEmptyDirectory(transactionDir)
        def isTransactionDirEmpty = transaction.execute {
            return isEmptyDirectory(transactionDir)
        }

        then:
        isTransactionDirEmpty
    }

    def "files are stashed and restored on failure"() {
        def sourceDir = file("sourceDir")
        sourceDir.mkdir()
        new File(sourceDir, "file.txt").createNewFile()
        new File(sourceDir, "subDir").mkdir()
        new File(sourceDir, "subDir/another-file.txt").createNewFile()
        def pattern = new PatternSet().include("**/*.txt")
        def stashDir = null

        when:
        transaction.newTransactionalDirectory {
            it.stashFilesForRollbackOnFailure(pattern, sourceDir)
            stashDir = it.getAsFile()
        }.execute {
            assert hasOnlyDirectories(sourceDir)
            assert stashDir.list()
                .collect { it.replaceAll(".uniqueId.*", "")}
                .containsAll(["file.txt", "another-file.txt"])
            throw new RuntimeException("Exception")
        }

        then:
        thrown(RuntimeException)
        sourceDir.list() as Set ==~ ["file.txt", "subDir"]
        new File(sourceDir,"subDir").list() as Set ==~ ["another-file.txt"]
    }

    def "files are stashed but not restored on success"() {
        def sourceDir = file("sourceDir")
        sourceDir.mkdir()
        new File(sourceDir, "file.txt").createNewFile()
        def pattern = new PatternSet().include("**/*.txt")
        def stashDir = null

        when:
        transaction.newTransactionalDirectory {
            it.stashFilesForRollbackOnFailure(pattern, sourceDir)
            stashDir = it.getAsFile()
        }.execute {
            assert isEmptyDirectory(sourceDir)
            assert stashDir.list() as Set ==~ ["file.txt.uniqueId0"]
            return DID_WORK
        }

        then:
        isEmptyDirectory(sourceDir)
    }

    def "files are stashed but not restored if exception doesn't match"() {
        def sourceDir = file("sourceDir")
        sourceDir.mkdir()
        new File(sourceDir, "file.txt").createNewFile()
        def pattern = new PatternSet().include("**/*.txt")
        def stashDir = null

        when:
        transaction.newTransactionalDirectory {
            it.stashFilesForRollbackOnFailure(pattern, sourceDir)
            stashDir = it.getAsFile()
        }.onFailureRollbackStashIfException {
            it instanceof ExecutionException
        }.execute {
            assert isEmptyDirectory(sourceDir)
            assert stashDir.list() as Set ==~ ["file.txt.uniqueId0"]
            throw new RuntimeException("Exception")
        }

        then:
        thrown(RuntimeException)
        isEmptyDirectory(sourceDir)
    }

    def "if something get stashed workResult passed to execution will mark 'did work'"() {
        def sourceDir = file("sourceDir")
        sourceDir.mkdir()
        new File(sourceDir, "file.txt").createNewFile()
        def pattern = new PatternSet().include("**/*.txt")
        def stashDir = null

        when:
        def workResult = transaction.newTransactionalDirectory {
            it.stashFilesForRollbackOnFailure(pattern, sourceDir)
            stashDir = it.getAsFile()
        }.execute {
            assert isEmptyDirectory(sourceDir)
            assert !isEmptyDirectory(stashDir)
            return it
        }

        then:
        workResult == WorkResults.didWork(true)
    }

    def "if nothing get stashed workResult passed to execution will mark 'did no work'"() {
        def workResult = transaction.execute {
            return it
        }

        expect:
        workResult == WorkResults.didWork(false)
    }

    def "nothing is stashed and directory is not created if #description"() {
        File directory = null
        if (directoryPath) {
            directory = file(directoryPath)
            directory.mkdir()
            new File(directory, "test-file.txt").createNewFile()
        }
        def stashDir = null

        when:
        def stashDirExists = transaction.newTransactionalDirectory {
            it.stashFilesForRollbackOnFailure(pattern, directory)
            stashDir = it.getAsFile()
        }.execute {
            stashDir.exists()
        }

        then:
        !stashDirExists

        where:
        pattern                              | directoryPath | description
        new PatternSet()                     | "sourceDir"   | "empty pattern"
        new PatternSet().include("**/*.txt") | null          | "null directory"
    }

    def "on success all files in stash dir are moved to a specific directory"() {
        def destinationDir = file("someDir")
        destinationDir.mkdir()
        def stashDir = null

        when:
        transaction.newTransactionalDirectory {
            it.onSuccessMoveFilesTo(destinationDir)
            stashDir = it.getAsFile()
        }.execute {
            new File(stashDir, "file.txt").createNewFile()
            new File(stashDir, "subDir").mkdir()
            new File(stashDir, "subDir/another-file.txt").createNewFile()
            return DID_WORK
        }

        then:
        destinationDir.list() as Set ==~ ["file.txt", "subDir"]
        new File(destinationDir,"subDir").list() as Set ==~ ["another-file.txt"]
    }

    def "on failure files are not moved to a specific directory"() {
        def destinationDir = file("someDir")
        destinationDir.mkdir()
        def stashDir = null

        when:
        transaction.newTransactionalDirectory {
            it.onSuccessMoveFilesTo(destinationDir)
            stashDir = it.getAsFile()
        }.execute {
            new File(stashDir, "file.txt").createNewFile()
            new File(stashDir, "subDir").mkdir()
            new File(stashDir, "subDir/another-file.txt").createNewFile()
            throw new RuntimeException("Exception")
        }

        then:
        thrown(RuntimeException)
        isEmptyDirectory(destinationDir)
    }

    def "before execution registered action is called"() {
        boolean wasCalled = false

        when:
        def wasCalledBeforeExecution = transaction.newTransactionalDirectory {
            it.beforeExecutionDo(() -> wasCalled = true)
        }.execute {
            return wasCalled
        }

        then:
        wasCalledBeforeExecution
        wasCalled
    }

    def "after execution 'always do' action is called on success"() {
        boolean wasCalled = false

        when:
        def wasCalledBeforeExecution = transaction.newTransactionalDirectory {
            it.afterExecutionAlwaysDo(() -> wasCalled = true)
        }.execute {
            return wasCalled
        }

        then:
        !wasCalledBeforeExecution
        wasCalled
    }

    def "after execution 'always do' action is called on failure"() {
        boolean wasCalled = false

        when:
        transaction.newTransactionalDirectory {
            it.afterExecutionAlwaysDo(() -> wasCalled = true)
        }.execute {
            throw new RuntimeException("Exception")
        }

        then:
        thrown(RuntimeException)
        wasCalled
    }

    def "unique folder is generated for every transactional directory"() {
        when:
        def directories = transaction.newTransactionalDirectory {
            it.createDirectoryBeforeExecution()
        }.newTransactionalDirectory {
            it.withNamePrefix("test").createDirectoryBeforeExecution()
        }.newTransactionalDirectory {
            it.withNamePrefix("test").createDirectoryBeforeExecution()
        }.execute {
            return transactionDir.list()
        }

        then:
        directories as Set ==~ ["dir-uniqueId0", "test-dir-uniqueId1", "test-dir-uniqueId2"]
    }

    private File fileInTransactionDir(String path) {
        return new File(checkNotNull(transactionDir), path)
    }

    private File file(String path) {
        return new File(checkNotNull(temporaryFolder) as File, path)
    }

    private boolean isEmptyDirectory(File file) {
        file.listFiles().length == 0
    }

    private boolean hasOnlyDirectories(File file) {
        try (Stream<Path> stream = Files.walk(file.toPath())) {
            return stream.allMatch { Files.isDirectory(it) }
        }
    }
}
