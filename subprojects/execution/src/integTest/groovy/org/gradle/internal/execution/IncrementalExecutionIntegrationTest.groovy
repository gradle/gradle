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

package org.gradle.internal.execution

import com.google.common.collect.ImmutableList
import com.google.common.collect.Iterables
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.problems.interfaces.Severity
import org.gradle.cache.Cache
import org.gradle.cache.ManualEvictionInMemoryCache
import org.gradle.caching.internal.controller.BuildCacheController
import org.gradle.internal.Try
import org.gradle.internal.deprecation.Documentation
import org.gradle.internal.execution.history.OutputFilesRepository
import org.gradle.internal.execution.history.changes.DefaultExecutionStateChangeDetector
import org.gradle.internal.execution.history.impl.DefaultOverlappingOutputDetector
import org.gradle.internal.execution.impl.DefaultExecutionEngine
import org.gradle.internal.execution.impl.DefaultFileCollectionFingerprinterRegistry
import org.gradle.internal.execution.impl.DefaultInputFingerprinter
import org.gradle.internal.execution.impl.DefaultOutputSnapshotter
import org.gradle.internal.execution.impl.FingerprinterRegistration
import org.gradle.internal.execution.steps.AssignWorkspaceStep
import org.gradle.internal.execution.steps.CaptureStateAfterExecutionStep
import org.gradle.internal.execution.steps.CaptureStateBeforeExecutionStep
import org.gradle.internal.execution.steps.CreateOutputsStep
import org.gradle.internal.execution.steps.ExecuteStep
import org.gradle.internal.execution.steps.IdentifyStep
import org.gradle.internal.execution.steps.IdentityCacheStep
import org.gradle.internal.execution.steps.LoadPreviousExecutionStateStep
import org.gradle.internal.execution.steps.RecordOutputsStep
import org.gradle.internal.execution.steps.RemovePreviousOutputsStep
import org.gradle.internal.execution.steps.RemoveUntrackedExecutionStateStep
import org.gradle.internal.execution.steps.ResolveCachingStateStep
import org.gradle.internal.execution.steps.ResolveChangesStep
import org.gradle.internal.execution.steps.ResolveInputChangesStep
import org.gradle.internal.execution.steps.SkipUpToDateStep
import org.gradle.internal.execution.steps.StoreExecutionStateStep
import org.gradle.internal.execution.steps.ValidateStep
import org.gradle.internal.fingerprint.DirectorySensitivity
import org.gradle.internal.fingerprint.LineEndingSensitivity
import org.gradle.internal.fingerprint.hashing.FileSystemLocationSnapshotHasher
import org.gradle.internal.fingerprint.impl.AbsolutePathFileCollectionFingerprinter
import org.gradle.internal.fingerprint.impl.DefaultFileCollectionSnapshotter
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.id.UniqueId
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.reflect.problems.ValidationProblemId
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.internal.scopeids.id.BuildInvocationScopeId
import org.gradle.internal.snapshot.SnapshotVisitorUtil
import org.gradle.internal.snapshot.impl.DefaultValueSnapshotter
import org.gradle.internal.snapshot.impl.ImplementationSnapshot
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.internal.execution.ExecutionEngine.ExecutionOutcome.EXECUTED_NON_INCREMENTALLY
import static org.gradle.internal.execution.ExecutionEngine.ExecutionOutcome.UP_TO_DATE

class IncrementalExecutionIntegrationTest extends Specification implements ValidationMessageChecker {
    private final DocumentationRegistry documentationRegistry = new DocumentationRegistry()

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = TestNameTestDirectoryProvider.newInstance(getClass())

