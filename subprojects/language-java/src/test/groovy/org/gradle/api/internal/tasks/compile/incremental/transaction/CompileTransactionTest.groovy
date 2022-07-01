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
import org.gradle.api.internal.tasks.compile.CompilationFailedException
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec
import org.gradle.api.internal.tasks.compile.JavaCompileSpec
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.GeneratedResource
import org.gradle.api.tasks.WorkResults
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.api.tasks.util.PatternSet
import org.gradle.util.TestUtil
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
    JavaCompileSpec spec

    def setup() {
        transactionDir = new File(temporaryFolder, "compileTransaction")
        transactionDir.mkdir()
        stashDir = new File(transactionDir, "stash-dir")
        spec = new DefaultJavaCompileSpec()
        spec.setTempDir(temporaryFolder)
        spec.setCompileOptions(new CompileOptions(TestUtil.objectFactory()))
        spec.setDestinationDir(createNewDirectory(file("classes")))
    }

    CompileTransaction newCompileTransaction() {
        return new CompileTransaction(spec, new PatternSet(), Collections.emptyMap(), TestFiles.fileOperations(temporaryFolder), TestFiles.deleter())
    }

    CompileTransaction newCompileTransaction(PatternSet classesToDelete) {
        return new CompileTransaction(spec, classesToDelete, Collections.emptyMap(), TestFiles.fileOperations(temporaryFolder), TestFiles.deleter())
    }

    CompileTransaction newCompileTransaction(PatternSet classesToDelete, Map<GeneratedResource.Location, PatternSet> resourcesToDelete) {
        return new CompileTransaction(spec, classesToDelete, resourcesToDelete, TestFiles.fileOperations(temporaryFolder), TestFiles.deleter())
    }

    def "transaction base directory is cleared before execution"() {
        fileInTransactionDir("some-dummy-file.txt").createNewFile()
        fileInTransactionDir("sub-dir").mkdir()

        when:
        assert !hasOnlyDirectories(transactionDir)
        def isTransactionDirEmpty = newCompileTransaction().execute {
            return hasOnlyDirectories(transactionDir)
        }

        then:
        isTransactionDirEmpty
    }

    def "files are stashed and restored on compile failure"() {
        def destinationDir = spec.getDestinationDir()
        createNewFile(new File(destinationDir, "file.txt"))
        createNewFile(new File(destinationDir, "subDir/another-file.txt"))
        createNewFile(new File(destinationDir, "subDir/some-dest-file.class"))
        def annotationOutput = createNewDirectory(file("annotationOut"))
        createNewFile(new File(annotationOutput, "some-ann-file.ann"))
        createNewFile(new File(annotationOutput, "some-ann-file.class"))
        createNewFile(new File(annotationOutput, "some-duplicated-file.class"))
        def headerOutput = createNewDirectory(file("headerOut"))
        createNewFile(new File(headerOutput, "some-header-file.h"))
        createNewFile(new File(headerOutput, "some-header-file.class"))
        createNewFile(new File(headerOutput, "some-duplicated-file.class"))
        spec.getCompileOptions().setAnnotationProcessorGeneratedSourcesDirectory(annotationOutput)
        spec.getCompileOptions().setHeaderOutputDirectory(headerOutput)
        def classesToDelete = new PatternSet().include("**/*.class")
        Map<GeneratedResource.Location, PatternSet> sourcesToDelete = [:]
        sourcesToDelete[GeneratedResource.Location.CLASS_OUTPUT] = new PatternSet().include("**/*.txt")
        sourcesToDelete[GeneratedResource.Location.SOURCE_OUTPUT] = new PatternSet().include("**/*.ann")
        sourcesToDelete[GeneratedResource.Location.NATIVE_HEADER_OUTPUT] = new PatternSet().include("**/*.h")

        when:
        newCompileTransaction(classesToDelete, sourcesToDelete).execute {
            assert hasOnlyDirectories(destinationDir)
            assert stashDir.list()
                .collect { it.replaceAll(".uniqueId.*", "") }
                .sort() == ["another-file.txt", "file.txt", "some-ann-file.ann",
                            "some-ann-file.class", "some-dest-file.class",
                            "some-duplicated-file.class", "some-duplicated-file.class",
                            "some-header-file.class", "some-header-file.h"]
            throw new CompilationFailedException()
        }

        then:
        thrown(CompilationFailedException)
        destinationDir.list() as Set ==~ ["file.txt", "subDir"]
        new File(destinationDir,"subDir").list() as Set ==~ ["another-file.txt", "some-dest-file.class"]
        annotationOutput.list() as Set ==~ ["some-duplicated-file.class", "some-ann-file.class", "some-ann-file.ann"]
        headerOutput.list() as Set ==~ ["some-duplicated-file.class", "some-header-file.h", "some-header-file.class"]
    }

    def "files are stashed but not restored on success"() {
        def destinationDir = spec.getDestinationDir()
        new File(destinationDir, "file.txt").createNewFile()
        def pattern = new PatternSet().include("**/*.txt")

        when:
        newCompileTransaction(pattern).execute {
            assert isEmptyDirectory(destinationDir)
            assert stashDir.list() as Set ==~ ["file.txt.uniqueId0"]
            return DID_WORK
        }

        then:
        isEmptyDirectory(destinationDir)
    }

    def "files are stashed but not restored on a failure not produced by the compilation"() {
        def destinationDir = spec.getDestinationDir()
        new File(destinationDir, "file.txt").createNewFile()
        def pattern = new PatternSet().include("**/*.txt")

        when:
        newCompileTransaction(pattern).execute {
            assert isEmptyDirectory(destinationDir)
            assert stashDir.list() as Set ==~ ["file.txt.uniqueId0"]
            throw new RuntimeException()
        }

        then:
        thrown(RuntimeException)
        isEmptyDirectory(destinationDir)
    }

    def "if something get stashed workResult passed to execution will mark 'did work'"() {
        def destinationDir = spec.getDestinationDir()
        new File(destinationDir, "file.txt").createNewFile()
        def pattern = new PatternSet().include("**/*.txt")

        when:
        def workResult = newCompileTransaction(pattern).execute {
            assert isEmptyDirectory(destinationDir)
            assert !isEmptyDirectory(stashDir)
            return it
        }

        then:
        workResult == WorkResults.didWork(true)
    }

    def "empty parent folders of stashed files are deleted recursively"() {
        def compileOutput = createNewDirectory(file("destination"))
        def annotationOutput = createNewDirectory(file("annotation"))
        def headerOutput = createNewDirectory(file("header"))
        createNewFile(new File(compileOutput, "dir/dir/another-file.txt"))
        createNewFile(new File(annotationOutput, "dir/dir/another-file.txt"))
        createNewFile(new File(headerOutput, "dir/dir/another-file.txt"))
        def pattern = new PatternSet().include("**/*.txt")
        spec.setDestinationDir(compileOutput)
        spec.getCompileOptions().setAnnotationProcessorGeneratedSourcesDirectory(annotationOutput)
        spec.getCompileOptions().setHeaderOutputDirectory(headerOutput)

        when:
        newCompileTransaction(pattern).execute {
            return it
        }

        then:
        // We delete empty directories, but we keep the root folder
        compileOutput.exists() && isEmptyDirectory(compileOutput)
        annotationOutput.exists() && isEmptyDirectory(annotationOutput)
        headerOutput.exists() && isEmptyDirectory(headerOutput)
    }

    def "if nothing get stashed workResult passed to execution will mark 'did no work'"() {
        def workResult = newCompileTransaction().execute {
            return it
        }

        expect:
        workResult == WorkResults.didWork(false)
    }

    def "nothing is stashed and directory is not created if #description"() {
        if (directoryPath) {
            File directory = createNewDirectory(file(directoryPath))
            createNewFile(new File(directory, "test-file.txt"))
            spec.setDestinationDir(directory)
        }

        when:
        def stashDirIsEmpty = newCompileTransaction(pattern).execute {
            isEmptyDirectory(stashDir)
        }

        then:
        stashDirIsEmpty

        where:
        pattern                              | directoryPath | description
        new PatternSet()                     | "sourceDir"   | "empty pattern"
        new PatternSet().include("**/*.txt") | null          | "empty directory"
    }

    def "on success all files are moved from staging dir to an output directory"() {
        def destinationDir = spec.getDestinationDir()
        def annotationOutput = createNewDirectory(file("annotationOut"))
        spec.getCompileOptions().setAnnotationProcessorGeneratedSourcesDirectory(annotationOutput)
        def headerOutput = createNewDirectory(file("headerOut"))
        spec.getCompileOptions().setHeaderOutputDirectory(headerOutput)
        def compileStagingDir = fileInTransactionDir("compile-output")
        def annotationsStagingDir = fileInTransactionDir("annotation-output")
        def headerStagingDir = fileInTransactionDir("header-output")

        when:
        newCompileTransaction().execute {
            new File(compileStagingDir, "file.txt").createNewFile()
            new File(compileStagingDir, "subDir").mkdir()
            new File(compileStagingDir, "subDir/another-file.txt").createNewFile()
            new File(annotationsStagingDir, "annotation-file.txt").createNewFile()
            new File(headerStagingDir, "header-file.txt").createNewFile()
            return DID_WORK
        }

        then:
        destinationDir.list() as Set ==~ ["file.txt", "subDir"]
        new File(destinationDir,"subDir").list() as Set ==~ ["another-file.txt"]
        annotationOutput.list() as Set ==~ ["annotation-file.txt"]
        headerOutput.list() as Set ==~ ["header-file.txt"]
    }

    def "on compile failure files are not moved to an output directory"() {
        def destinationDir = createNewDirectory(file("someDir"))
        def stagingDir = fileInTransactionDir("compile-output")
        spec.setDestinationDir(destinationDir)

        when:
        newCompileTransaction().execute {
                new File(stagingDir, "file.txt").createNewFile()
                new File(stagingDir, "subDir").mkdir()
                new File(stagingDir, "subDir/another-file.txt").createNewFile()
                throw new CompilationFailedException()
            }

        then:
        thrown(CompilationFailedException)
        isEmptyDirectory(destinationDir)
    }

    def "unique directory is generated for stash directory and every staging directory that exists"() {
        given:
        spec.setDestinationDir(createNewFile(file("compile")))
        spec.getCompileOptions().setAnnotationProcessorGeneratedSourcesDirectory(createNewFile(file("annotation")))
        spec.getCompileOptions().setHeaderOutputDirectory(createNewFile(file("header")))

        when:
        def directories = newCompileTransaction().execute {
            return transactionDir.list()
        }

        then:
        directories as Set ==~ ["stash-dir", "compile-output", "annotation-output", "header-output"]
    }

    def "#stagingDir directory is cleaned before the execution and folder structure is the same as in #originalDir"() {
        createNewDirectory(file("$originalDir/dir1/dir1"))
        createNewDirectory(file("$originalDir/dir2"))
        // This is a file, so folder ./dir1/dir2 in transactional dir should be deleted
        createNewFile(file("$originalDir/dir1/dir2"))
        createNewFile(fileInTransactionDir("$stagingDir/dir1/dir1/file1.txt")) // only file should be deleted
        createNewDirectory(fileInTransactionDir("$stagingDir/dir1/dir2")) // dir2 directory should be deleted
        createNewDirectory(fileInTransactionDir("$stagingDir/dir3")) // dir3 directory should be deleted
        createNewFile(fileInTransactionDir("$stagingDir/dir2/file1.txt")) // only file should be deleted
        createNewDirectory(fileInTransactionDir("some-other-dir")) // some-other-dir directory should be deleted
        spec.setDestinationDir(file("classes"))
        spec.getCompileOptions().setAnnotationProcessorGeneratedSourcesDirectory(file("annotation-generated-sources"))
        spec.getCompileOptions().setHeaderOutputDirectory(file("header-sources"))

        expect:
        newCompileTransaction().execute {
            assert transactionDir.list() as Set ==~ ["stash-dir", "compile-output", "annotation-output", "header-output"]
            assert fileInTransactionDir(stagingDir).list() as Set ==~ ["dir1", "dir2"]
            assert fileInTransactionDir("$stagingDir/dir1").list() as Set ==~ ["dir1"]
            assert hasOnlyDirectories(fileInTransactionDir(stagingDir))
            return DID_WORK
        }

        where:
        originalDir | stagingDir
        "classes"                      | "compile-output"
        "annotation-generated-sources" | "annotation-output"
        "header-sources"               | "header-output"
    }

    def "modifies spec output directories before execution and restores them after execution"() {
        spec.setDestinationDir(file("classes"))
        spec.getCompileOptions().setAnnotationProcessorGeneratedSourcesDirectory(file("annotation-generated-sources"))
        spec.getCompileOptions().setHeaderOutputDirectory(file("header-sources"))

        expect:
        // Modify output folders before
        newCompileTransaction().execute {
            assert spec.getDestinationDir() == fileInTransactionDir("compile-output")
            assert spec.getCompileOptions().getAnnotationProcessorGeneratedSourcesDirectory() == fileInTransactionDir("annotation-output")
            assert spec.getCompileOptions().getHeaderOutputDirectory() == fileInTransactionDir("header-output")
            return DID_WORK
        }

        // And restore output folders after
        spec.getDestinationDir() == file("classes")
        spec.getCompileOptions().getAnnotationProcessorGeneratedSourcesDirectory() == file("annotation-generated-sources")
        spec.getCompileOptions().getHeaderOutputDirectory() == file("header-sources")
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
