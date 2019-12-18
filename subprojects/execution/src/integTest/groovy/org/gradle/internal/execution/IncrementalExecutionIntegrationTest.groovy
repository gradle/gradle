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
import com.google.common.collect.ImmutableSortedMap
import com.google.common.collect.Iterables
import com.google.common.collect.Maps
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.ImmutableFileCollection
import org.gradle.caching.internal.CacheableEntity
import org.gradle.caching.internal.controller.BuildCacheController
import org.gradle.internal.execution.caching.CachingDisabledReason
import org.gradle.internal.execution.history.ExecutionHistoryStore
import org.gradle.internal.execution.history.OutputFilesRepository
import org.gradle.internal.execution.history.changes.DefaultExecutionStateChangeDetector
import org.gradle.internal.execution.history.changes.InputChangesInternal
import org.gradle.internal.execution.impl.DefaultWorkExecutor
import org.gradle.internal.execution.steps.BroadcastChangingOutputsStep
import org.gradle.internal.execution.steps.CaptureStateBeforeExecutionStep
import org.gradle.internal.execution.steps.CatchExceptionStep
import org.gradle.internal.execution.steps.CleanupOutputsStep
import org.gradle.internal.execution.steps.CreateOutputsStep
import org.gradle.internal.execution.steps.ExecuteStep
import org.gradle.internal.execution.steps.LoadExecutionStateStep
import org.gradle.internal.execution.steps.RecordOutputsStep
import org.gradle.internal.execution.steps.ResolveCachingStateStep
import org.gradle.internal.execution.steps.ResolveChangesStep
import org.gradle.internal.execution.steps.ResolveInputChangesStep
import org.gradle.internal.execution.steps.SkipUpToDateStep
import org.gradle.internal.execution.steps.SnapshotOutputsStep
import org.gradle.internal.execution.steps.StoreExecutionStateStep
import org.gradle.internal.execution.steps.ValidateStep
import org.gradle.internal.file.TreeType
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.fingerprint.FileCollectionFingerprint
import org.gradle.internal.fingerprint.impl.AbsolutePathFileCollectionFingerprinter
import org.gradle.internal.fingerprint.impl.DefaultFileCollectionSnapshotter
import org.gradle.internal.fingerprint.impl.OutputFileCollectionFingerprinter
import org.gradle.internal.fingerprint.overlap.OverlappingOutputs
import org.gradle.internal.fingerprint.overlap.impl.DefaultOverlappingOutputDetector
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.internal.hash.HashCode
import org.gradle.internal.id.UniqueId
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.scopeids.id.BuildInvocationScopeId
import org.gradle.internal.snapshot.CompositeFileSystemSnapshot
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.internal.snapshot.impl.DefaultValueSnapshotter
import org.gradle.internal.snapshot.impl.ImplementationSnapshot
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import javax.annotation.Nullable
import java.time.Duration
import java.util.function.Consumer
import java.util.function.Supplier

import static org.gradle.internal.execution.ExecutionOutcome.EXECUTED_NON_INCREMENTALLY
import static org.gradle.internal.execution.ExecutionOutcome.UP_TO_DATE
import static org.gradle.internal.reflect.TypeValidationContext.Severity.ERROR

class IncrementalExecutionIntegrationTest extends Specification {

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = TestNameTestDirectoryProvider.newInstance()

