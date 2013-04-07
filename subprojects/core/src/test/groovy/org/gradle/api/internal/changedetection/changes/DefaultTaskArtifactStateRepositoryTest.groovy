/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
import org.gradle.CacheUsage
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.changedetection.TaskArtifactState
import org.gradle.api.internal.changedetection.state.*
import org.gradle.api.internal.tasks.IncrementalTaskAction
import org.gradle.cache.CacheRepository
import org.gradle.cache.internal.DefaultCacheRepository
import org.gradle.internal.id.RandomLongIdGenerator
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.InMemoryCacheFactory
import org.gradle.util.HelperUtil
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.util.WrapUtil.toMap
import static org.gradle.util.WrapUtil.toSet
// TODO:DAZ Add test cases here
public class DefaultTaskArtifactStateRepositoryTest extends Specification {
    
    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final project = HelperUtil.createRootProject()
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
    DefaultTaskArtifactStateRepository repository


    def setup() {
        CacheRepository cacheRepository = new DefaultCacheRepository(tmpDir.createDir("user-home"), null, CacheUsage.ON, new InMemoryCacheFactory())
        TaskArtifactStateCacheAccess cacheAccess = new DefaultTaskArtifactStateCacheAccess(gradle, cacheRepository)
        FileSnapshotter inputFilesSnapshotter = new DefaultFileSnapshotter(new DefaultHasher())
        FileSnapshotter outputFilesSnapshotter = new OutputFilesSnapshotter(inputFilesSnapshotter, new RandomLongIdGenerator(), cacheAccess)
        TaskHistoryRepository taskHistoryRepository = new CacheBackedTaskHistoryRepository(cacheAccess, new CacheBackedFileSnapshotRepository(cacheAccess))
        repository = new DefaultTaskArtifactStateRepository(taskHistoryRepository, outputFilesSnapshotter, inputFilesSnapshotter)
    }

    def artifactsAreNotUpToDateWhenCacheIsEmpty() {
        expect:
        !repository.getStateFor(task).upToDate
    }

    def artifactsAreNotUpToDateWhenAnyOutputFileNoLongerExists() {
        given:
        execute(task)

        when:
        outputFile.delete()

        then:
        !repository.getStateFor(task).upToDate
    }

    def artifactsAreNotUpToDateWhenAnyFileInOutputDirNoLongerExists() {
        given:
        execute(task)

        when:
        outputDirFile.delete()

        then:
        !repository.getStateFor(task).upToDate
    }

    def artifactsAreNotUpToDateWhenAnyOutputFileHasChangedType() {
        given:
        execute(task)

        when:
        outputFile.delete()
        outputFile.createDir()

        then:
        !repository.getStateFor(task).upToDate
    }

    def artifactsAreNotUpToDateWhenAnyFileInOutputDirHasChangedType() {
        given:
        execute(task)

        when:
        outputDirFile.delete()
        outputDirFile.createDir()

        then:
        !repository.getStateFor(task).upToDate
    }

    def artifactsAreNotUpToDateWhenAnyOutputFileHasChangedHash() {
        given:
        execute(task)
        
        when:
        outputFile.write("new content")

        then:
        !repository.getStateFor(task).upToDate
    }

    def artifactsAreNotUpToDateWhenAnyFileInOutputDirHasChangedHash() {
        given:
        execute(task)
        
        when:
        outputDirFile.write("new content")

        then:
        !repository.getStateFor(task).upToDate
    }

    def artifactsAreNotUpToDateWhenAnyOutputFilesAddedToSet() {
        when:
        execute(task)
        
        then:
        TaskInternal outputFilesAddedTask = builder.withOutputFiles(outputFile, outputDir, tmpDir.createFile("output-file-2"), emptyOutputDir, missingOutputFile).task()
        !repository.getStateFor(outputFilesAddedTask).upToDate
    }

    def artifactsAreNotUpToDateWhenAnyOutputFilesRemovedFromSet() {
        when:
        execute(task)

        then:
        TaskInternal outputFilesRemovedTask = builder.withOutputFiles(outputFile, emptyOutputDir, missingOutputFile).task()
        !repository.getStateFor(outputFilesRemovedTask).upToDate
    }

    def artifactsAreNotUpToDateWhenTaskWithDifferentTypeGeneratedAnyOutputFiles() {
        when:
        TaskInternal task1 = builder.withOutputFiles(outputFile).task()
        TaskInternal task2 = builder.withType(TaskSubType.class).withOutputFiles(outputFile).task()
        execute(task1, task2)

        then:
        !repository.getStateFor(task1).upToDate
    }

    def artifactsAreNotUpToDateWhenAnyInputFilesAddedToSet() {
        when:
        execute(task)

        then:
        TaskInternal inputFilesAdded = builder.withInputFiles(inputFile, inputDir, tmpDir.createFile("other-input"), missingInputFile).task()
        !repository.getStateFor(inputFilesAdded).upToDate

    }

