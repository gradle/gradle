/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.execution.impl.steps

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSortedMap
import com.google.common.collect.Iterables
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.ImmutableFileCollection
import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.caching.internal.CacheableEntity
import org.gradle.caching.internal.origin.OriginMetadata
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher
import org.gradle.internal.execution.CacheHandler
import org.gradle.internal.execution.ExecutionOutcome
import org.gradle.internal.execution.OutputChangeListener
import org.gradle.internal.execution.Result
import org.gradle.internal.execution.TestExecutionHistoryStore
import org.gradle.internal.execution.TestOutputFilesRepository
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.WorkExecutor
import org.gradle.internal.execution.history.changes.DefaultExecutionStateChanges
import org.gradle.internal.execution.history.changes.ExecutionStateChanges
import org.gradle.internal.execution.history.impl.DefaultBeforeExecutionState
import org.gradle.internal.execution.impl.DefaultWorkExecutor
import org.gradle.internal.file.TreeType
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.fingerprint.impl.AbsolutePathFileCollectionFingerprinter
import org.gradle.internal.fingerprint.impl.OutputFileCollectionFingerprinter
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.TestFileHasher
import org.gradle.internal.id.UniqueId
import org.gradle.internal.scopeids.id.BuildInvocationScopeId
import org.gradle.internal.snapshot.ValueSnapshot
import org.gradle.internal.snapshot.WellKnownFileLocations
import org.gradle.internal.snapshot.impl.DefaultFileSystemMirror
import org.gradle.internal.snapshot.impl.DefaultFileSystemSnapshotter
import org.gradle.internal.snapshot.impl.DefaultValueSnapshotter
import org.gradle.internal.snapshot.impl.ImplementationSnapshot
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testing.internal.util.Specification
import org.junit.Rule

import java.time.Duration
import java.util.function.BooleanSupplier

class IncrementalExecutionTest extends Specification {

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = TestNameTestDirectoryProvider.newInstance()

    def fileHasher = new TestFileHasher()
    def fileSystemMirror = new DefaultFileSystemMirror(Stub(WellKnownFileLocations))
    def snapshotter = new DefaultFileSystemSnapshotter(fileHasher, new StringInterner(), TestFiles.fileSystem(), fileSystemMirror)
    def fingerprinter = new AbsolutePathFileCollectionFingerprinter(new StringInterner(), snapshotter)
    def outputFingerprinter = new OutputFileCollectionFingerprinter(new StringInterner(), snapshotter)
    def executionHistoryStore = new TestExecutionHistoryStore()
    def outputChangeListener = new OutputChangeListener() {
        @Override
        void beforeOutputChange() {
            fileSystemMirror.beforeOutputChange()
        }

        @Override
        void beforeOutputChange(Iterable<String> affectedOutputPaths) {
            fileSystemMirror.beforeOutputChange(affectedOutputPaths)
        }
    }
    def outputFilesRepository = new TestOutputFilesRepository()
    def buildInvocationScopeId = new BuildInvocationScopeId(UniqueId.generate())
    def classloaderHierarchyHasher = new ClassLoaderHierarchyHasher() {
        @Override
        HashCode getClassLoaderHash(ClassLoader classLoader) {
            return HashCode.fromInt(1234)
        }
    }
    def valueSnapshotter = new DefaultValueSnapshotter(classloaderHierarchyHasher, new NamedObjectInstantiator())

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
    final outputFiles = [file: [outputFile], missingFile: [missingOutputFile]]
    final outputDirs = [emptyDir: [emptyOutputDir], dir: [outputDir], missingDir: [missingOutputDir]]
    final createFiles = [outputFile, outputDirFile, outputDirFile2] as Set

    def unitOfWork = builder.build()


    WorkExecutor<UpToDateResult> getExecutor() {
        new DefaultWorkExecutor<UpToDateResult>(
            new SkipUpToDateStep<Context>(
                new StoreSnapshotsStep<Context>(outputFilesRepository,
                    new SnapshotOutputStep<Context>(buildInvocationScopeId.getId(),
                        new CreateOutputsStep<Context, Result>(
                            new CatchExceptionStep<Context>(
                                new ExecuteStep(outputChangeListener)
                            )
                        )
                    )
                )
            )
        )
    }