    def virtualFileSystem = TestFiles.virtualFileSystem()
    def fileSystemAccess = TestFiles.fileSystemAccess(virtualFileSystem)
    def snapshotter = new DefaultFileCollectionSnapshotter(fileSystemAccess, TestFiles.fileSystem())
    def fingerprinter = new AbsolutePathFileCollectionFingerprinter(DirectorySensitivity.DEFAULT, snapshotter, FileSystemLocationSnapshotHasher.DEFAULT)
    def executionHistoryStore = new TestExecutionHistoryStore()
    def outputChangeListener = new OutputChangeListener() {

        @Override
        void invalidateCachesFor(Iterable<String> affectedOutputPaths) {
            fileSystemAccess.write(affectedOutputPaths) {}
        }
    }
    def buildInvocationScopeId = new BuildInvocationScopeId(UniqueId.generate())
    def classloaderHierarchyHasher = new ClassLoaderHierarchyHasher() {
        @Override
        HashCode getClassLoaderHash(ClassLoader classLoader) {
            return TestHashCodes.hashCodeFrom(1234)
        }
    }
    def outputFilesRepository = Stub(OutputFilesRepository) {
        isGeneratedByGradle() >> true
    }
    def outputSnapshotter = new DefaultOutputSnapshotter(snapshotter)
    def fingerprinterRegistry = new DefaultFileCollectionFingerprinterRegistry([FingerprinterRegistration.registration(DirectorySensitivity.DEFAULT, LineEndingSensitivity.DEFAULT, fingerprinter)])
    def valueSnapshotter = new DefaultValueSnapshotter([], classloaderHierarchyHasher)
    def inputFingerprinter = new DefaultInputFingerprinter(snapshotter, fingerprinterRegistry, valueSnapshotter)
    def buildCacheController = Mock(BuildCacheController)
    def buildOperationExecutor = new TestBuildOperationExecutor()
    def validationWarningReporter = Mock(ValidateStep.ValidationWarningRecorder)

    final outputFile = temporaryFolder.file("output-file")
    final outputDir = temporaryFolder.file("output-dir")
    final outputDirFile = outputDir.file("some-file")
    final outputDirFile2 = outputDir.file("some-file-2")
    final emptyOutputDir = temporaryFolder.file("empty-output-dir")
    final missingOutputFile = temporaryFolder.file("missing-output-file")
    final missingOutputDir = temporaryFolder.file("missing-output-dir")
    final inputFile = temporaryFolder.createFile("input-file")
    final inputDir = temporaryFolder.createDir("input-dir")
    final inputDirFile = inputDir.file("input-file2").createFile()
    final missingInputFile = temporaryFolder.file("missing-input-file")
    final inputFiles = [file: [inputFile], dir: [inputDir], missingFile: [missingInputFile]]
    final outputFiles = [file: outputFile, missingFile: missingOutputFile]
    final outputDirs = [emptyDir: emptyOutputDir, dir: outputDir, missingDir: missingOutputDir]
    final createFiles = [outputFile, outputDirFile, outputDirFile2] as Set

    def unitOfWork = builder.build()

    def changeDetector = new DefaultExecutionStateChangeDetector()
    def overlappingOutputDetector = new DefaultOverlappingOutputDetector()
    def deleter = TestFiles.deleter()

    ExecutionEngine getExecutor() {
        // @formatter:off
        new DefaultExecutionEngine(documentationRegistry,
            new IdentifyStep<>(buildOperationExecutor,
            new IdentityCacheStep<>(
            new AssignWorkspaceStep<>(
            new LoadPreviousExecutionStateStep<>(
            new RemoveUntrackedExecutionStateStep<>(
            new CaptureStateBeforeExecutionStep<>(buildOperationExecutor, classloaderHierarchyHasher, outputSnapshotter, overlappingOutputDetector,
            new ValidateStep<>(virtualFileSystem, validationWarningReporter,
            new ResolveCachingStateStep<>(buildCacheController, false,
            new ResolveChangesStep<>(changeDetector,
            new SkipUpToDateStep<>(
            new RecordOutputsStep<>(outputFilesRepository,
            new StoreExecutionStateStep<>(
            new ResolveInputChangesStep<>(
            new CaptureStateAfterExecutionStep<>(buildOperationExecutor, buildInvocationScopeId.getId(), outputSnapshotter, outputChangeListener,
            new CreateOutputsStep<>(
            new RemovePreviousOutputsStep<>(deleter, outputChangeListener,
            new ExecuteStep<>(buildOperationExecutor
        ))))))))))))))))))
        // @formatter:on
    }

