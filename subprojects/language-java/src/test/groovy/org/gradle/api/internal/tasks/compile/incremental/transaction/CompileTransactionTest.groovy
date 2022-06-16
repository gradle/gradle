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
import java.util.stream.Stream

import static com.google.common.base.Preconditions.checkNotNull

class CompileTransactionTest extends Specification {

    private static final DID_WORK = WorkResults.didWork(true)

    @TempDir
    File temporaryFolder
    File transactionDir
    File stashDir
    CompileTransaction.CompileTransactionBuilder transactionBuilder

    def setup() {
        transactionDir = new File(temporaryFolder, "transactionDir")
        transactionDir.mkdir()
        stashDir = new File(transactionDir, "stash-dir")
        transactionBuilder = CompileTransaction.builder(transactionDir, TestFiles.fileOperations(temporaryFolder), TestFiles.deleter())
    }

    def "transaction base directory is cleared before execution"() {
        fileInTransactionDir("some-dummy-file.txt").createNewFile()
        fileInTransactionDir("sub-dir").mkdir()

        when:
        assert !hasOnlyDirectories(transactionDir)
        def isTransactionDirEmpty = transactionBuilder
            .build()
            .execute {
                return hasOnlyDirectories(transactionDir)
            }

        then:
        isTransactionDirEmpty
    }

    def "files are stashed and restored on failure"() {
        def sourceDir = createNewDirectory(file("sourceDir"))
        createNewFile(new File(sourceDir, "file.txt"))
        createNewFile(new File(sourceDir, "subDir/another-file.txt"))
        def pattern = new PatternSet().include("**/*.txt")

        when:
        transactionBuilder.stashFiles(pattern, sourceDir)
            .build()
            .execute {
                assert hasOnlyDirectories(sourceDir)
                assert stashDir.list()
                    .collect { it.replaceAll(".uniqueId.*", "") }
                    .containsAll(["file.txt", "another-file.txt"])
                throw new RuntimeException("Exception")
            }

        then:
        thrown(RuntimeException)
        sourceDir.list() as Set ==~ ["file.txt", "subDir"]
        new File(sourceDir,"subDir").list() as Set ==~ ["another-file.txt"]
    }

    def "files are stashed but not restored on success"() {
        def sourceDir = createNewDirectory(file("sourceDir"))
        new File(sourceDir, "file.txt").createNewFile()
        def pattern = new PatternSet().include("**/*.txt")

        when:
        transactionBuilder.stashFiles(pattern, sourceDir)
            .build()
            .execute {
                assert isEmptyDirectory(sourceDir)
                assert stashDir.list() as Set ==~ ["file.txt.uniqueId0"]
                return DID_WORK
            }

        then:
        isEmptyDirectory(sourceDir)
    }

    def "if something get stashed workResult passed to execution will mark 'did work'"() {
        def sourceDir = createNewDirectory(file("sourceDir"))
        new File(sourceDir, "file.txt").createNewFile()
        def pattern = new PatternSet().include("**/*.txt")

        when:
        def workResult = transactionBuilder.stashFiles(pattern, sourceDir)
            .build()
            .execute {
                assert isEmptyDirectory(sourceDir)
                assert !isEmptyDirectory(stashDir)
                return it
            }

        then:
        workResult == WorkResults.didWork(true)
    }

    def "if nothing get stashed workResult passed to execution will mark 'did no work'"() {
        def workResult = transactionBuilder
            .build()
            .execute {
                return it
            }

        expect:
        workResult == WorkResults.didWork(false)
    }

    def "nothing is stashed and directory is not created if #description"() {
        File directory = null
        if (directoryPath) {
            directory = createNewDirectory(file(directoryPath))
            createNewFile(new File(directory, "test-file.txt"))
        }

        when:
        def stashDirIsEmpty = transactionBuilder.stashFiles(pattern, directory)
            .build()
            .execute {
                isEmptyDirectory(stashDir)
            }

        then:
        stashDirIsEmpty

        where:
        pattern                              | directoryPath | description
        new PatternSet()                     | "sourceDir"   | "empty pattern"
        new PatternSet().include("**/*.txt") | null          | "null directory"
    }

    def "on success all files are moved from stage dir to an output directory"() {
        def outputDir = createNewDirectory(file("someDir"))
        def stageDir = null

        when:
        transactionBuilder.stageOutputDirectory(outputDir) { stageDir = it }
            .build()
            .execute {
                new File(stageDir, "file.txt").createNewFile()
                new File(stageDir, "subDir").mkdir()
                new File(stageDir, "subDir/another-file.txt").createNewFile()
                return DID_WORK
            }

        then:
        outputDir.list() as Set ==~ ["file.txt", "subDir"]
        new File(outputDir,"subDir").list() as Set ==~ ["another-file.txt"]
    }

    def "on failure files are not moved to a output directory"() {
        def outputDir = createNewDirectory(file("someDir"))
        def stageDir = null

        when:
        transactionBuilder.stageOutputDirectory(outputDir) { stageDir = it }
            .build()
            .execute {
                new File(stageDir, "file.txt").createNewFile()
                new File(stageDir, "subDir").mkdir()
                new File(stageDir, "subDir/another-file.txt").createNewFile()
                throw new RuntimeException("Exception")
            }

        then:
        thrown(RuntimeException)
        isEmptyDirectory(outputDir)
    }

    def "unique folder is generated for stash directory and every output stage directory that exists"() {
        when:
        def directories = transactionBuilder.stageOutputDirectory(createNewDirectory(file("dir"))) {}
            .stageOutputDirectory(createNewDirectory(file("dir"))) {}
            .stageOutputDirectory(createNewDirectory(file("dir1"))) {}
            .build()
            .execute {
                return transactionDir.list()
            }

        then:
        directories as Set ==~ ["stash-dir", "staging-dir-0", "staging-dir-1", "staging-dir-2"]
    }

    def "folder is cleaned before execution and folder structure is kept for transactional directory"() {
        createNewFile(fileInTransactionDir("staging-dir-0/dir1/dir1/file1.txt"))
        createNewDirectory(fileInTransactionDir("staging-dir-0/dir1/dir2"))
        createNewDirectory(fileInTransactionDir("staging-dir-0/dir3"))
        createNewFile(fileInTransactionDir("staging-dir-0/dir2/file1.txt"))
        createNewDirectory(fileInTransactionDir("staging-dir-1"))
        createNewDirectory(file("other-dir/dir1/dir1"))
        createNewDirectory(file("other-dir/dir2"))
        // This is a file, so folder ./dir1/dir2 in transactional dir should be deleted
        createNewFile(file("other-dir/dir1/dir2"))

        expect:
        transactionBuilder.stageOutputDirectory(file("other-dir")) {}
            .build()
            .execute {
                assert transactionDir.list() as Set ==~ ["stash-dir", "staging-dir-0"]
                assert fileInTransactionDir("staging-dir-0").list() as Set ==~ ["dir1", "dir2"]
                assert fileInTransactionDir("staging-dir-0/dir1").list() as Set ==~ ["dir1"]
                assert hasOnlyDirectories(fileInTransactionDir("staging-dir-0"))
                return DID_WORK
            }
    }

    private File fileInTransactionDir(String path) {
        return new File(checkNotNull(transactionDir), path)
    }

    private File file(String path) {
        return new File(checkNotNull(temporaryFolder) as File, path)
    }

    private File createNewDirectory(File file) {
        file.parentFile.mkdirs()
        file.mkdir()
        return file
    }

    private File createNewFile(File file) {
        file.parentFile.mkdirs()
        file.createNewFile()
        return file
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