    def "outputs are created"() {
        when:
        def unitOfWork = builder.withOutputDirs(
            dir: [file("outDir")],
            dirs: [file("outDir1"), file("outDir2")],
        ).withOutputFiles(
            "file": [file("parent/outFile")],
            "files": [file("parent1/outFile"), file("parent2/outputFile1"), file("parent2/outputFile2")],
        ).withWork { ->
            true
        }.build()
        execute(unitOfWork)

        then:
        def allDirs = ["outDir", "outDir1", "outDir2"].collect { file(it) }
        def allFiles = ["parent/outFile", "parent1/outFile1", "parent2/outFile1", "parent2/outFile2"].collect { file(it) }
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
        result.finalOutputs.keySet() == ["dir", "emptyDir", "file", "missingDir", "missingFile"] as Set
        result.finalOutputs["dir"].rootHashes.size() == 1
        result.originMetadata.buildInvocationId == buildInvocationScopeId.id
        def afterExecution = Iterables.getOnlyElement(executionHistoryStore.executionHistory.values())
        afterExecution.originMetadata.buildInvocationId == buildInvocationScopeId.id
        afterExecution.outputFileProperties.values()*.rootHashes == result.finalOutputs.values()*.rootHashes
        result.outcome == ExecutionOutcome.EXECUTED
    }

    def "work unit is up-to-date if nothing changes"() {
        when:
        def result = execute(unitOfWork)
        then:
        result.outcome == ExecutionOutcome.EXECUTED
        !result.failure
        def origin = result.originMetadata.buildInvocationId
        def finalOutputs = result.finalOutputs

        when:
        buildInvocationScopeId = new BuildInvocationScopeId(UniqueId.generate())
        result = upToDate(builder.withWork { ->
            throw new IllegalStateException("Must not be executed")
        }.build())
        then:
        result.originMetadata.buildInvocationId == origin
        result.originMetadata.buildInvocationId != buildInvocationScopeId.id
        result.finalOutputs.values()*.rootHashes == finalOutputs.values()*.rootHashes
    }

    def "out-of-date for an output file change"() {
        when:
        def result = execute(unitOfWork)
        then:
        result.outcome == ExecutionOutcome.EXECUTED
        !result.failure
        def origin = result.originMetadata.buildInvocationId

        when:
        buildInvocationScopeId = new BuildInvocationScopeId(UniqueId.generate())
        outputFile.text = "outdated"
        result = outOfDate(builder.build(), outputFilesChanged(file: [outputFile]))
        then:
        result.originMetadata.buildInvocationId == buildInvocationScopeId.id
        result.originMetadata.buildInvocationId != origin
    }

    def "failed executions are never up-to-date"() {
        when:
        def result = execute(builder.withWork { -> throw new RuntimeException() }.build())
        then:
        result.outcome == ExecutionOutcome.EXECUTED
        result.failure
        def origin = result.originMetadata.buildInvocationId

        when:
        buildInvocationScopeId = new BuildInvocationScopeId(UniqueId.generate())
        result = outOfDate(builder.build(), "Task has failed previously.")
        then:
        result.originMetadata.buildInvocationId == buildInvocationScopeId.id
        result.originMetadata.buildInvocationId != origin
    }

    def "out of date when no history"() {
        when:
        def result = execute(unitOfWork)
        then:
        result.outcome == ExecutionOutcome.EXECUTED
        result.outOfDateReasons == ["No history is available."]
    }

    def "out of date when output file removed"() {
        when:
        def result = execute(unitOfWork)
        then:
        result.outcome == ExecutionOutcome.EXECUTED

        when:
        outputFile.delete()
        result = execute(unitOfWork)
        then:
        result.outcome == ExecutionOutcome.EXECUTED
        result.outOfDateReasons == ["Output property 'file' file ${outputFile.absolutePath} has been removed."]
    }

    def "out of date when output file in output dir removed"() {
        when:
        def result = execute(unitOfWork)
        then:
        result.outcome == ExecutionOutcome.EXECUTED

        when:
        outputDirFile.delete()
        result = execute(unitOfWork)
        then:
        result.outcome == ExecutionOutcome.EXECUTED
        result.outOfDateReasons == ["Output property 'dir' file ${outputDirFile.absolutePath} has been removed."]
    }

    def "out of date when output file has changed type"() {
        when:
        def result = execute(unitOfWork)
        then:
        result.outcome == ExecutionOutcome.EXECUTED

        when:
        outputFile.delete()
        outputFile.createDir()
        result = execute(unitOfWork)
        then:
        result.outcome == ExecutionOutcome.EXECUTED
        result.outOfDateReasons == ["Output property 'file' file ${outputFile.absolutePath} has changed."]
    }

