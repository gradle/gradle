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

import com.google.common.hash.HashCode
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.cache.CrossBuildInMemoryCacheFactory
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.changedetection.TaskArtifactState
import org.gradle.api.internal.changedetection.state.CacheBackedFileSnapshotRepository
import org.gradle.api.internal.changedetection.state.CacheBackedTaskHistoryRepository
import org.gradle.api.internal.changedetection.state.DefaultFileCollectionSnapshotterRegistry
import org.gradle.api.internal.changedetection.state.DefaultFileSystemMirror
import org.gradle.api.internal.changedetection.state.DefaultFileSystemSnapshotter
import org.gradle.api.internal.changedetection.state.DefaultGenericFileCollectionSnapshotter
import org.gradle.api.internal.changedetection.state.DefaultTaskHistoryStore
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot
import org.gradle.api.internal.changedetection.state.InMemoryCacheDecoratorFactory
import org.gradle.api.internal.changedetection.state.TaskHistoryRepository
import org.gradle.api.internal.changedetection.state.TaskHistoryStore
import org.gradle.api.internal.changedetection.state.ValueSnapshotter
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.hash.DefaultFileHasher
import org.gradle.api.tasks.incremental.InputFileDetails
import org.gradle.cache.CacheRepository
import org.gradle.cache.internal.CacheScopeMapping
import org.gradle.cache.internal.DefaultCacheRepository
import org.gradle.caching.internal.tasks.TaskCacheKeyCalculator
import org.gradle.internal.classloader.ConfigurableClassLoaderHierarchyHasher
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.id.RandomLongIdGenerator
import org.gradle.internal.id.UniqueId
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.scopeids.id.BuildInvocationScopeId
import org.gradle.internal.serialize.DefaultSerializerRegistry
import org.gradle.internal.serialize.SerializerRegistry
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testfixtures.internal.InMemoryCacheFactory
import org.gradle.util.TestUtil

class DefaultTaskArtifactStateRepositoryTest extends AbstractProjectBuilderSpec {
    def gradle
    final outputFile = temporaryFolder.file("output-file")
    final outputDir = temporaryFolder.file("output-dir")
    final outputDirFile = outputDir.file("some-file")
    final outputDirFile2 = outputDir.file("some-file-2")
    final emptyOutputDir = temporaryFolder.file("empty-output-dir")
    final missingOutputFile = temporaryFolder.file("missing-output-file")
    final inputFile = temporaryFolder.createFile("input-file")
    final inputDir = temporaryFolder.createDir("input-dir")
    final inputDirFile = inputDir.file("input-file2").createFile()
    final missingInputFile = temporaryFolder.file("missing-input-file")
    final inputFiles = [file: [inputFile], dir: [inputDir], missingFile: [missingInputFile]]
    final outputFiles = [file: [outputFile], dir: [outputDir], emptyDir: [emptyOutputDir], missingFile: [missingOutputFile]]
    final createFiles = [outputFile, outputDirFile, outputDirFile2] as Set
    def buildScopeId = new BuildInvocationScopeId(UniqueId.generate())

    TaskInternal task
    def mapping = Stub(CacheScopeMapping) {
        getBaseDirectory(_, _, _) >> {
            return temporaryFolder.createDir("history-cache")
        }
    }
    DefaultGenericFileCollectionSnapshotter fileCollectionSnapshotter
    DefaultTaskArtifactStateRepository repository
    DefaultFileSystemMirror fileSystemMirror
    TaskCacheKeyCalculator cacheKeyCalculator = Mock(TaskCacheKeyCalculator)

