/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.changedetection.changes

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.changedetection.TaskArtifactState
import org.gradle.api.internal.changedetection.state.*
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.hash.DefaultHasher
import org.gradle.api.tasks.incremental.InputFileDetails
import org.gradle.cache.CacheRepository
import org.gradle.cache.internal.CacheScopeMapping
import org.gradle.cache.internal.DefaultCacheRepository
import org.gradle.internal.id.RandomLongIdGenerator
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.serialize.DefaultSerializerRegistry
import org.gradle.internal.serialize.SerializerRegistry
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.InMemoryCacheFactory
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.util.WrapUtil.toMap
import static org.gradle.util.WrapUtil.toSet

public class DefaultTaskArtifactStateRepositoryTest extends Specification {

    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final project = TestUtil.createRootProject()
    final gradle = project.getGradle()
    final outputFile = tmpDir.file("output-file")
    final outputDir = tmpDir.file("output-dir")
    final outputDirFile = outputDir.file("some-file")
    final outputDirFile2 = outputDir.file("some-file-2")
    final emptyOutputDir = tmpDir.file("empty-output-dir")
    final missingOutputFile = tmpDir.file("missing-output-file")
    final inputFile = tmpDir.createFile("input-file")
    final inputDir = tmpDir.createDir("input-dir")
    final inputDirFile = inputDir.file("input-file2").createFile()
    final missingInputFile = tmpDir.file("missing-input-file")
    final inputFiles = toSet(inputFile, inputDir, missingInputFile)
    final outputFiles = toSet(outputFile, outputDir, emptyOutputDir, missingOutputFile)
    final createFiles = toSet(outputFile, outputDirFile, outputDirFile2)
    TaskInternal task = builder.task()
    def mapping = Stub(CacheScopeMapping) {
        getBaseDirectory(_, _, _) >> {
            return tmpDir.createDir("history-cache")
        }
    }
    DefaultTaskArtifactStateRepository repository
    CachingTreeVisitor treeVisitor

    def setup() {
        CacheRepository cacheRepository = new DefaultCacheRepository(mapping, new InMemoryCacheFactory())
        TaskArtifactStateCacheAccess cacheAccess = new DefaultTaskArtifactStateCacheAccess(gradle, cacheRepository, new NoOpDecorator())
        def stringInterner = new StringInterner()
        def snapshotter = new CachingFileSnapshotter(new DefaultHasher(), cacheAccess, stringInterner)
        treeVisitor = new CachingTreeVisitor()
        def treeSnapshotRepository = new TreeSnapshotRepository(cacheAccess, stringInterner)
        FileCollectionSnapshotter inputFilesSnapshotter = new DefaultFileCollectionSnapshotter(snapshotter, cacheAccess, stringInterner, TestFiles.resolver(), treeVisitor, treeSnapshotRepository)
        FileCollectionSnapshotter discoveredFilesSnapshotter = new MinimalFileSetSnapshotter(snapshotter, cacheAccess, stringInterner, TestFiles.resolver(), TestFiles.fileSystem())
        FileCollectionSnapshotter outputFilesSnapshotter = new OutputFilesCollectionSnapshotter(inputFilesSnapshotter, stringInterner)
        SerializerRegistry<FileCollectionSnapshot> serializerRegistry = new DefaultSerializerRegistry<FileCollectionSnapshot>();
        inputFilesSnapshotter.registerSerializers(serializerRegistry);
        outputFilesSnapshotter.registerSerializers(serializerRegistry);
        discoveredFilesSnapshotter.registerSerializers(serializerRegistry);
        TaskHistoryRepository taskHistoryRepository = new CacheBackedTaskHistoryRepository(cacheAccess, new CacheBackedFileSnapshotRepository(cacheAccess, serializerRegistry.build(FileCollectionSnapshot), new RandomLongIdGenerator(), treeSnapshotRepository), stringInterner)
        repository = new DefaultTaskArtifactStateRepository(taskHistoryRepository, DirectInstantiator.INSTANCE, outputFilesSnapshotter, inputFilesSnapshotter, discoveredFilesSnapshotter, TestFiles.fileCollectionFactory())
    }