    def artifactsAreNotUpToDateWhenAnyInputFilesRemovedFromSet() {
        when:
        execute(task)

        then:
        TaskInternal inputFilesRemoved = builder.withInputFiles(inputFile).task()
        !repository.getStateFor(inputFilesRemoved).upToDate
    }

    def artifactsAreNotUpToDateWhenAnyInputFileHasChangedHash() {
        given:
        execute(task)

        when:
        inputFile.write("some new content")

        then:
        !repository.getStateFor(task).upToDate
    }

    def artifactsAreNotUpToDateWhenAnyInputFileHasChangedType() {
        given:
        execute(task)

        when:
        inputFile.delete()
        inputFile.createDir()

        then:
        !repository.getStateFor(task).upToDate
    }

    def artifactsAreNotUpToDateWhenAnyInputFileNoLongerExists() {
        given:
        execute(task)

        when:
        inputFile.delete()

        then:
        !repository.getStateFor(task).upToDate
    }

    def artifactsAreNotUpToDateWhenAnyFileCreatedInInputDir() {
        given:
        execute(task)

        when:
        inputDir.file("other-file").createFile()

        then:
        !repository.getStateFor(task).upToDate
    }

    def artifactsAreNotUpToDateWhenAnyFileDeletedFromInputDir() {
        given:
        execute(task)

        when:
        inputDirFile.delete()

        then:
        !repository.getStateFor(task).upToDate
    }

    def artifactsAreNotUpToDateWhenAnyFileInInputDirChangesHash() {
        given:
        execute(task)

        when:
        inputDirFile.writelns("new content")

        then:
        !repository.getStateFor(task).upToDate
    }

    def artifactsAreNotUpToDateWhenAnyFileInInputDirChangesType() {
        given:
        execute(task)

        when:
        inputDirFile.delete()
        inputDirFile.mkdir()

        then:
        !repository.getStateFor(task).upToDate
    }

    def artifactsAreNotUpToDateWhenAnyInputPropertyValueChanged() {
        when:
        execute(builder.withProperty("prop", "original value").task())

        then:
        final inputPropertiesTask = builder.withProperty("prop", "new value").task()
        !repository.getStateFor(inputPropertiesTask).upToDate
    }

    def inputPropertyValueCanBeNull() {
        when:
        TaskInternal task = builder.withProperty("prop", null).task()
        execute(task)

        then:
        repository.getStateFor(task).upToDate
    }

    def artifactsAreNotUpToDateWhenAnyInputPropertyAdded() {
        when:
        execute(task)

        then:
        final addedPropertyTask = builder.withProperty("prop2", "value").task()
        !repository.getStateFor(addedPropertyTask).upToDate
    }

    def artifactsAreNotUpToDateWhenAnyInputPropertyRemoved() {
        given:
        execute(builder.withProperty("prop2", "value").task())

        expect:
        !repository.getStateFor(task).upToDate
    }

    def artifactsAreNotUpToDateWhenStateHasNotBeenUpdated() {
        when:
        repository.getStateFor(task)

        then:
        !repository.getStateFor(task).upToDate
    }

    def artifactsAreNotUpToDateWhenOutputDirWhichUsedToExistHasBeenDeleted() {
        given:
        // Output dir already exists before first execution of task
        outputDirFile.createFile()

        TaskInternal task1 = builder.withOutputFiles(outputDir).createsFiles(outputDirFile).task()
        TaskInternal task2 = builder.withPath("other").withOutputFiles(outputDir).createsFiles(outputDirFile2).task()
        
        when:
        TaskArtifactState state = repository.getStateFor(task1)
        state.afterTask()
        
        then:
        !state.upToDate
        
        when:
        outputDir.deleteDir()

        and:
        // Another task creates dir
        state = repository.getStateFor(task2)

        then:
        !state.upToDate
        
        when:
        task2.execute()
        state.afterTask()
        
        then:
        // Task should be out-of-date
        !repository.getStateFor(task1).upToDate
    }

    def artifactsAreUpToDateWhenNothingHasChangedSinceOutputFilesWereGenerated() {
        given:
        execute(task)

        expect:
        repository.getStateFor(task).upToDate
        repository.getStateFor(task).upToDate
    }

    def artifactsAreUpToDateWhenOutputFileWhichDidNotExistNowExists() {
        given:
        execute(task)

        when:
        missingOutputFile.touch()

        then:
        repository.getStateFor(task).upToDate
    }

    def artifactsAreUpToDateWhenOutputDirWhichWasEmptyIsNoLongerEmpty() {
        given:
        execute(task)

        when:
        emptyOutputDir.file("some-file").touch()

        then:
        repository.getStateFor(task).upToDate
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
        repository.getStateFor(task1).upToDate
        repository.getStateFor(task2).upToDate
    }

    def multipleTasksCanProduceTheSameFileWithTheSameContents() {
        when:
        TaskInternal task1 = builder.withOutputFiles(outputFile).task()
        TaskInternal task2 = builder.withPath("other").withOutputFiles(outputFile).task()
        execute(task1, task2)

        then:
        repository.getStateFor(task1).upToDate
        repository.getStateFor(task2).upToDate
    }