    def virtualFileSystem = TestFiles.virtualFileSystem()
    def snapshotter = new DefaultFileCollectionSnapshotter(virtualFileSystem, TestFiles.genericFileTreeSnapshotter(), TestFiles.fileSystem())
    def fingerprinter = new AbsolutePathFileCollectionFingerprinter(snapshotter)
    def outputFingerprinter = new OutputFileCollectionFingerprinter(snapshotter)
    def executionHistoryStore = new TestExecutionHistoryStore()
    def outputChangeListener = new OutputChangeListener() {
        @Override
        void beforeOutputChange() {
            virtualFileSystem.invalidateAll()
        }

        @Override
        void beforeOutputChange(Iterable<String> affectedOutputPaths) {
            virtualFileSystem.update(affectedOutputPaths) {}
        }
    }
    def buildInvocationScopeId = new BuildInvocationScopeId(UniqueId.generate())
    def classloaderHierarchyHasher = new ClassLoaderHierarchyHasher() {
        @Override
        HashCode getClassLoaderHash(ClassLoader classLoader) {
            return HashCode.fromInt(1234)
        }
    }
    def outputFilesRepository = Stub(OutputFilesRepository) {
        isGeneratedByGradle() >> true
    }
    def valueSnapshotter = new DefaultValueSnapshotter(classloaderHierarchyHasher, null)
    def buildCacheController = Mock(BuildCacheController)
    def buildOperationExecutor = new TestBuildOperationExecutor()
    def validationWarningReporter = Mock(ValidateStep.ValidationWarningReporter)

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

    WorkExecutor<ExecutionRequestContext, CachingResult> getExecutor() {
        // @formatter:off
        new DefaultWorkExecutor<>(
            new LoadExecutionStateStep<>(
            new ValidateStep<>(validationWarningReporter,
            new CaptureStateBeforeExecutionStep<>(buildOperationExecutor, classloaderHierarchyHasher, valueSnapshotter, overlappingOutputDetector,
            new ResolveCachingStateStep<>(buildCacheController, false,
            new ResolveChangesStep<>(changeDetector,
            new SkipUpToDateStep<>(
            new RecordOutputsStep<>(outputFilesRepository,
            new BroadcastChangingOutputsStep<>(outputChangeListener,
            new StoreExecutionStateStep<>(
            new SnapshotOutputsStep<>(buildOperationExecutor, buildInvocationScopeId.getId(),
            new CreateOutputsStep<>(
            new CatchExceptionStep<>(
            new ResolveInputChangesStep<>(
            new CleanupOutputsStep<>(deleter, outputChangeListener,
            new ExecuteStep<>(
        ))))))))))))))))
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
        result.outcome.get() == EXECUTED_NON_INCREMENTALLY
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
        result.outcome.get() == EXECUTED_NON_INCREMENTALLY
        !result.reusedOutputOriginMetadata.present

        result.finalOutputs.keySet() == ["dir", "emptyDir", "file", "missingDir", "missingFile"] as Set
        result.finalOutputs["dir"].rootHashes.size() == 1
        def afterExecution = Iterables.getOnlyElement(executionHistoryStore.executionHistory.values())
        afterExecution.originMetadata.buildInvocationId == buildInvocationScopeId.id.asString()
        afterExecution.outputFileProperties.values()*.rootHashes == result.finalOutputs.values()*.rootHashes
    }

    def "work unit is up-to-date if nothing changes"() {
        when:
        def result = execute(unitOfWork)

        then:
        result.outcome.get() == EXECUTED_NON_INCREMENTALLY
        !result.reusedOutputOriginMetadata.present

        def finalOutputs = result.finalOutputs

        when:
        buildInvocationScopeId = new BuildInvocationScopeId(UniqueId.generate())
        result = upToDate(builder.withWork { ->
            throw new IllegalStateException("Must not be executed")
        }.build())

        then:
        result.outcome.get() == UP_TO_DATE
        result.finalOutputs.values()*.rootHashes == finalOutputs.values()*.rootHashes
    }

    def "out-of-date for an output file change"() {
        when:
        def result = execute(unitOfWork)

        then:
        result.outcome.get() == EXECUTED_NON_INCREMENTALLY
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
        result.outcome.failure.get() == failure
        !result.reusedOutputOriginMetadata.present

        when:
        buildInvocationScopeId = new BuildInvocationScopeId(UniqueId.generate())
        result = outOfDate(builder.build(), "Task has failed previously.")

        then:
        result.outcome.get() == EXECUTED_NON_INCREMENTALLY
        !result.reusedOutputOriginMetadata.present
    }