    def artifactsAreNotUpToDateWhenCacheIsEmpty() {
        expect:
        outOfDate(task)
    }

    def artifactsAreNotUpToDateWhenAnyOutputFileNoLongerExists() {
        given:
        execute(task)

        when:
        outputFile.delete()

        then:
        outOfDate task
    }

    def artifactsAreNotUpToDateWhenAnyFileInOutputDirNoLongerExists() {
        given:
        execute(task)

        when:
        outputDirFile.delete()

        then:
        outOfDate task
    }

    def artifactsAreNotUpToDateWhenAnyOutputFileHasChangedType() {
        given:
        execute(task)

        when:
        outputFile.delete()
        outputFile.createDir()

        then:
        outOfDate task
    }

    def artifactsAreNotUpToDateWhenAnyFileInOutputDirHasChangedType() {
        given:
        execute(task)

        when:
        outputDirFile.delete()
        outputDirFile.createDir()

        then:
        outOfDate task
    }

    def artifactsAreNotUpToDateWhenAnyOutputFileHasChangedHash() {
        given:
        execute(task)

        when:
        outputFile.write("new content")

        then:
        outOfDate task
    }

    def artifactsAreNotUpToDateWhenAnyFileInOutputDirHasChangedHash() {
        given:
        execute(task)

        when:
        outputDirFile.write("new content")

        then:
        outOfDate task
    }

    def artifactsAreNotUpToDateWhenAnyOutputFilesAddedToSet() {
        when:
        execute(task)
        TaskInternal outputFilesAddedTask = builder.withOutputFiles(outputFile, outputDir, tmpDir.createFile("output-file-2"), emptyOutputDir, missingOutputFile).task()

        then:
        outOfDate outputFilesAddedTask
    }

    def artifactsAreNotUpToDateWhenAnyOutputFilesRemovedFromSet() {
        when:
        execute(task)
        TaskInternal outputFilesRemovedTask = builder.withOutputFiles(outputFile, emptyOutputDir, missingOutputFile).task()

        then:
        outOfDate outputFilesRemovedTask
    }

    def artifactsAreNotUpToDateWhenTaskWithDifferentTypeGeneratedAnyOutputFiles() {
        when:
        TaskInternal task1 = builder.withOutputFiles(outputFile).task()
        TaskInternal task2 = builder.withType(TaskSubType.class).withOutputFiles(outputFile).task()
        execute(task1, task2)

        then:
        outOfDate task
    }

    def artifactsAreNotUpToDateWhenAnyInputFilesAddedToSet() {
        final addedFile = tmpDir.createFile("other-input")

        when:
        execute(task)
        TaskInternal inputFilesAdded = builder.withInputFiles(inputFile, inputDir, addedFile, missingInputFile).task()

        then:
        inputsOutOfDate(inputFilesAdded).withAddedFile(addedFile)
    }

    def artifactsAreNotUpToDateWhenAnyInputFilesRemovedFromSet() {
        when:
        execute(task)
        TaskInternal inputFilesRemoved = builder.withInputFiles(inputFile).task()

        then:
        inputsOutOfDate(inputFilesRemoved).withRemovedFile(inputDirFile)
    }

    def artifactsAreNotUpToDateWhenAnyInputFileHasChangedHash() {
        given:
        execute(task)

        when:
        inputFile.write("some new content")

        then:
        inputsOutOfDate(task).withModifiedFile(inputFile)
    }

    def artifactsAreNotUpToDateWhenAnyInputFileHasChangedType() {
        given:
        execute(task)

        when:
        inputFile.delete()
        inputFile.createDir()

        then:
        inputsOutOfDate(task).withRemovedFile(inputFile)
    }

    def artifactsAreNotUpToDateWhenAnyInputFileNoLongerExists() {
        given:
        execute(task)

        when:
        inputFile.delete()

        then:
        inputsOutOfDate(task).withRemovedFile(inputFile)
    }