    def multipleTasksCanProduceTheSameEmptyDir() {
        when:
        TaskInternal task1 = task
        TaskInternal task2 = builder.withPath("other").withOutputFiles(outputDir).task()
        execute(task1, task2)

        then:
        repository.getStateFor(task1).upToDate
        repository.getStateFor(task2).upToDate
    }

    def doesNotConsiderExistingFilesInOutputDirectoryAsProducedByTask() {
        when:
        TestFile otherFile = outputDir.file("other").createFile()
        execute(task)
        otherFile.delete()

        then:
        TaskArtifactState state = repository.getStateFor(task)
        state.upToDate
        !state.getExecutionHistory().getOutputFiles().getFiles().contains(otherFile)
    }

    def considersExistingFileInOutputDirectoryWhichIsUpdatedByTheTaskAsProducedByTask() {
        when:
        TestFile otherFile = outputDir.file("other").createFile()
        TaskArtifactState state = repository.getStateFor(task)

        then:
        !state.upToDate

        when:
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
        state.afterTask()

        when:
        outputDirFile.write("ignore me")

        then:
        def stateAfter = repository.getStateFor(task)
        stateAfter.upToDate
        !stateAfter.executionHistory.outputFiles.files.contains(outputDirFile)
    }

    def artifactsAreUpToDateWhenTaskDoesNotAcceptAnyInputs() {
        when:
        TaskInternal noInputsTask = builder.doesNotAcceptInput().task()
        execute(noInputsTask)

        then:
        repository.getStateFor(noInputsTask).upToDate

        when:
        outputDirFile.delete()

        then:
        !repository.getStateFor(noInputsTask).upToDate
    }

    def artifactsAreUpToDateWhenTaskHasNoInputFiles() {
        when:
        TaskInternal noInputFilesTask = builder.withInputFiles().task()
        execute(noInputFilesTask)

        then:
        repository.getStateFor(noInputFilesTask).upToDate
    }

    def artifactsAreUpToDateWhenTaskHasNoOutputFiles() {
        when:
        TaskInternal noOutputsTask = builder.withOutputFiles().task()
        execute(noOutputsTask)

        then:
        repository.getStateFor(noOutputsTask).upToDate
    }

    def artifactsAreNotUpToDateAndHistoryNotPersistedWhenTaskDoesNotDeclareOutputs() {
        when:
        TaskInternal noOutputsTask = builder.doesNotDeclareOutputs().task()
        execute(noOutputsTask)

        then:
        def state = repository.getStateFor(noOutputsTask)
        !state.upToDate
        !state.executionHistory.hasHistory()
    }

    def artifactsAreUpToDateAndHistoryPersistedWhenIncrementalTaskDoesNotDeclareOutputs() {
        when:
        TaskInternal noOutputsIncrementalTask = builder.incremental().doesNotDeclareOutputs().task()
        execute(noOutputsIncrementalTask)

        then:
        def state = repository.getStateFor(noOutputsIncrementalTask)
        state.upToDate
        state.executionHistory.hasHistory()
    }

    def artifactsAreNotUpToDateWhenTaskUpToDateSpecIsFalse() {
        when:
        task.outputs.upToDateWhen {
            false
        }
        execute(task)

        then:
        !repository.getStateFor(task).upToDate
        !repository.getStateFor(task).upToDate
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
        state1.upToDate
        state1.executionHistory.outputFiles.files == [outputDirFile] as Set

        and:
        def state2 = repository.getStateFor(task2)
        state2.upToDate
        state2.executionHistory.outputFiles.files == [outputDirFile2] as Set
    }


    private void execute(TaskInternal... tasks) {
        for (TaskInternal task : tasks) {
            TaskArtifactState state = repository.getStateFor(task)
            state.isUpToDate()
            task.execute()
            state.afterTask()
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
        boolean incremental

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

        TaskBuilder doesNotDeclareOutputs() {
            outputs = null
            return this
        }

        TaskBuilder incremental() {
            incremental = true
            return this
        }

        public TaskBuilder withProperty(String name, Object value) {
            inputProperties.put(name, value)
            return this
        }

        TaskInternal task() {
            final TaskInternal task = HelperUtil.createTask(type, project, path)
            if (inputs != null) {
                task.getInputs().files(inputs)
            }
            if (inputProperties != null) {
                task.getInputs().properties(inputProperties)
            }
            if (outputs != null) {
                task.getOutputs().files(outputs)
            }
            if (incremental) {
                task.addActionRaw(new IncrementalTaskAction() {
                    void setTaskArtifactState(TaskArtifactState taskArtifactState) {
                    }

                    void execute(Task t) {
                        for (TestFile file : create) {
                            file.createFile()
                        }
                    }
                })

            } else {
                task.doLast(new org.gradle.api.Action<Object>() {
                    public void execute(Object o) {
                        for (TestFile file : create) {
                            file.createFile()
                        }
                    }
                })
            }

            return task
        }
    }

    public static class TaskSubType extends DefaultTask {
    }

}