    def "outputs are created"() {
        def unitOfWork = builder.withOutputDirs(
            dir1: file("outDir1"),
            dir2: file("outDir2")
        ).withOutputFiles(
            "file1": file("parent1/outFile"),
            "file2": file("parent2/outFile")
        ).withWork { ->
            UnitOfWork.WorkResult.DID_WORK
        }.build()

        when:
        def result = execute(unitOfWork)

        then:
        result.execution.get().outcome == EXECUTED_NON_INCREMENTALLY
        !result.reusedOutputOriginMetadata.present

        def allDirs = ["outDir1", "outDir2"].collect { file(it) }
        def allFiles = ["parent1/outFile", "parent2/outFile"].collect { file(it) }
        allDirs.each {
            assert it.isDirectory()
        }
        allFiles.each {
            assert it.parentFile.isDirectory()
            assert !it.exists()
        }
    }

    def "output snapshots are stored"() {
        when:
        def result = execute(unitOfWork)

        then:
        result.execution.get().outcome == EXECUTED_NON_INCREMENTALLY
        !result.reusedOutputOriginMetadata.present

        result.afterExecutionState.get().outputFilesProducedByWork.keySet() == ["dir", "emptyDir", "file", "missingDir", "missingFile"] as Set
        SnapshotVisitorUtil.getRelativePaths(result.afterExecutionState.get().outputFilesProducedByWork["dir"]) == ["some-file", "some-file-2"]
        def afterExecution = Iterables.getOnlyElement(executionHistoryStore.executionHistory.values())
        afterExecution.originMetadata.buildInvocationId == buildInvocationScopeId.id.asString()
        afterExecution.outputFilesProducedByWork == result.afterExecutionState.get().outputFilesProducedByWork
    }

    def "work unit is up-to-date if nothing changes"() {
        when:
        def result = execute(unitOfWork)

        then:
        result.execution.get().outcome == EXECUTED_NON_INCREMENTALLY
        !result.reusedOutputOriginMetadata.present

        def outputFilesProducedByWork = result.afterExecutionState.get().outputFilesProducedByWork

        when:
        buildInvocationScopeId = new BuildInvocationScopeId(UniqueId.generate())
        result = upToDate(builder.withWork { ->
            throw new IllegalStateException("Must not be executed")
        }.build())

        then:
        result.execution.get().outcome == UP_TO_DATE
        result.afterExecutionState.get().outputFilesProducedByWork == outputFilesProducedByWork
    }

    def "out-of-date for an output file change"() {
        when:
        def result = execute(unitOfWork)

        then:
        result.execution.get().outcome == EXECUTED_NON_INCREMENTALLY
        !result.reusedOutputOriginMetadata.present

        when:
        buildInvocationScopeId = new BuildInvocationScopeId(UniqueId.generate())
        outputFile.text = "outdated"

        then:
        outOfDate(builder.build(), outputFilesChanged(file: [outputFile]))
    }

    def "failed executions are never up-to-date"() {
        def failure = new RuntimeException()

        when:
        def result = execute(builder.withWork { ->
            throw failure
        }.build())
        then:
        result.execution.failure.get() == failure
        !result.reusedOutputOriginMetadata.present

        when:
        buildInvocationScopeId = new BuildInvocationScopeId(UniqueId.generate())
        result = outOfDate(builder.build(), "Task has failed previously.")

        then:
        result.execution.get().outcome == EXECUTED_NON_INCREMENTALLY
        !result.reusedOutputOriginMetadata.present
    }

    def "out of date when no history"() {
        when:
        def result = execute(unitOfWork)

        then:
        result.execution.get().outcome == EXECUTED_NON_INCREMENTALLY
        !result.reusedOutputOriginMetadata.present
        result.executionReasons == ["No history is available."]
    }

    def "out of date when work fails validation"() {
        given:
        execute(unitOfWork)

        def invalidWork = builder
            .withValidator { context ->
                context
                    .forType(UnitOfWork, false)
                    .visitPropertyProblem {
                        it.type(ValidationProblemId.TEST_PROBLEM.name())
                            .severity(Severity.WARNING)
                            .message("Validation problem")
                            .documentedAt(Documentation.userManual("id", "section"))
                            .description("Test")
                            .noLocation()
                    }
            }
            .build()
        when:
        def result = execute(invalidWork)

        then:
        result.execution.get().outcome == EXECUTED_NON_INCREMENTALLY
        !result.reusedOutputOriginMetadata.present
        result.executionReasons == ["Incremental execution has been disabled to ensure correctness. Please consult deprecation warnings for more details."]
    }