    def setup() {
        gradle = project.getGradle()
        task = builder.task()
        CacheRepository cacheRepository = new DefaultCacheRepository(mapping, new InMemoryCacheFactory())
        CrossBuildInMemoryCacheFactory cacheFactory = new CrossBuildInMemoryCacheFactory(new DefaultListenerManager())
        TaskHistoryStore cacheAccess = new DefaultTaskHistoryStore(gradle, cacheRepository, new InMemoryCacheDecoratorFactory(false, cacheFactory))
        def stringInterner = new StringInterner()
        def snapshotter = new DefaultFileHasher()
        fileSystemMirror = new DefaultFileSystemMirror([])
        fileCollectionSnapshotter = new DefaultGenericFileCollectionSnapshotter(stringInterner, TestFiles.directoryFileTreeFactory(), new DefaultFileSystemSnapshotter(snapshotter, stringInterner, TestFiles.fileSystem(), TestFiles.directoryFileTreeFactory(), fileSystemMirror))
        def classLoaderHierarchyHasher = Mock(ConfigurableClassLoaderHierarchyHasher) {
            getClassLoaderHash(_) >> HashCode.fromInt(123)
        }
        SerializerRegistry serializerRegistry = new DefaultSerializerRegistry()
        fileCollectionSnapshotter.registerSerializers(serializerRegistry)
        TaskHistoryRepository taskHistoryRepository = new CacheBackedTaskHistoryRepository(cacheAccess, new CacheBackedFileSnapshotRepository(cacheAccess, serializerRegistry.build(FileCollectionSnapshot), new RandomLongIdGenerator()), stringInterner, buildScopeId)
        repository = new DefaultTaskArtifactStateRepository(taskHistoryRepository, DirectInstantiator.INSTANCE, new DefaultFileCollectionSnapshotterRegistry([fileCollectionSnapshotter]), TestFiles.fileCollectionFactory(), classLoaderHierarchyHasher, cacheKeyCalculator, new ValueSnapshotter(classLoaderHierarchyHasher))
    }

    def "artifacts are not up to date when cache is empty"() {
        expect:
        outOfDate(task)
    }

    def "artifacts are not up to date when any output file no longer exists"() {
        given:
        execute(task)

        when:
        outputFile.delete()

        then:
        outOfDate task
    }

    def "artifacts are not up to date when any file in output dir no longer exists"() {
        given:
        execute(task)

        when:
        outputDirFile.delete()

        then:
        outOfDate task
    }

    def "artifacts are not up to date when any output file has changed type"() {
        given:
        execute(task)

        when:
        outputFile.delete()
        outputFile.createDir()

        then:
        outOfDate task
    }

    def "artifacts are not up to date when any file in output dir has changed type"() {
        given:
        execute(task)

        when:
        outputDirFile.delete()
        outputDirFile.createDir()

        then:
        outOfDate task
    }

    def "artifacts are not up to date when any output file has changed hash"() {
        given:
        execute(task)

        when:
        outputFile.write("new content")

        then:
        outOfDate task
    }

    def "artifacts are not up to date when any file in output dir has changed hash"() {
        given:
        execute(task)

        when:
        outputDirFile.write("new content")

        then:
        outOfDate task
    }

    def "artifacts are not up to date when any output files added to set"() {
        when:
        execute(task)
        TaskInternal outputFilesAddedTask = builder.withOutputFiles(outputFile, outputDir, temporaryFolder.createFile("output-file-2"), emptyOutputDir, missingOutputFile).task()

        then:
        outOfDate outputFilesAddedTask
    }

    def "artifacts are not up to date when any output files removed from set"() {
        when:
        execute(task)
        TaskInternal outputFilesRemovedTask = builder.withOutputFiles(outputFile, emptyOutputDir, missingOutputFile).task()

        then:
        outOfDate outputFilesRemovedTask
    }

    def "artifacts are not up to date when task with different type generated any output files"() {
        when:
        TaskInternal task1 = builder.withOutputFiles(outputFile).task()
        TaskInternal task2 = builder.withType(TaskSubType.class).withOutputFiles(outputFile).task()
        execute(task1, task2)

        then:
        outOfDate task
    }