    def artifactsAreNotUpToDateWhenAnyFileCreatedInInputDir() {
        given:
        execute(task)

        when:
        def file = inputDir.file("other-file").createFile()
        treeVisitor.clearCache()

        then:
        inputsOutOfDate(task).withAddedFile(file)
    }

    def artifactsAreNotUpToDateWhenAnyFileDeletedFromInputDir() {
        given:
        execute(task)

        when:
        inputDirFile.delete()
        treeVisitor.clearCache()

        then:
        inputsOutOfDate(task).withRemovedFile(inputDirFile)
    }

    def artifactsAreNotUpToDateWhenAnyFileInInputDirChangesHash() {
        given:
        execute(task)

        when:
        inputDirFile.writelns("new content")
        treeVisitor.clearCache()

        then:
        inputsOutOfDate(task).withModifiedFile(inputDirFile)
    }

    def artifactsAreNotUpToDateWhenAnyFileInInputDirChangesType() {
        given:
        execute(task)

        when:
        inputDirFile.delete()
        inputDirFile.mkdir()
        treeVisitor.clearCache()

        then:
        inputsOutOfDate(task).withModifiedFile(inputDirFile)
    }

    def artifactsAreNotUpToDateWhenAnyInputPropertyValueChanged() {
        when:
        execute(builder.withProperty("prop", "original value").task())
        final inputPropertiesTask = builder.withProperty("prop", "new value").task()

        then:
        outOfDate inputPropertiesTask
    }

    def inputPropertyValueCanBeNull() {
        when:
        TaskInternal task = builder.withProperty("prop", null).task()
        execute(task)

        then:
        upToDate(task)
    }

    def artifactsAreNotUpToDateWhenAnyInputPropertyAdded() {
        when:
        execute(task)
        final addedPropertyTask = builder.withProperty("prop2", "value").task()

        then:
        outOfDate addedPropertyTask
    }

    def artifactsAreNotUpToDateWhenAnyInputPropertyRemoved() {
        given:
        execute(builder.withProperty("prop2", "value").task())

        expect:
        outOfDate task
    }

    def artifactsAreNotUpToDateWhenStateHasNotBeenUpdated() {
        when:
        repository.getStateFor(task)

        then:
        outOfDate task
    }

    def artifactsAreNotUpToDateWhenOutputDirWhichUsedToExistHasBeenDeleted() {
        given:
        // Output dir already exists before first execution of task
        outputDir.createDir()

        TaskInternal task1 = builder.withOutputFiles(outputDir).task()
        TaskInternal task2 = builder.withPath("other").withOutputFiles(outputDir).task()

        when:
        TaskArtifactState state = repository.getStateFor(task1)
        state.isUpToDate([])
        state.beforeTask()
        outputDirFile.createFile()
        state.afterTask()

        then:
        !state.upToDate

        when:
        outputDir.deleteDir()

        and:
        // Another task creates dir
        state = repository.getStateFor(task2)

        then:
        !state.isUpToDate([])

        when:
        state.beforeTask()
        outputDirFile2.createFile()
        state.afterTask()

        then:
        // Task should be out-of-date
        outOfDate task1
    }

    def artifactsAreUpToDateWhenNothingHasChangedSinceOutputFilesWereGenerated() {
        given:
        execute(task)

        expect:
        repository.getStateFor(task).isUpToDate([])
        repository.getStateFor(task).isUpToDate([])
    }

    def artifactsAreUpToDateWhenOutputFileWhichDidNotExistNowExists() {
        given:
        execute(task)

        when:
        missingOutputFile.touch()

        then:
        upToDate task
    }

    def artifactsAreUpToDateWhenOutputDirWhichWasEmptyIsNoLongerEmpty() {
        given:
        execute(task)

        when:
        emptyOutputDir.file("some-file").touch()

        then:
        upToDate task
    }

    def hasEmptyTaskHistoryWhenTaskHasNeverBeenExecuted() {
        when:
        TaskArtifactState state = repository.getStateFor(task)

        then:
        state.getExecutionHistory().getOutputFiles().getFiles().isEmpty()
    }

