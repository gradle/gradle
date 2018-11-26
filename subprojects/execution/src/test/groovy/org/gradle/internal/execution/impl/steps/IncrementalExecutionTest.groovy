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
import org.gradle.caching.internal.origin.OriginMetadata
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
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.TestFileHasher
import org.gradle.internal.id.UniqueId
import org.gradle.internal.scopeids.id.BuildInvocationScopeId
import org.gradle.internal.snapshot.ValueSnapshot
import org.gradle.internal.snapshot.WellKnownFileLocations
import org.gradle.internal.snapshot.impl.DefaultFileSystemMirror
import org.gradle.internal.snapshot.impl.DefaultFileSystemSnapshotter
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
    def executionHistoryStore = new TestExecutionHistoryStore()
    def outputChangeListener = Mock(OutputChangeListener)
    def outputFilesRepository = new TestOutputFilesRepository()
    def buildInvocationScopeId = new BuildInvocationScopeId(UniqueId.generate())

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
        executor.execute(new TestUnitOfWork([
            "dir": outputDirectory(file("outDir")),
            "dirs": outputDirectory(file("outDir1"), file("outDir2")),
            "file": outputFiles(file("parent/outFile")),
            "files": outputFiles(file("parent1/outFile"), file("parent2/outputFile1"), file("parent2/outputFile2")),
        ]))

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
        def outputDir = file("outputDir")

        when:
        def result = executor.execute(new TestUnitOfWork(
            ["outputDir": outputDirectory(outputDir)], { ->
            outputDir.file("result").text = "output"
            return true
        }))

        then:
        result.finalOutputs.keySet() == ["outputDir"] as Set
        result.finalOutputs["outputDir"].rootHashes.size() == 1
        result.originMetadata.buildInvocationId == buildInvocationScopeId.id
        def afterExecution = Iterables.getOnlyElement(executionHistoryStore.executionHistory.values())
        afterExecution.originMetadata.buildInvocationId == buildInvocationScopeId.id
        afterExecution.outputFileProperties.values()*.rootHashes == result.finalOutputs.values()*.rootHashes
        result.outcome == ExecutionOutcome.EXECUTED
    }

    def "work unit is up-to-date if nothing changes"() {
        def outputFile = file("outputFile")
        def outputs = [
            outputFile: outputFiles(outputFile)
        ]

        when:
        def result = executor.execute(new TestUnitOfWork(outputs, { -> true }))
        then:
        result.outcome == ExecutionOutcome.EXECUTED
        !result.failure
        def origin = result.originMetadata.buildInvocationId
        def finalOutputs = result.finalOutputs

        when:
        buildInvocationScopeId = new BuildInvocationScopeId(UniqueId.generate())
        result = executor.execute(new TestUnitOfWork(outputs, { -> throw new IllegalStateException("Must not be executed") }))
        then:
        result.outcome == ExecutionOutcome.UP_TO_DATE
        !result.failure
        result.originMetadata.buildInvocationId == origin
        result.originMetadata.buildInvocationId != buildInvocationScopeId.id
        result.finalOutputs.values()*.rootHashes == finalOutputs.values()*.rootHashes
    }

    def "work unit is out-of-date for an output file change"() {
        def outputFile = file("outputFile")
        def outputs = [
            outputFile: outputFiles(outputFile)
        ]

        when:
        def result = executor.execute(new TestUnitOfWork(outputs, { -> true }))
        then:
        result.outcome == ExecutionOutcome.EXECUTED
        !result.failure
        def origin = result.originMetadata.buildInvocationId

        when:
        fileSystemMirror.beforeBuildFinished()
        buildInvocationScopeId = new BuildInvocationScopeId(UniqueId.generate())
        outputFile.text = "outdated"
        result = executor.execute(new TestUnitOfWork(outputs, { -> true }))
        then:
        result.outcome == ExecutionOutcome.EXECUTED
        !result.failure
        result.originMetadata.buildInvocationId == buildInvocationScopeId.id
        result.originMetadata.buildInvocationId != origin
    }

    def "failed executions are never up-to-date"() {
        def outputFile = file("outputFile")
        def outputs = [
            outputFile: outputFiles(outputFile)
        ]

        when:
        def result = executor.execute(new TestUnitOfWork(outputs, { -> throw new RuntimeException() }))
        then:
        result.outcome == ExecutionOutcome.EXECUTED
        result.failure
        def origin = result.originMetadata.buildInvocationId

        when:
        fileSystemMirror.beforeBuildFinished()
        buildInvocationScopeId = new BuildInvocationScopeId(UniqueId.generate())
        result = executor.execute(new TestUnitOfWork(outputs, { -> true }))
        then:
        result.outcome == ExecutionOutcome.EXECUTED
        !result.failure
        result.originMetadata.buildInvocationId == buildInvocationScopeId.id
        result.originMetadata.buildInvocationId != origin
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

    static OutputPropertySpec outputDirectory(File... dirs) {
        return new OutputPropertySpec(ImmutableList.<File> of(dirs), TreeType.DIRECTORY)
    }

    static OutputPropertySpec outputFiles(File... files) {
        return new OutputPropertySpec(ImmutableList.<File> of(files), TreeType.FILE)
    }

    class TestUnitOfWork implements UnitOfWork {

        private final BooleanSupplier work
        private final Map<String, OutputPropertySpec> outputs
        private ImplementationSnapshot implementationSnapshot = ImplementationSnapshot.of(TestUnitOfWork.name, HashCode.fromInt(1234))
        private ImmutableList<ImplementationSnapshot> additionalImplementationSnapshots = ImmutableList.of()
        private final ImmutableSortedMap<String, ValueSnapshot> inputProperySnapshots = ImmutableSortedMap.of()
        private final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFingerprints = ImmutableSortedMap.of()

        TestUnitOfWork(Map<String, OutputPropertySpec> outputs, BooleanSupplier work = { -> true }) {
            this.outputs = outputs
            this.work = work
        }

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
        void visitOutputs(OutputVisitor outputVisitor) {
            outputs.forEach { name, spec ->
                outputVisitor.visitOutput(name, spec.treeType, spec.roots)
            }
        }

        @Override
        long markExecutionTime() {
            0
        }

        @Override
        FileCollection getLocalState() {
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
                inputProperySnapshots,
                inputFingerprints,
                finalOutputs,
                successful
            )
        }

        @Override
        Optional<ExecutionStateChanges> getChangesSincePreviousExecution() {
            Optional.ofNullable(executionHistoryStore.load(getIdentity())).map { previous ->
                def outputsBefore = snapshotOutputs()
                def beforeExecutionState = new DefaultBeforeExecutionState(implementationSnapshot, additionalImplementationSnapshots, inputProperySnapshots, inputFingerprints, outputsBefore)
                return new DefaultExecutionStateChanges(previous, beforeExecutionState, this)
            }
        }

        @Override
        Optional<? extends Iterable<String>> getChangingOutputs() {
            Optional.empty()
        }

        @Override
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> snapshotAfterOutputsGenerated() {
            snapshotOutputs()
        }

        private ImmutableSortedMap<String, CurrentFileCollectionFingerprint> snapshotOutputs() {
            Map<String, CurrentFileCollectionFingerprint> fingerprints = outputs.collectEntries { String name, OutputPropertySpec spec ->
                [(name): fingerprinter.fingerprint(spec.roots)]
            }
            ImmutableSortedMap.copyOf(fingerprints)
        }

        @Override
        String getIdentity() {
            "myId"
        }

        @Override
        void visitTrees(CacheableTreeVisitor visitor) {
            throw new UnsupportedOperationException()
        }

        @Override
        String getDisplayName() {
            "Test unit of work"
        }
    }

}