    def "artifacts are not up to date when any input files added to set"() {
        final addedFile = temporaryFolder.createFile("other-input")

        when:
        execute(task)
        TaskInternal inputFilesAdded = builder.withInputFiles(file: [inputFile, addedFile], dir: [inputDir], missingFile: [missingInputFile]).task()

        then:
        inputsOutOfDate(inputFilesAdded).withAddedFile(addedFile)
    }

    def "artifacts are not up to date when any input files removed from set"() {
        when:
        execute(task)
        TaskInternal inputFilesRemoved = builder.withInputFiles(file: [inputFile], dir: [], missingFile: [missingInputFile]).task()

        then:
        inputsOutOfDate(inputFilesRemoved).withRemovedFiles(inputDir, inputDirFile)
    }

    def "artifacts are not up to date when any input file has changed hash"() {
        given:
        execute(task)

        when:
        inputFile.write("some new content")

        then:
        inputsOutOfDate(task).withModifiedFile(inputFile)
    }

    def "artifacts are not up to date when any input file has changed type"() {
        given:
        execute(task)

        when:
        inputFile.delete()
        inputFile.createDir()

        then:
        inputsOutOfDate(task).withModifiedFile(inputFile)
    }

    def "artifacts are not up to date when any input file no longer exists"() {
        given:
        execute(task)

        when:
        inputFile.delete()

        then:
        inputsOutOfDate(task).withModifiedFile(inputFile)
    }

    def "artifacts are not up to date when any input file did not exist and now does"() {
        given:
        inputFile.delete()
        execute(task)

        when:
        inputFile.createNewFile()

        then:
        inputsOutOfDate(task).withModifiedFile(inputFile)
    }

    def "artifacts are not up to date when any file created in input dir"() {
        given:
        execute(task)

        when:
        def file = inputDir.file("other-file").createFile()

        then:
        inputsOutOfDate(task).withAddedFile(file)
    }

    def "artifacts are not up to date when any file deleted from input dir"() {
        given:
        execute(task)

        when:
        inputDirFile.delete()

        then:
        inputsOutOfDate(task).withRemovedFile(inputDirFile)
    }

    def "artifacts are not up to date when any file in input dir changes hash"() {
        given:
        execute(task)

        when:
        inputDirFile.writelns("new content")

        then:
        inputsOutOfDate(task).withModifiedFile(inputDirFile)
    }

    def "artifacts are not up to date when any file in input dir changes type"() {
        given:
        execute(task)

        when:
        inputDirFile.delete()
        inputDirFile.mkdir()

        then:
        inputsOutOfDate(task).withModifiedFile(inputDirFile)
    }

    def "artifacts are not up to date when any input property value changed"() {
        when:
        execute(builder.withProperty("prop", "original value").task())
        final inputPropertiesTask = builder.withProperty("prop", "new value").task()

        then:
        outOfDate inputPropertiesTask
    }

    def "input property value can be null"() {
        when:
        TaskInternal task = builder.withProperty("prop", null).task()
        execute(task)

        then:
        upToDate(task)
    }

    def "artifacts are not up to date when any input property added"() {
        when:
        execute(task)
        final addedPropertyTask = builder.withProperty("prop2", "value").task()

        then:
        outOfDate addedPropertyTask
    }

    def "artifacts are not up to date when any input property removed"() {
        given:
        execute(builder.withProperty("prop2", "value").task())

        expect:
        outOfDate task
    }

    def "artifacts are not up to date when state has not been updated"() {
        when:
        repository.getStateFor(task)

        then:
        outOfDate task
    }

    def "artifacts are not up to date when output dir which used to exist has been deleted"() {
        given:
        // Output dir already exists before first execution of task
        outputDir.createDir()

        TaskInternal task1 = builder.withOutputFiles(outputDir).task()
        TaskInternal task2 = builder.withPath("other").withOutputFiles(outputDir).task()

        when:
        TaskArtifactState state = repository.getStateFor(task1)
        state.isUpToDate([])
        fileSystemMirror.beforeTaskOutputsGenerated()
        outputDirFile.createFile()
        state.afterTask(null)

        then:
        !state.upToDate

        when:
        fileSystemMirror.beforeTaskOutputsGenerated()
        outputDir.deleteDir()

        and:
        // Another task creates dir
        state = repository.getStateFor(task2)

        then:
        !state.isUpToDate([])

        when:
        fileSystemMirror.beforeTaskOutputsGenerated()
        outputDirFile2.createFile()
        state.afterTask(null)

        then:
        // Task should be out-of-date
        outOfDate task1
    }