    def hasTaskHistoryFromPreviousExecution() {
        given:
        execute(task)

        when:
        TaskArtifactState state = repository.getStateFor(task)

        then:
        state.getExecutionHistory().getOutputFiles().getFiles() == [outputFile, outputDirFile, outputDirFile2] as Set
    }

    def multipleTasksCanProduceFilesIntoTheSameOutputDirectory() {
        when:
        TaskInternal task1 = task
        TaskInternal task2 = builder.withPath("other").withOutputFiles(outputDir).createsFiles(outputDir.file("output2")).task()
        execute(task1, task2)

        then:
        upToDate task1
        upToDate task2
    }

    def multipleTasksCanProduceTheSameFileWithTheSameContents() {
        when:
        TaskInternal task1 = builder.withOutputFiles(outputFile).task()
        TaskInternal task2 = builder.withPath("other").withOutputFiles(outputFile).task()
        execute(task1, task2)

        then:
        upToDate task1
        upToDate task2
    }

    def multipleTasksCanProduceTheSameEmptyDir() {
        when:
        TaskInternal task1 = task
        TaskInternal task2 = builder.withPath("other").withOutputFiles(outputDir).task()
        execute(task1, task2)

        then:
        upToDate task1
        upToDate task2
    }

    def doesNotConsiderExistingFilesInOutputDirectoryAsProducedByTask() {
        when:
        TestFile otherFile = outputDir.file("other").createFile()
        execute(task)
        otherFile.delete()

        then:
        TaskArtifactState state = repository.getStateFor(task)
        state.isUpToDate([])
        !state.getExecutionHistory().getOutputFiles().getFiles().contains(otherFile)
    }

    def considersExistingFileInOutputDirectoryWhichIsUpdatedByTheTaskAsProducedByTask() {
        when:
        TestFile otherFile = outputDir.file("other").createFile()
        TaskArtifactState state = repository.getStateFor(task)

        then:
        !state.isUpToDate([])

        when:
        state.beforeTask()
        task.execute()
        otherFile.write("new content")
        state.afterTask()
        otherFile.delete()

        then:
        def stateAfter = repository.getStateFor(task)
        !stateAfter.upToDate
        stateAfter.executionHistory.outputFiles.files.contains(otherFile)
    }

    def fileIsNoLongerConsideredProducedByTaskOnceItIsDeleted() {
        given:
        execute(task)

        outputDirFile.delete()
        TaskArtifactState state = repository.getStateFor(task)
        state.isUpToDate([])
        state.afterTask()

        when:
        outputDirFile.write("ignore me")

        then:
        def stateAfter = repository.getStateFor(task)
        stateAfter.isUpToDate([])
        !stateAfter.executionHistory.outputFiles.files.contains(outputDirFile)
    }

    def artifactsAreUpToDateWhenTaskDoesNotAcceptAnyInputs() {
        when:
        TaskInternal noInputsTask = builder.doesNotAcceptInput().task()
        execute(noInputsTask)

        then:
        upToDate noInputsTask

        when:
        outputDirFile.delete()

        then:
        outOfDate noInputsTask
    }

    def artifactsAreUpToDateWhenTaskHasNoInputFiles() {
        when:
        TaskInternal noInputFilesTask = builder.withInputFiles().task()
        execute(noInputFilesTask)

        then:
        upToDate noInputFilesTask
    }

    def artifactsAreUpToDateWhenTaskHasNoOutputFiles() {
        when:
        TaskInternal noOutputsTask = builder.withOutputFiles().task()
        execute(noOutputsTask)

        then:
        upToDate noOutputsTask
    }

    def taskCanProduceIntoDifferentSetsOfOutputFiles() {
        when:
        TestFile outputDir2 = tmpDir.createDir("output-dir-2")
        TestFile outputDirFile2 = outputDir2.file("output-file-2")
        TaskInternal task1 = builder.withOutputFiles(outputDir).createsFiles(outputDirFile).task()
        TaskInternal task2 = builder.withOutputFiles(outputDir2).createsFiles(outputDirFile2).task()

        execute(task1, task2)

        then:
        def state1 = repository.getStateFor(task1)
        state1.isUpToDate([])
        state1.executionHistory.outputFiles.files == [outputDirFile] as Set

        and:
        def state2 = repository.getStateFor(task2)
        state2.isUpToDate([])
        state2.executionHistory.outputFiles.files == [outputDirFile2] as Set
    }