    def "out of date when output file removed"() {
        given:
        execute(unitOfWork)

        when:
        outputFile.delete()
        def result = execute(unitOfWork)

        then:
        result.execution.get().outcome == EXECUTED_NON_INCREMENTALLY
        !result.reusedOutputOriginMetadata.present
        result.executionReasons == ["Output property 'file' file ${outputFile.absolutePath} has been removed."]
    }

    def "out of date when output file in output dir removed"() {
        given:
        execute(unitOfWork)

        when:
        outputDirFile.delete()
        def result = execute(unitOfWork)

        then:
        result.execution.get().outcome == EXECUTED_NON_INCREMENTALLY
        !result.reusedOutputOriginMetadata.present
        result.executionReasons == ["Output property 'dir' file ${outputDirFile.absolutePath} has been removed."]
    }

    def "out of date when output file has changed type"() {
        given:
        execute(unitOfWork)

        when:
        outputFile.delete()
        outputFile.createDir()
        def result = execute(unitOfWork)

        then:
        !result.execution.successful
        !result.reusedOutputOriginMetadata.present
        result.executionReasons == ["Output property 'file' file ${outputFile.absolutePath} has changed."]
    }

    def "out of date when any file in output dir has changed type"() {
        given:
        execute(unitOfWork)

        when:
        outputDirFile.delete()
        outputDirFile.createDir()
        def result = execute(unitOfWork)

        then:
        !result.execution.successful
        !result.reusedOutputOriginMetadata.present
        result.executionReasons == ["Output property 'dir' file ${outputDirFile.absolutePath} has changed."]
    }

    def "out of date when any output file has changed contents"() {
        given:
        execute(unitOfWork)

        when:
        outputFile << "new content"
        def result = execute(unitOfWork)
        then:
        result.execution.get().outcome == EXECUTED_NON_INCREMENTALLY
        !result.reusedOutputOriginMetadata.present
        result.executionReasons == ["Output property 'file' file ${outputFile.absolutePath} has changed."]
    }

    def "out of date when any file in output dir has changed contents"() {
        given:
        execute(unitOfWork)

        when:
        outputDirFile << "new content"
        def result = execute(unitOfWork)
        then:
        result.execution.get().outcome == EXECUTED_NON_INCREMENTALLY
        !result.reusedOutputOriginMetadata.present
        result.executionReasons == ["Output property 'dir' file ${outputDirFile.absolutePath} has changed."]
    }

    def "out-of-date when any output files properties are added"() {
        when:
        execute(unitOfWork)
        def outputFilesAddedUnitOfWork = builder.withOutputFiles(*: outputFiles, newFile: temporaryFolder.createFile("output-file-2")).build()

        then:
        outOfDate(outputFilesAddedUnitOfWork, "Output property 'newFile' has been added for ${outputFilesAddedUnitOfWork.displayName}")
    }

    def "out-of-date when any output file properties are removed"() {
        given:
        execute(unitOfWork)

        when:
        outputFiles.removeAll { it.key == "file" }
        def outputFilesRemovedUnitOfWork = builder.withOutputFiles(outputFiles).build()
        def result = execute(outputFilesRemovedUnitOfWork)

        then:
        result.execution.get().outcome == EXECUTED_NON_INCREMENTALLY
        !result.reusedOutputOriginMetadata.present
        result.executionReasons == ["Output property 'file' has been removed for ${outputFilesRemovedUnitOfWork.displayName}"]
    }

    def "out-of-date when implementation changes"() {
        expect:
        execute(unitOfWork)
        outOfDate(
            builder.withImplementation(ImplementationSnapshot.of("DifferentType", TestHashCodes.hashCodeFrom(1234))).build(),
            "The type of ${unitOfWork.displayName} has changed from 'org.gradle.internal.execution.UnitOfWork' to 'DifferentType'."
        )
    }