    def "artifacts are up to date when nothing has changed since output files were generated"() {
        given:
        execute(task)

        expect:
        repository.getStateFor(task).isUpToDate([])
        repository.getStateFor(task).isUpToDate([])
    }

    def "artifacts are up to date when output file which did not exist now exists"() {
        given:
        execute(task)

        when:
        missingOutputFile.touch()

        then:
        upToDate task
    }

    def "artifacts are up to date when output dir which was empty is no longer empty"() {
        given:
        execute(task)

        when:
        emptyOutputDir.file("some-file").touch()

        then:
        upToDate task
    }

    def "has empty task history when task has never been executed"() {
        when:
        TaskArtifactState state = repository.getStateFor(task)

        then:
        state.getExecutionHistory().getOutputFiles().getFiles().isEmpty()
    }

    def "has task history from previous execution"() {
        given:
        execute(task)

        when:
        TaskArtifactState state = repository.getStateFor(task)

        then:
        state.getExecutionHistory().getOutputFiles().getFiles() == [outputFile, outputDirFile, outputDirFile2] as Set
    }

    def "multiple tasks can produce files into the same output directory"() {
        when:
        TaskInternal task1 = task
        TaskInternal task2 = builder.withPath("other").withOutputFiles(outputDir).createsFiles(outputDir.file("output2")).task()
        execute(task1, task2)

        then:
        upToDate task1
        upToDate task2
    }

    def "multiple tasks can produce the same file with the same contents"() {
        when:
        TaskInternal task1 = builder.withOutputFiles(outputFile).task()
        TaskInternal task2 = builder.withPath("other").withOutputFiles(outputFile).task()
        execute(task1, task2)

        then:
        upToDate task1
        upToDate task2
    }

    def "multiple tasks can produce the same empty dir"() {
        when:
        TaskInternal task1 = task
        TaskInternal task2 = builder.withPath("other").withOutputFiles(outputDir).task()
        execute(task1, task2)

        then:
        upToDate task1
        upToDate task2
    }

    def "does not consider existing files in output directory as produced by task"() {
        when:
        TestFile otherFile = outputDir.file("other").createFile()
        execute(task)
        otherFile.delete()

        then:
        TaskArtifactState state = repository.getStateFor(task)
        state.isUpToDate([])
        !state.getExecutionHistory().getOutputFiles().getFiles().contains(otherFile)
    }

    def "considers existing file in output directory which is updated by the task as produced by task"() {
        when:
        TestFile otherFile = outputDir.file("other").createFile()
        TaskArtifactState state = repository.getStateFor(task)

        then:
        !state.isUpToDate([])

        when:
        task.execute()
        fileSystemMirror.beforeTaskOutputsGenerated()
        otherFile.write("new content")
        state.afterTask(null)
        otherFile.delete()

        then:
        def stateAfter = repository.getStateFor(task)
        !stateAfter.upToDate
        stateAfter.executionHistory.outputFiles.files.contains(otherFile)
    }

    def "file is no longer considered produced by task once it is deleted"() {
        given:
        execute(task)

        outputDirFile.delete()
        TaskArtifactState state = repository.getStateFor(task)
        state.isUpToDate([])
        state.afterTask(null)

        when:
        outputDirFile.write("ignore me")

        then:
        def stateAfter = repository.getStateFor(task)
        stateAfter.isUpToDate([])
        !stateAfter.executionHistory.outputFiles.files.contains(outputDirFile)
    }