    def "out of date when no history"() {
        when:
        def result = execute(unitOfWork)

        then:
        result.outcome.get() == EXECUTED_NON_INCREMENTALLY
        !result.reusedOutputOriginMetadata.present
        result.executionReasons == ["No history is available."]
    }

    def "out of date when output file removed"() {
        given:
        execute(unitOfWork)

        when:
        outputFile.delete()
        def result = execute(unitOfWork)

        then:
        result.outcome.get() == EXECUTED_NON_INCREMENTALLY
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
        result.outcome.get() == EXECUTED_NON_INCREMENTALLY
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
        !result.outcome.successful
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
        !result.outcome.successful
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
        result.outcome.get() == EXECUTED_NON_INCREMENTALLY
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
        result.outcome.get() == EXECUTED_NON_INCREMENTALLY
        !result.reusedOutputOriginMetadata.present
        result.executionReasons == ["Output property 'dir' file ${outputDirFile.absolutePath} has changed."]
    }

    def "out-of-date when any output files properties are added"() {
        when:
        execute(unitOfWork)
        def outputFilesAddedUnitOfWork = builder.withOutputFiles(*:outputFiles, newFile: temporaryFolder.createFile("output-file-2")).build()

        then:
        outOfDate(outputFilesAddedUnitOfWork, "Output property 'newFile' has been added for ${outputFilesAddedUnitOfWork.displayName}")
    }

    def "out-of-date when any output file properties are removed"() {
        given:
        execute(unitOfWork)

        when:
        outputFiles.removeAll { it.key == "file"}
        def outputFilesRemovedUnitOfWork = builder.withOutputFiles(outputFiles).build()
        def result = execute(outputFilesRemovedUnitOfWork)

        then:
        result.outcome.get() == EXECUTED_NON_INCREMENTALLY
        !result.reusedOutputOriginMetadata.present
        result.executionReasons == ["Output property 'file' has been removed for ${outputFilesRemovedUnitOfWork.displayName}"]
    }

