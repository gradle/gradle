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
import org.gradle.api.internal.tasks.compile.ApiCompilerResult
import org.gradle.api.internal.tasks.compile.CompilationFailedException
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec
import org.gradle.api.internal.tasks.compile.JavaCompileSpec
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.GeneratedResource
import org.gradle.api.tasks.WorkResults
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.api.tasks.util.PatternSet
import org.gradle.util.TestUtil
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Supplier
import java.util.stream.Stream

import static com.google.common.base.Preconditions.checkNotNull
import static org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.GeneratedResource.Location.CLASS_OUTPUT
import static org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.GeneratedResource.Location.NATIVE_HEADER_OUTPUT
import static org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.GeneratedResource.Location.SOURCE_OUTPUT

class CompileTransactionTest extends Specification {

    private static final DID_WORK = WorkResults.didWork(true)

    @TempDir
    File temporaryFolder
    File transactionDir
    File stashDir
    @Shared
    File classBackupDir
    JavaCompileSpec spec

    def setup() {
        transactionDir = new File(temporaryFolder, "compileTransaction")
        transactionDir.mkdir()
        stashDir = new File(transactionDir, "stash-dir")
        classBackupDir = new File(transactionDir, "backup-dir")
        spec = new DefaultJavaCompileSpec()
        spec.setTempDir(temporaryFolder)
        spec.setCompileOptions(TestUtil.newInstance(CompileOptions, TestUtil.objectFactory()))
        spec.setDestinationDir(createNewDirectory(file("classes")))
        spec.getCompileOptions().setSupportsIncrementalCompilationAfterFailure(true)
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
        sourcesToDelete[CLASS_OUTPUT] = new PatternSet().include("**/*.txt")
        sourcesToDelete[SOURCE_OUTPUT] = new PatternSet().include("**/*.ann")
        sourcesToDelete[NATIVE_HEADER_OUTPUT] = new PatternSet().include("**/*.h")

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

    def "files are stashed but not restored on a compile failure if incremental compilation after failure is not supported"() {
        spec.getCompileOptions().setSupportsIncrementalCompilationAfterFailure(false)
        def destinationDir = spec.getDestinationDir()
        new File(destinationDir, "file.txt").createNewFile()
        def pattern = new PatternSet().include("**/*.txt")

        when:
        newCompileTransaction(pattern).execute {
            assert isEmptyDirectory(destinationDir)
            assert stashDir.list() as Set ==~ ["file.txt.uniqueId0"]
            throw new CompilationFailedException()
        }

        then:
        thrown(CompilationFailedException)
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

    def "stash and backup directory are generated"() {
        given:
        spec.setDestinationDir(createNewFile(file("compile")))
        spec.getCompileOptions().setAnnotationProcessorGeneratedSourcesDirectory(createNewFile(file("annotation")))
        spec.getCompileOptions().setHeaderOutputDirectory(createNewFile(file("header")))

        when:
        def directories = newCompileTransaction().execute {
            return transactionDir.list()
        }

        then:
        directories as Set ==~ ["stash-dir", "backup-dir"]
    }

    def "generated classes and resources are deleted and classes in backup dir are restored on a failure"() {
        def destinationDir = spec.getDestinationDir()
        def annotationGeneratedSourcesDir = createNewDirectory(file("generated/annotation-resources"))
        def overwrittenFile = createNewFile(new File(destinationDir, "Overwritten.class"))
        spec.getCompileOptions().setAnnotationProcessorGeneratedSourcesDirectory(annotationGeneratedSourcesDir)

        when:
        newCompileTransaction().execute {
            def compilerResult = simulateGeneratingClassesAndResources(destinationDir, annotationGeneratedSourcesDir, overwrittenFile)
            throw new CompilationFailedException(compilerResult)
        }

        then:
        thrown(CompilationFailedException)
        overwrittenFile.text == ""
        destinationDir.list() as Set<String> == ["Overwritten.class"] as Set<String>
        isEmptyDirectory(annotationGeneratedSourcesDir)
    }

    def "generated classes and resources are not deleted and classes in backup dir are not restored on a success"() {
        def destinationDir = spec.getDestinationDir()
        def annotationGeneratedSourcesDir = createNewDirectory(file("generated/annotation-resources"))
        def overwrittenFile = createNewFile(new File(destinationDir, "Overwritten.class"))
        spec.getCompileOptions().setAnnotationProcessorGeneratedSourcesDirectory(annotationGeneratedSourcesDir)

        when:
        newCompileTransaction().execute {
            return simulateGeneratingClassesAndResources(destinationDir, annotationGeneratedSourcesDir, overwrittenFile)
        }

        then:
        overwrittenFile.text == "Overwritten"
        listAllFiles(destinationDir) == ["Overwritten.class", "com/example/A.class", "com/example/A.txt", "com/example/B.class", "com/example/C\$D.class", "com/example/C.class"]
        listAllFiles(annotationGeneratedSourcesDir) == ["com/example/A.java", "com/example/B.java", "com/example/B.txt"]
    }

    def "generated classes and resources are not deleted and classes in backup dir are not restored if incremental compilation after failure is not supported"() {
        def destinationDir = spec.getDestinationDir()
        def annotationGeneratedSourcesDir = createNewDirectory(file("generated/annotation-resources"))
        def overwrittenFile = createNewFile(new File(destinationDir, "Overwritten.class"))
        spec.getCompileOptions().setAnnotationProcessorGeneratedSourcesDirectory(annotationGeneratedSourcesDir)
        spec.getCompileOptions().setSupportsIncrementalCompilationAfterFailure(false)

        when:
        newCompileTransaction().execute {
            def compilerResult = simulateGeneratingClassesAndResources(destinationDir, annotationGeneratedSourcesDir, overwrittenFile)
            throw new CompilationFailedException(compilerResult)
        }

        then:
        thrown(CompilationFailedException)
        overwrittenFile.text == "Overwritten"
        listAllFiles(destinationDir) == ["Overwritten.class", "com/example/A.class", "com/example/A.txt", "com/example/B.class", "com/example/C\$D.class", "com/example/C.class"]
        listAllFiles(annotationGeneratedSourcesDir) == ["com/example/A.java", "com/example/B.java", "com/example/B.txt"]
    }

    ApiCompilerResult simulateGeneratingClassesAndResources(File destinationDir, File annotationGeneratedSourcesDir, File overwrittenFile) {
        def compilerResult = new ApiCompilerResult()
        compilerResult.annotationProcessingResult.generatedAggregatingTypes.addAll(["com.example.A"])
        compilerResult.annotationProcessingResult.generatedAggregatingResources.addAll(new GeneratedResource(CLASS_OUTPUT, "com.example", "A.txt"))
        compilerResult.annotationProcessingResult.addGeneratedType("com.example.B", ["ElementA"] as Set<String>)
        compilerResult.annotationProcessingResult.addGeneratedResource(new GeneratedResource(SOURCE_OUTPUT, "com.example", "B.txt"), ["ElementB"] as Set<String>)
        compilerResult.sourceClassesMapping.put("com/example/C.java", ["com.example.C", "com.example.C\$D"] as Set<String>)
        def fileInBackupDir = new File(classBackupDir, "Overwritten.class")
        Files.copy(overwrittenFile.toPath(), fileInBackupDir.toPath())
        overwrittenFile.text = "Overwritten"
        compilerResult.backupClassFiles.put(overwrittenFile.absolutePath, fileInBackupDir.absolutePath)

        createNewFile(new File(destinationDir, "com/example/A.class"))
        createNewFile(new File(annotationGeneratedSourcesDir, "com/example/A.java"))
        createNewFile(new File(destinationDir, "com/example/B.class"))
        createNewFile(new File(annotationGeneratedSourcesDir, "com/example/B.java"))
        createNewFile(new File(destinationDir, "com/example/A.txt"))
        createNewFile(new File(annotationGeneratedSourcesDir, "com/example/B.txt"))
        createNewFile(new File(destinationDir, "com/example/C.class"))
        createNewFile(new File(destinationDir, "com/example/C\$D.class"))
        return compilerResult
    }

    def "class backup directory is #description when compile after failure is set to #compileAfterFailure"() {
        given:
        spec.setDestinationDir(createNewFile(file("compile")))
        spec.getCompileOptions().setSupportsIncrementalCompilationAfterFailure(compileAfterFailure)

        when:
        def classBackupDir = newCompileTransaction().execute {
            return spec.getClassBackupDir()
        }

        then:
        classBackupDir == (expectedClassBackupDir as Supplier<File>).get()

        where:
        description | compileAfterFailure | expectedClassBackupDir
        "set"       | true                | { classBackupDir }
        "not set"   | false               | { null }
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

    private List<String> listAllFiles(File file) {
        return Files.find(file.toPath(), 99, { path, attributes -> attributes.isRegularFile() })
            .collect { file.toPath().relativize(it).toString().replace("\\", "/") }
            .toSorted()
    }

    private boolean hasOnlyDirectories(File file) {
        try (Stream<Path> stream = Files.walk(file.toPath())) {
            return stream.allMatch { Files.isDirectory(it) }
        }
    }
}