    def "out of date when any file in output dir has changed type"() {
        when:
        def result = execute(unitOfWork)
        then:
        result.outcome == ExecutionOutcome.EXECUTED

        when:
        outputDirFile.delete()
        outputDirFile.createDir()
        result = execute(unitOfWork)
        then:
        result.outcome == ExecutionOutcome.EXECUTED
        result.outOfDateReasons == ["Output property 'dir' file ${outputDirFile.absolutePath} has changed."]
    }

    def "out of date when any output file has changed contents"() {
        when:
        def result = execute(unitOfWork)
        then:
        result.outcome == ExecutionOutcome.EXECUTED

        when:
        outputFile << "new content"
        result = execute(unitOfWork)
        then:
        result.outcome == ExecutionOutcome.EXECUTED
        result.outOfDateReasons == ["Output property 'file' file ${outputFile.absolutePath} has changed."]
    }

    def "out of date when any file in output dir has changed contents"() {
        when:
        def result = execute(unitOfWork)
        then:
        result.outcome == ExecutionOutcome.EXECUTED

        when:
        outputDirFile << "new content"
        result = execute(unitOfWork)
        then:
        result.outcome == ExecutionOutcome.EXECUTED
        result.outOfDateReasons == ["Output property 'dir' file ${outputDirFile.absolutePath} has changed."]
    }

    def "out-of-date when any output files properties are added"() {
        when:
        execute(unitOfWork)
        def outputFilesAddedUnitOfWork = builder.withOutputFiles(*:outputFiles, newFile: [temporaryFolder.createFile("output-file-2")]).build()
        then:
        outOfDate(outputFilesAddedUnitOfWork, "Output property 'newFile' has been added for ${outputFilesAddedUnitOfWork.displayName}")
    }