    def "out-of-date when implementation changes"() {
        expect:
        execute(unitOfWork)
        outOfDate(
            builder.withImplementation(ImplementationSnapshot.of("DifferentType", HashCode.fromInt(1234))).build(),
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
                validationContext.createContextFor(Object, true).visitTypeProblem(ERROR, Object, "Validation error")
            }
            .withWork({ throw new RuntimeException("Should not get executed") })
            .build()

        when:
        execute(invalidWork)

        then:
        def ex = thrown WorkValidationException
        ex.causes*.message as List == ["Type '$Object.simpleName': Validation error."]
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

    UpToDateResult outOfDate(UnitOfWork unitOfWork, String... expectedReasons) {
        return outOfDate(unitOfWork, ImmutableList.<String>copyOf(expectedReasons))
    }

    UpToDateResult outOfDate(UnitOfWork unitOfWork, List<String> expectedReasons) {
        def result = execute(unitOfWork)
        assert result.outcome.get() == EXECUTED_NON_INCREMENTALLY
        !result.reusedOutputOriginMetadata.present
        assert result.executionReasons == expectedReasons
        return result
    }

    UpToDateResult upToDate(UnitOfWork unitOfWork) {
        def result = execute(unitOfWork)
        assert result.outcome.get() == UP_TO_DATE
        return result
    }

    UpToDateResult execute(UnitOfWork unitOfWork) {
        virtualFileSystem.invalidateAll()

        executor.execute(new ExecutionRequestContext() {
            @Override
            UnitOfWork getWork() {
                unitOfWork
            }

            @Override
            Optional<String> getRebuildReason() {
                Optional.empty()
            }
        })
    }

    private TestFile file(Object... path) {
        return temporaryFolder.file(path)
    }

    static class OutputPropertySpec {
        File root
        TreeType treeType

        OutputPropertySpec(File root, TreeType treeType) {
            this.treeType = treeType
            this.root = root
        }
    }

    static OutputPropertySpec outputDirectorySpec(File dir) {
        return new OutputPropertySpec(dir, TreeType.DIRECTORY)
    }

    static OutputPropertySpec outputFileSpec(File file) {
        return new OutputPropertySpec(file, TreeType.FILE)
    }

    UnitOfWorkBuilder getBuilder() {
        new UnitOfWorkBuilder()
    }

    class UnitOfWorkBuilder {
        private Supplier<UnitOfWork.WorkResult> work = { ->
            create.each { it ->
                it.createFile()
            }
            return UnitOfWork.WorkResult.DID_WORK
        }
        private Map<String, Object> inputProperties = [prop: "value"]
        private Map<String, ? extends Collection<? extends File>> inputs = inputFiles
        private Map<String, ? extends File> outputFiles = IncrementalExecutionIntegrationTest.this.outputFiles
        private Map<String, ? extends File> outputDirs = IncrementalExecutionIntegrationTest.this.outputDirs
        private Collection<? extends TestFile> create = createFiles
        private ImplementationSnapshot implementation = ImplementationSnapshot.of(UnitOfWork.name, HashCode.fromInt(1234))
        private Consumer<UnitOfWork.WorkValidationContext> validator

        UnitOfWorkBuilder withWork(Supplier<UnitOfWork.WorkResult> closure) {
            work = closure
            return this
        }

        UnitOfWorkBuilder withInputFiles(Map<String, ? extends Collection<? extends File>> files) {
            this.inputs = files
            return this
        }

        UnitOfWorkBuilder withoutInputFiles() {
            this.inputs = [:]
            return this
        }

        UnitOfWorkBuilder withoutInputProperties() {
            this.inputProperties = [:]
            return this
        }

        UnitOfWorkBuilder withOutputFiles(File... outputFiles) {
            return withOutputFiles((outputFiles as List)
                .withIndex()
                .collectEntries { outputFile, index -> [('defaultFiles' + index): outputFile] }
            )
        }

        UnitOfWorkBuilder withOutputFiles(Map<String, ? extends File> files) {
            this.outputFiles = files
            return this
        }

        UnitOfWorkBuilder withOutputDirs(File... outputDirs) {
            return withOutputDirs((outputDirs as List)
                .withIndex()
                .collectEntries { outputFile, index -> [('defaultDir' + index): outputFile] }
            )
        }

        UnitOfWorkBuilder withOutputDirs(Map<String, ? extends File> dirs) {
            this.outputDirs = dirs
            return this
        }

        UnitOfWorkBuilder createsFiles(TestFile... outputFiles) {
            create = Arrays.asList(outputFiles)
            return this
        }

        UnitOfWorkBuilder withImplementation(ImplementationSnapshot implementation) {
            this.implementation = implementation
            return this
        }

        UnitOfWorkBuilder withProperty(String name, Object value) {
            inputProperties.put(name, value)
            return this
        }

        UnitOfWorkBuilder withValidator(Consumer<UnitOfWork.WorkValidationContext> validator) {
            this.validator = validator
            return this
        }

        UnitOfWork build() {
            Map<String, OutputPropertySpec> outputFileSpecs = Maps.transformEntries(outputFiles, { key, value -> outputFileSpec(value) } )
            Map<String, OutputPropertySpec> outputDirSpecs = Maps.transformEntries(outputDirs, { key, value -> outputDirectorySpec(value) } )
            Map<String, OutputPropertySpec> outputs = outputFileSpecs + outputDirSpecs

            return new UnitOfWork() {
                boolean executed

                @Override
                UnitOfWork.WorkResult execute(@Nullable InputChangesInternal inputChanges, InputChangesContext context) {
                    executed = true
                    return work.get()
                }

                @Override
                Optional<ExecutionHistoryStore> getExecutionHistoryStore() {
                    return Optional.of(IncrementalExecutionIntegrationTest.this.executionHistoryStore)
                }

                @Override
                Optional<Duration> getTimeout() {
                    throw new UnsupportedOperationException()
                }

                @Override
                UnitOfWork.InputChangeTrackingStrategy getInputChangeTrackingStrategy() {
                    return UnitOfWork.InputChangeTrackingStrategy.NONE
                }

                @Override
                void visitImplementations(UnitOfWork.ImplementationVisitor visitor) {
                    visitor.visitImplementation(implementation)
                }

                @Override
                void visitInputProperties(UnitOfWork.InputPropertyVisitor visitor) {
                    inputProperties.each { propertyName, value ->
                        visitor.visitInputProperty(propertyName, value)
                    }
                }

                @Override
                void visitInputFileProperties(UnitOfWork.InputFilePropertyVisitor visitor) {
                    for (entry in inputs.entrySet()) {
                        visitor.visitInputFileProperty(entry.key, entry.value, false,
                            { -> fingerprinter.fingerprint(ImmutableFileCollection.of(entry.value)) }
                        )
                    }
                }

                @Override
                void visitOutputProperties(UnitOfWork.OutputPropertyVisitor visitor) {
                    outputs.forEach { name, spec ->
                        visitor.visitOutputProperty(name, spec.treeType, spec.root)
                    }
                }

                @Override
                ImmutableSortedMap<String, FileSystemSnapshot> snapshotOutputsBeforeExecution() {
                    snapshotOutputs(outputs)
                }

                @Override
                ImmutableSortedMap<String, FileSystemSnapshot> snapshotOutputsAfterExecution() {
                    snapshotOutputs(outputs)
                }

                @Override
                void validate(UnitOfWork.WorkValidationContext validationContext) {
                    validator?.accept(validationContext)
                }

                @Override
                boolean shouldCleanupOutputsOnNonIncrementalExecution() {
                    return false
                }

                @Override
                long markExecutionTime() {
                    0
                }

                @Override
                void visitLocalState(UnitOfWork.LocalStateVisitor visitor) {
                    throw new UnsupportedOperationException()
                }

                @Override
                Optional<CachingDisabledReason> shouldDisableCaching(@Nullable OverlappingOutputs detectedOverlappingOutputs) {
                    throw new UnsupportedOperationException()
                }

                @Override
                boolean isAllowedToLoadFromCache() {
                    throw new UnsupportedOperationException()
                }

                @Override
                Optional<? extends Iterable<String>> getChangingOutputs() {
                    Optional.empty()
                }

                @Override
                String getIdentity() {
                    "myId"
                }

                @Override
                void visitOutputTrees(CacheableEntity.CacheableTreeVisitor visitor) {
                    throw new UnsupportedOperationException()
                }

                @Override
                String getDisplayName() {
                    "Test unit of work"
                }

                @Override
                ImmutableSortedMap<String, CurrentFileCollectionFingerprint> fingerprintAndFilterOutputSnapshots(
                    ImmutableSortedMap<String, FileCollectionFingerprint> afterPreviousExecutionOutputFingerprints,
                    ImmutableSortedMap<String, FileSystemSnapshot> beforeExecutionOutputSnapshots,
                    ImmutableSortedMap<String, FileSystemSnapshot> afterExecutionOutputSnapshots,
                    boolean hasDetectedOverlappingOutputs
                ) {
                    fingerprintOutputs(afterExecutionOutputSnapshots)
                }
            }
        }

        private ImmutableSortedMap<String, CurrentFileCollectionFingerprint> fingerprintOutputs(ImmutableSortedMap<String, FileSystemSnapshot> outputSnapshots) {
            def builder = ImmutableSortedMap.<String, CurrentFileCollectionFingerprint>naturalOrder()
            outputSnapshots.each { propertyName, snapshot ->
                builder.put(propertyName, outputFingerprinter.fingerprint([snapshot]))
            }
            return builder.build()
        }

        private ImmutableSortedMap<String, FileSystemSnapshot> snapshotOutputs(Map<String, OutputPropertySpec> outputs) {
            def builder = ImmutableSortedMap.<String, FileSystemSnapshot>naturalOrder()
            outputs.each { propertyName, spec ->
                builder.put(propertyName, CompositeFileSystemSnapshot.of(snapshotter.snapshot(ImmutableFileCollection.of(spec.root))))
            }
            return builder.build()
        }
    }
}