    def "artifacts are up to date when task does not accept any inputs"() {
        when:
        TaskInternal noInputsTask = builder.doesNotAcceptInput().task()
        execute(noInputsTask)

        then:
        upToDate noInputsTask

        when:
        fileSystemMirror.beforeTaskOutputsGenerated()
        outputDirFile.delete()

        then:
        outOfDate noInputsTask
    }

    def "artifacts are up to date when task has no input files"() {
        when:
        TaskInternal noInputFilesTask = builder.withInputFiles().task()
        execute(noInputFilesTask)

        then:
        upToDate noInputFilesTask
    }

    def "artifacts are up to date when task has no output files"() {
        when:
        TaskInternal noOutputsTask = builder.withOutputFiles().task()
        execute(noOutputsTask)

        then:
        upToDate noOutputsTask
    }

    def "task can produce into different sets of output files"() {
        when:
        TestFile outputDir2 = temporaryFolder.createDir("output-dir-2")
        TestFile outputDirFile2 = outputDir2.file("output-file-2")
        TaskInternal task1 = builder.withOutputFiles(dir: [outputDir]).createsFiles(outputDirFile).withPath('task1').task()
        TaskInternal task2 = builder.withOutputFiles(dir: [outputDir2]).createsFiles(outputDirFile2).withPath('task2').task()

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

    def "has no origin build ID when not executed"() {
        expect:
        repository.getStateFor(task).originBuildInvocationId == null
    }

    def "has origin build ID after executed"() {
        when:
        execute(task)

        then:
        repository.getStateFor(task).originBuildInvocationId == buildScopeId.id
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
                    assert false: "Not a valid change"
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
            // reset state
            fileSystemMirror.beforeTaskOutputsGenerated()
            task.execute()
            state.afterTask(null)
        }
        // reset state
        fileSystemMirror.beforeTaskOutputsGenerated()
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

        void withRemovedFiles(File... files) {
            assert added == []
            assert modified == []
            assert removed == files as List
        }
    }

    private TaskBuilder getBuilder() {
        return new TaskBuilder()
    }

    private class TaskBuilder {
        private String path = "task"
        private Map<String, Object> inputProperties = [prop: "value"]
        private Map<String, ? extends Collection<? extends File>> inputs = inputFiles
        private Map<String, ? extends Collection<? extends File>> outputs = outputFiles
        private Collection<? extends TestFile> create = createFiles
        private Class<? extends TaskInternal> type = TaskInternal.class

        TaskBuilder withInputFiles(File... inputFiles) {
            return withInputFiles(default: Arrays.asList(inputFiles))
        }

        TaskBuilder withInputFiles(Map<String, ? extends Collection<? extends File>> files) {
            this.inputs = files
            return this
        }

        TaskBuilder withOutputFiles(File... outputFiles) {
            return withOutputFiles(default: Arrays.asList(outputFiles))
        }

        TaskBuilder withOutputFiles(Map<String, ? extends Collection<? extends File>> files) {
            this.outputs = files
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

        TaskBuilder withProperty(String name, Object value) {
            inputProperties.put(name, value)
            return this
        }

        TaskInternal task() {
            final TaskInternal task = TestUtil.createTask(type, project, path)
            if (inputs != null) {
                inputs.each { String property, Collection<? extends File> files ->
                    task.inputs.files files withPropertyName property
                }
            }
            if (inputProperties != null) {
                task.getInputs().properties(inputProperties)
            }
            if (outputs != null) {
                outputs.each { String property, Collection<? extends File> files ->
                    if (files.size() == 1) {
                        task.getOutputs().file files[0] withPropertyName property
                    } else if (files.size() > 1) {
                        for (int idx = 0; idx < files.size(); idx++) {
                            def file = files[idx]
                            task.getOutputs().file file withPropertyName "$property.\$$idx"
                        }
                    }
                }
            }
            task.doLast(new Action<Object>() {
                void execute(Object o) {
                    for (TestFile file : create) {
                        file.createFile()
                    }
                }
            })

            return task
        }
    }

    static class TaskSubType extends DefaultTask {
    }

}