    def "out-of-date when any output file properties are removed"() {
        when:
        execute(unitOfWork)
        outputFiles.removeAll { it.key == "file"}
        def outputFilesRemovedUnitOfWork = builder.withOutputFiles(outputFiles).build()
        def result = execute(outputFilesRemovedUnitOfWork)

        then:
        result.outcome == ExecutionOutcome.EXECUTED
        result.outOfDateReasons == ["Output property 'file' has been removed for ${outputFilesRemovedUnitOfWork.displayName}"]
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

    def "up to date when output file which did not exist now exists"() {
        given:
        execute(unitOfWork)

        when:
        missingOutputFile.touch()

        then:
        upToDate(unitOfWork)
    }

    def "up to date when output dir which was empty is no longer empty"() {
        given:
        execute(unitOfWork)

        when:
        emptyOutputDir.file("some-file").touch()

        then:
        upToDate(unitOfWork)
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
        assert result.outcome == ExecutionOutcome.EXECUTED
        assert result.outOfDateReasons == expectedReasons
        return result
    }

    UpToDateResult upToDate(UnitOfWork unitOfWork) {
        def result = execute(unitOfWork)
        assert result.outcome == ExecutionOutcome.UP_TO_DATE
        assert result.failure == null
        return result
    }

    UpToDateResult execute(UnitOfWork unitOfWork) {
        fileSystemMirror.beforeBuildFinished()
        executor.execute(unitOfWork)
    }

    private TestFile file(Object... path) {
        return temporaryFolder.file(path)
    }

    static class OutputPropertySpec {
        FileCollection roots
        TreeType treeType

        OutputPropertySpec(Iterable<File> roots, TreeType treeType) {
            this.treeType = treeType
            this.roots = ImmutableFileCollection.of(roots)
        }
    }

    static OutputPropertySpec outputDirectorySpec(File... dirs) {
        return new OutputPropertySpec(ImmutableList.<File>copyOf(dirs), TreeType.DIRECTORY)
    }

    static OutputPropertySpec outputFileSpec(File... files) {
        return new OutputPropertySpec(ImmutableList.<File>copyOf(files), TreeType.FILE)
    }

    UnitOfWorkBuilder getBuilder() {
        new UnitOfWorkBuilder()
    }

    class UnitOfWorkBuilder {
        private BooleanSupplier work = { ->
            create.each { it ->
                it.createFile()
            }
            return true
        }
        private Map<String, Object> inputProperties = [prop: "value"]
        private Map<String, ? extends Collection<? extends File>> inputs = inputFiles
        private Map<String, ? extends Collection<? extends File>> outputFiles = IncrementalExecutionTest.this.outputFiles
        private Map<String, ? extends Collection<? extends File>> outputDirs = IncrementalExecutionTest.this.outputDirs
        private Collection<? extends TestFile> create = createFiles
        private ImplementationSnapshot implementation = ImplementationSnapshot.of(UnitOfWork.name, HashCode.fromInt(1234))
        private

        UnitOfWorkBuilder withWork(BooleanSupplier closure) {
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
            return withOutputFiles(defaultFiles: Arrays.asList(outputFiles))
        }

        UnitOfWorkBuilder withOutputFiles(Map<String, ? extends Collection<? extends File>> files) {
            this.outputFiles = files
            return this
        }

        UnitOfWorkBuilder withOutputDirs(File... outputDirs) {
            return withOutputDirs(defaultDir: Arrays.asList(outputDirs))
        }

        UnitOfWorkBuilder withOutputDirs(Map<String, ? extends Collection<? extends File>> dirs) {
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

        UnitOfWork build() {
            def outputFileSpecs = outputFiles.collectEntries { key, value -> [(key): outputFileSpec(*value)] }
            def outputDirSpecs = outputDirs.collectEntries { key, value -> [(key): outputDirectorySpec(*value)]}
            return new UnitOfWork() {
                private final Map<String, OutputPropertySpec> outputs = outputFileSpecs + outputDirSpecs
                private final ImplementationSnapshot implementationSnapshot = implementation
                private final ImmutableList<ImplementationSnapshot> additionalImplementationSnapshots = ImmutableList.of()
                Optional<ExecutionStateChanges> changes

                boolean executed

                boolean execute() {
                    executed = true
                    return work.asBoolean
                }

                @Override
                Optional<Duration> getTimeout() {
                    throw new UnsupportedOperationException()
                }

                @Override
                void visitOutputProperties(UnitOfWork.OutputPropertyVisitor visitor) {
                    outputs.forEach { name, spec ->
                        visitor.visitOutputProperty(name, spec.treeType, spec.roots)
                    }
                }

                @Override
                long markExecutionTime() {
                    0
                }

                @Override
                void visitLocalState(CacheableEntity.LocalStateVisitor visitor) {
                    throw new UnsupportedOperationException()
                }

                @Override
                void outputsRemovedAfterFailureToLoadFromCache() {
                    throw new UnsupportedOperationException()
                }

                @Override
                CacheHandler createCacheHandler() {
                    throw new UnsupportedOperationException()
                }

                @Override
                void persistResult(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> finalOutputs, boolean successful, OriginMetadata originMetadata) {
                    executionHistoryStore.store(
                        getIdentity(),
                        originMetadata,
                        implementationSnapshot,
                        additionalImplementationSnapshots,
                        snapshotInputProperties(),
                        snapshotInputFiles(),
                        finalOutputs,
                        successful
                    )
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

                ImplementationSnapshot implementationSnapshot = implementation


                @Override
                Optional<ExecutionStateChanges> getChangesSincePreviousExecution() {
                    changes = Optional.ofNullable(executionHistoryStore.load(getIdentity())).map { previous ->
                        def outputsBefore = snapshotOutputs()
                        def beforeExecutionState = new DefaultBeforeExecutionState(implementationSnapshot, additionalImplementationSnapshots, snapshotInputProperties(), snapshotInputFiles(), outputsBefore)
                        return new DefaultExecutionStateChanges(previous, beforeExecutionState, this)
                    }
                }

                private ImmutableSortedMap<String, ValueSnapshot> snapshotInputProperties() {
                    def builder = ImmutableSortedMap.<String, ValueSnapshot>naturalOrder()
                    inputProperties.each { propertyName, value ->
                        builder.put(propertyName, valueSnapshotter.snapshot(value))
                    }
                    return builder.build()
                }

                private ImmutableSortedMap<String, CurrentFileCollectionFingerprint> snapshotInputFiles() {
                    def builder = ImmutableSortedMap.<String, CurrentFileCollectionFingerprint>naturalOrder()
                    inputs.each { propertyName, value ->
                        builder.put(propertyName, fingerprinter.fingerprint(ImmutableFileCollection.of(value)))
                    }
                    return builder.build()

                }

                @Override
                ImmutableSortedMap<String, CurrentFileCollectionFingerprint> snapshotAfterOutputsGenerated() {
                    snapshotOutputs()
                }

                private ImmutableSortedMap<String, CurrentFileCollectionFingerprint> snapshotOutputs() {
                    def builder = ImmutableSortedMap.<String, CurrentFileCollectionFingerprint>naturalOrder()
                    outputs.each { propertyName, spec ->
                        builder.put(propertyName, outputFingerprinter.fingerprint(spec.roots))
                    }
                    return builder.build()
                }
            }
        }
    }
}