    def "out-of-date when any input files are added"() {
        final addedFile = temporaryFolder.createFile("other-input")

        when:
        execute(unitOfWork)
        def inputFilesAdded = builder.withInputFiles(file: [inputFile, addedFile], dir: [inputDir], missingFile: [missingInputFile]).build()

        then:
        outOfDate(inputFilesAdded, filesAdded(file: [addedFile]))
    }

    def "out-of-date when any input file is removed"() {
        when:
        execute(unitOfWork)
        def inputFilesRemovedUnitOfWork = builder.withInputFiles(file: [inputFile], dir: [], missingFile: [missingInputFile]).build()

        then:
        outOfDate(inputFilesRemovedUnitOfWork, inputFilesRemoved(dir: [inputDir, inputDirFile]))
    }

    def "out-of-date when any input file changes"() {
        given:
        execute(unitOfWork)

        when:
        inputFile.write("some new content")

        then:
        outOfDate(unitOfWork, inputFilesChanged(file: [inputFile]))
    }

    def "out-of-date when any input file type changes"() {
        given:
        execute(unitOfWork)

        when:
        inputFile.delete()
        inputFile.createDir()

        then:
        outOfDate(unitOfWork, inputFilesChanged(file: [inputFile]))
    }

    def "out-of-date when any input file no longer exists"() {
        given:
        execute(unitOfWork)

        when:
        inputFile.delete()

        then:
        outOfDate(unitOfWork, inputFilesRemoved(file: [inputFile]))
    }

    def "out-of-date when any input file did not exist and now does"() {
        given:
        inputFile.delete()
        execute(unitOfWork)

        when:
        inputFile.createNewFile()

        then:
        outOfDate(unitOfWork, filesAdded(file: [inputFile]))
    }

    def "out-of-date when any file is created in input dir"() {
        given:
        execute(unitOfWork)

        when:
        def file = inputDir.file("other-file").createFile()

        then:
        outOfDate(unitOfWork, filesAdded(dir: [file]))
    }

    def "out-of-date when any file deleted from input dir"() {
        given:
        execute(unitOfWork)

        when:
        inputDirFile.delete()

        then:
        outOfDate(unitOfWork, inputFilesRemoved(dir: [inputDirFile]))
    }

    def "out-of-date when any file in input dir changes"() {
        given:
        execute(unitOfWork)

        when:
        inputDirFile.writelns("new content")

        then:
        outOfDate(unitOfWork, inputFilesChanged(dir: [inputDirFile]))
    }

    def "out-of-date when any file in input dir changes type"() {
        given:
        execute(unitOfWork)

        when:
        inputDirFile.delete()
        inputDirFile.mkdir()

        then:
        outOfDate(unitOfWork, inputFilesChanged(dir: [inputDirFile]))
    }

    def "out-of-date when any input property value changed"() {
        when:
        execute(builder.withProperty("prop", "original value").build())
        def inputPropertiesChanged = builder.withProperty("prop", "new value").build()

        then:
        outOfDate(inputPropertiesChanged, "Value of input property 'prop' has changed for ${inputPropertiesChanged.displayName}")
    }

    def "input property value can be null"() {
        when:
        def unitOfWork = builder.withProperty("prop", null).build()
        execute(unitOfWork)

        then:
        upToDate(unitOfWork)
    }

    def "out-of-date when any input property added"() {
        when:
        execute(unitOfWork)
        def addedProperty = builder.withProperty("prop2", "value").build()

        then:
        outOfDate(addedProperty, "Input property 'prop2' has been added for ${addedProperty.displayName}")
    }

    def "out-of-date when any input property removed"() {
        given:
        execute(builder.withProperty("prop2", "value").build())

        expect:
        outOfDate(unitOfWork, "Input property 'prop2' has been removed for ${unitOfWork.displayName}")
    }

    def "up to date when no inputs"() {
        when:
        def noInputsTask = builder.withoutInputFiles().build()
        execute(noInputsTask)

        then:
        upToDate noInputsTask

        when:
        outputDirFile.delete()

        then:
        outOfDate(noInputsTask, outputFilesRemoved(dir: [outputDirFile]))
    }

    def "up to date when task has no output files"() {
        when:
        def noOutputs = builder.withOutputFiles([:]).build()
        execute(noOutputs)

        then:
        upToDate noOutputs
    }