    private void outOfDate(TaskInternal task) {
        final state = repository.getStateFor(task)
        assert !state.isUpToDate([])
        assert !state.inputChanges.incremental
    }

    def inputsOutOfDate(TaskInternal task) {
        final state = repository.getStateFor(task)
        assert !state.isUpToDate([])

        final inputChanges = state.inputChanges
        assert inputChanges.incremental

        final changedFiles = new ChangedFiles()
        inputChanges.outOfDate(new Action<InputFileDetails>() {
            void execute(InputFileDetails t) {
                if (t.added) {
                    println "Added: " + t.file
                    changedFiles.added << t.file
                } else if (t.modified) {
                    println "Modified: " + t.file
                    changedFiles.modified << t.file
                } else {
                    assert false : "Not a valid change"
                }
            }
        })
        inputChanges.removed(new Action<InputFileDetails>() {
            void execute(InputFileDetails t) {
                println "Removed: " + t.file
                assert t.removed
                changedFiles.removed << t.file
            }
        })

        return changedFiles
    }

    private void upToDate(TaskInternal task) {
        final state = repository.getStateFor(task)
        assert state.isUpToDate([])
    }

    private void execute(TaskInternal... tasks) {
        for (TaskInternal task : tasks) {
            TaskArtifactState state = repository.getStateFor(task)
            state.isUpToDate([])
            state.beforeTask()
            task.execute()
            state.afterTask()
        }
    }

    private static class ChangedFiles {
        def added = []
        def modified = []
        def removed = []

        void withAddedFile(File file) {
            assert added == [file]
            assert modified == []
            assert removed == []
        }

        void withModifiedFile(File file) {
            assert added == []
            assert modified == [file]
            assert removed == []
        }

        void withRemovedFile(File file) {
            assert added == []
            assert modified == []
            assert removed == [file]
        }
    }

    private TaskBuilder getBuilder() {
        return new TaskBuilder()
    }

    private class TaskBuilder {
        private String path = "task"
        private Collection<? extends File> inputs = inputFiles
        private Collection<? extends File> outputs = outputFiles
        private Collection<? extends TestFile> create = createFiles
        private Class<? extends TaskInternal> type = TaskInternal.class
        private Map<String, Object> inputProperties = new HashMap<String, Object>(toMap("prop", "value"))

        TaskBuilder withInputFiles(File... inputFiles) {
            inputs = Arrays.asList(inputFiles)
            return this
        }

        TaskBuilder withOutputFiles(File... outputFiles) {
            outputs = Arrays.asList(outputFiles)
            return this
        }

        TaskBuilder createsFiles(TestFile... outputFiles) {
            create = Arrays.asList(outputFiles)
            return this
        }

        TaskBuilder withPath(String path) {
            this.path = path
            return this
        }

        TaskBuilder withType(Class<? extends TaskInternal> type) {
            this.type = type
            return this
        }

        TaskBuilder doesNotAcceptInput() {
            inputs = null
            inputProperties = null
            return this
        }

        public TaskBuilder withProperty(String name, Object value) {
            inputProperties.put(name, value)
            return this
        }

        TaskInternal task() {
            final TaskInternal task = TestUtil.createTask(type, project, path)
            if (inputs != null) {
                task.getInputs().files(inputs)
            }
            if (inputProperties != null) {
                task.getInputs().properties(inputProperties)
            }
            if (outputs != null) {
                task.getOutputs().files(outputs)
            }
            task.doLast(new org.gradle.api.Action<Object>() {
                public void execute(Object o) {
                    for (TestFile file : create) {
                        file.createFile()
                    }
                }
            })

            return task
        }
    }

    public static class TaskSubType extends DefaultTask {
    }

}