    def "invalid work is not executed"() {
        def invalidWork = builder
            .withValidator { validationContext ->
                validationContext.forType(Object, true).visitNewTypeProblem {
                    it
                        .withAnnotationType(Object)
                        .type(ValidationProblemId.TEST_PROBLEM.name())
                        .severity(Severity.ERROR)
                        .message("Validation error")
                        .documentedAt(Documentation.userManual("id", "section"))
                        .description("Test")
                        .noLocation()
                }
            }
            .withWork({ throw new RuntimeException("Should not get executed") })
            .build()

        when:
        execute(invalidWork)

        then:
        def ex = thrown WorkValidationException
        WorkValidationExceptionChecker.check(ex) {
            hasProblem dummyValidationProblemWithLink('java.lang.Object', null, 'Validation error', 'Test').trim()
        }
    }

    def "results are loaded from identity cache"() {
        def work = builder.build()
        def cache = new ManualEvictionInMemoryCache<UnitOfWork.Identity, Try<Object>>()

        when:
        def executedResult = executeDeferred(work, cache)

        then:
        executedResult == "deferred"

        when:
        def cachedResult = executeDeferred(work, cache)

        then:
        cachedResult == "cached"
    }

    List<String> inputFilesRemoved(Map<String, List<File>> removedFiles) {
        filesRemoved('Input', removedFiles)
    }

    List<String> outputFilesRemoved(Map<String, List<File>> removedFiles) {
        filesRemoved('Output', removedFiles)
    }

    List<String> filesRemoved(String type, Map<String, List<File>> removedFiles) {
        removedFiles.collectMany { propertyName, files ->
            files.collect { file ->
                "${type} property '${propertyName}' file ${file.absolutePath} has been removed.".toString()
            }
        }
    }

    List<String> outputFilesChanged(Map<String, List<File>> removedFiles) {
        filesChanged('Output', removedFiles)
    }

    List<String> inputFilesChanged(Map<String, List<File>> changedFiles) {
        filesChanged("Input", changedFiles)
    }

    List<String> filesChanged(String type, Map<String, List<File>> changedFiles) {
        changedFiles.collectMany { propertyName, files ->
            files.collect { file ->
                "${type} property '${propertyName}' file ${file.absolutePath} has changed.".toString()
            }
        }
    }

    List<String> filesAdded(Map<String, List<File>> addedFiles) {
        addedFiles.collectMany { propertyName, files ->
            files.collect { file ->
                "Input property '${propertyName}' file ${file.absolutePath} has been added.".toString()
            }
        }
    }

    ExecutionEngine.Result outOfDate(UnitOfWork unitOfWork, String... expectedReasons) {
        return outOfDate(unitOfWork, ImmutableList.<String> copyOf(expectedReasons))
    }

    ExecutionEngine.Result outOfDate(UnitOfWork unitOfWork, List<String> expectedReasons) {
        def result = execute(unitOfWork)
        assert result.execution.get().outcome == EXECUTED_NON_INCREMENTALLY
        !result.reusedOutputOriginMetadata.present
        assert result.executionReasons == expectedReasons
        return result
    }

    ExecutionEngine.Result upToDate(UnitOfWork unitOfWork) {
        def result = execute(unitOfWork)
        assert result.execution.get().outcome == UP_TO_DATE
        return result
    }

    ExecutionEngine.Result execute(UnitOfWork unitOfWork) {
        virtualFileSystem.invalidateAll()
        executor.createRequest(unitOfWork).execute()
    }

    String executeDeferred(UnitOfWork unitOfWork, Cache<UnitOfWork.Identity, Try<Object>> cache) {
        virtualFileSystem.invalidateAll()
        def result = executor.createRequest(unitOfWork)
            .executeDeferred(cache)

        if (result.getCompleted().isPresent()) {
            return "cached"
        } else {
            result.completeAndGet()
            return "deferred"
        }
    }

    private TestFile file(Object... path) {
        return temporaryFolder.file(path)
    }

    UnitOfWorkBuilder getBuilder() {
        new UnitOfWorkBuilder(
            [prop: "value"],
            inputFiles,
            outputFiles,
            outputDirs,
            createFiles,
            inputFingerprinter,
            executionHistoryStore)
    }
}
