/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.changedetection;

import org.gradle.CacheUsage;
import org.gradle.api.DefaultTask;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.internal.DefaultCacheRepository;
import org.gradle.internal.id.RandomLongIdGenerator;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.gradle.testfixtures.internal.InMemoryCacheFactory;
import org.gradle.util.HelperUtil;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.util.*;

import static org.gradle.util.Matchers.isEmpty;
import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

// TODO:DAZ Add test cases here
public class DefaultTaskArtifactStateRepositoryTest {
    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();
    private final ProjectInternal project = HelperUtil.createRootProject();
    private final Gradle gradle = project.getGradle();
    private final TestFile outputFile = tmpDir.file("output-file");
    private final TestFile outputDir = tmpDir.file("output-dir");
    private final TestFile outputDirFile = outputDir.file("some-file");
    private final TestFile outputDirFile2 = outputDir.file("some-file-2");
    private final TestFile emptyOutputDir = tmpDir.file("empty-output-dir");
    private final TestFile missingOutputFile = tmpDir.file("missing-output-file");
    private final TestFile inputFile = tmpDir.createFile("input-file");
    private final TestFile inputDir = tmpDir.createDir("input-dir");
    private final TestFile inputDirFile = inputDir.file("input-file2").createFile();
    private final TestFile missingInputFile = tmpDir.file("missing-input-file");
    private final Set<TestFile> inputFiles = toSet(inputFile, inputDir, missingInputFile);
    private final Set<TestFile> outputFiles = toSet(outputFile, outputDir, emptyOutputDir, missingOutputFile);
    private final Set<TestFile> createFiles = toSet(outputFile, outputDirFile, outputDirFile2);
    private DefaultTaskArtifactStateRepository repository;

    @Before
    public void setup() {
        CacheRepository cacheRepository = new DefaultCacheRepository(tmpDir.createDir("user-home"), null, CacheUsage.ON, new InMemoryCacheFactory());
        TaskArtifactStateCacheAccess cacheAccess = new DefaultTaskArtifactStateCacheAccess(gradle, cacheRepository);
        FileSnapshotter inputFilesSnapshotter = new DefaultFileSnapshotter(new DefaultHasher());
        FileSnapshotter outputFilesSnapshotter = new OutputFilesSnapshotter(inputFilesSnapshotter, new RandomLongIdGenerator(), cacheAccess);
        TaskHistoryRepository taskHistoryRepository = new CacheBackedTaskHistoryRepository(cacheAccess, new CacheBackedFileSnapshotRepository(cacheAccess));
        repository = new DefaultTaskArtifactStateRepository(taskHistoryRepository, outputFilesSnapshotter, inputFilesSnapshotter);
    }

    @Test
    public void artifactsAreNotUpToDateWhenCacheIsEmpty() {
        TaskArtifactState state = repository.getStateFor(task());
        assertNotNull(state);
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyOutputFileNoLongerExists() {
        execute();

        outputFile.delete();

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyFileInOutputDirNoLongerExists() {
        execute();

        outputDirFile.delete();

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyOutputFileHasChangedType() {
        execute();

        outputFile.delete();
        outputFile.createDir();

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyFileInOutputDirHasChangedType() {
        execute();

        outputDirFile.delete();
        outputDirFile.createDir();

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyOutputFileHasChangedHash() {
        execute();

        outputFile.write("new content");

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyFileInOutputDirHasChangedHash() {
        execute();

        outputDirFile.write("new content");

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyOutputFilesAddedToSet() {
        execute();

        TaskInternal task = builder().withOutputFiles(outputFile, outputDir, tmpDir.createFile("output-file-2"), emptyOutputDir, missingOutputFile).task();

        TaskArtifactState state = repository.getStateFor(task);
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyOutputFilesRemovedFromSet() {
        execute();

        TaskInternal task = builder().withOutputFiles(outputFile, emptyOutputDir, missingOutputFile).task();

        TaskArtifactState state = repository.getStateFor(task);
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenTaskWithDifferentTypeGeneratedAnyOutputFiles() {
        TaskInternal task1 = builder().withOutputFiles(outputFile).task();
        TaskInternal task2 = builder().withType(TaskSubType.class).withOutputFiles(outputFile).task();

        execute(task1, task2);

        TaskArtifactState state = repository.getStateFor(task1);
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyInputFilesAddedToSet() {
        execute();

        TaskInternal task = builder().withInputFiles(inputFile, inputDir, tmpDir.createFile("other-input"), missingInputFile).task();
        TaskArtifactState state = repository.getStateFor(task);
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyInputFilesRemovedFromSet() {
        execute();

        TaskInternal task = builder().withInputFiles(inputFile).task();
        TaskArtifactState state = repository.getStateFor(task);
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyInputFileHasChangedHash() {
        execute();

        inputFile.write("some new content");

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyInputFileHasChangedType() {
        execute();

        inputFile.delete();
        inputFile.createDir();

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyInputFileNoLongerExists() {
        execute();

        inputFile.delete();

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyFileCreatedInInputDir() {
        execute();

        inputDir.file("other-file").createFile();

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyFileDeletedFromInputDir() {
        execute();

        inputDirFile.delete();

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyFileInInputDirChangesHash() {
        execute();

        inputDirFile.writelns("new content");

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyFileInInputDirChangesType() {
        execute();

        inputDirFile.delete();
        inputDirFile.mkdir();

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyInputPropertyValueChanged() {
        execute();

        TaskArtifactState state = repository.getStateFor(builder().withProperty("prop", "new value").task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void inputPropertyValueCanBeNull() {
        TaskInternal task = builder().withProperty("prop", null).task();
        execute(task);

        TaskArtifactState state = repository.getStateFor(task);
        assertTrue(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyInputPropertyAdded() {
        execute();

        TaskArtifactState state = repository.getStateFor(builder().withProperty("prop2", "value").task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyInputPropertyRemoved() {
        execute(builder().withProperty("prop2", "value").task());

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenStateHasNotBeenUpdated() {
        repository.getStateFor(task());

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenOutputDirWhichUsedToExistHasBeenDeleted() {
        // Output dir already exists before first execution of task
        outputDirFile.createFile();

        TaskInternal task1 = builder().withOutputFiles(outputDir).createsFiles(outputDirFile).task();
        TaskInternal task2 = builder().withPath("other").withOutputFiles(outputDir).createsFiles(outputDirFile2).task();

        TaskArtifactState state = repository.getStateFor(task1);
        assertFalse(state.isUpToDate());
        state.afterTask();

        outputDir.deleteDir();

        // Another task creates dir
        state = repository.getStateFor(task2);
        assertFalse(state.isUpToDate());
        task2.execute();
        state.afterTask();

        // Task should be out-of-date
        state = repository.getStateFor(task1);
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreUpToDateWhenNothingHasChangedSinceOutputFilesWereGenerated() {
        execute();

        TaskArtifactState state = repository.getStateFor(task());
        assertTrue(state.isUpToDate());

        state = repository.getStateFor(task());
        assertTrue(state.isUpToDate());
    }

    @Test
    public void artifactsAreUpToDateWhenOutputFileWhichDidNotExistNowExists() {
        execute();

        missingOutputFile.touch();

        TaskArtifactState state = repository.getStateFor(task());
        assertTrue(state.isUpToDate());
    }

    @Test
    public void artifactsAreUpToDateWhenOutputDirWhichWasEmptyIsNoLongerEmpty() {
        execute();

        emptyOutputDir.file("some-file").touch();

        TaskArtifactState state = repository.getStateFor(task());
        assertTrue(state.isUpToDate());
    }

    @Test
    public void hasEmptyTaskHistoryWhenTaskHasNeverBeenExecuted() {
        TaskArtifactState state = repository.getStateFor(task());
        assertThat(state.getExecutionHistory().getOutputFiles().getFiles(), isEmpty());
    }

    @Test
    public void hasTaskHistoryFromPreviousExecution() {
        execute();

        TaskArtifactState state = repository.getStateFor(task());
        assertThat(state.getExecutionHistory().getOutputFiles().getFiles(), equalTo(toLinkedSet((File) outputFile, outputDirFile, outputDirFile2)));
    }

    @Test
    public void multipleTasksCanProduceFilesIntoTheSameOutputDirectory() {
        TaskInternal task1 = task();
        TaskInternal task2 = builder().withPath("other").withOutputFiles(outputDir).createsFiles(outputDir.file("output2")).task();
        execute(task1, task2);

        TaskArtifactState state = repository.getStateFor(task1);
        assertTrue(state.isUpToDate());

        state = repository.getStateFor(task2);
        assertTrue(state.isUpToDate());
    }

    @Test
    public void multipleTasksCanProduceTheSameFileWithTheSameContents() {
        TaskInternal task1 = builder().withOutputFiles(outputFile).task();
        TaskInternal task2 = builder().withPath("other").withOutputFiles(outputFile).task();
        execute(task1, task2);

        TaskArtifactState state = repository.getStateFor(task1);
        assertTrue(state.isUpToDate());

        state = repository.getStateFor(task2);
        assertTrue(state.isUpToDate());
    }

    @Test
    public void multipleTasksCanProduceTheSameEmptyDir() {
        TaskInternal task1 = task();
        TaskInternal task2 = builder().withPath("other").withOutputFiles(outputDir).task();
        execute(task1, task2);

        TaskArtifactState state = repository.getStateFor(task1);
        assertTrue(state.isUpToDate());

        state = repository.getStateFor(task2);
        assertTrue(state.isUpToDate());
    }

    @Test
    public void doesNotConsiderExistingFilesInOutputDirectoryAsProducedByTask() {
        TestFile otherFile = outputDir.file("other").createFile();

        execute();

        otherFile.delete();

        TaskArtifactState state = repository.getStateFor(task());
        assertTrue(state.isUpToDate());
        assertThat(state.getExecutionHistory().getOutputFiles().getFiles(), (Matcher) not(hasItem(otherFile)));
    }

    @Test
    public void considersExistingFileInOutputDirectoryWhichIsUpdatedByTheTaskAsProducedByTask() {
        TestFile otherFile = outputDir.file("other").createFile();

        TaskInternal task = task();
        TaskArtifactState state = repository.getStateFor(task);
        assertFalse(state.isUpToDate());

        task.execute();
        otherFile.write("new content");

        state.afterTask();

        otherFile.delete();

        state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
        assertThat(state.getExecutionHistory().getOutputFiles().getFiles(), hasItem((File) otherFile));
    }

    @Test
    public void fileIsNoLongerConsideredProducedByTaskOnceItIsDeleted() {
        execute();

        outputDirFile.delete();

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
        state.afterTask();

        outputDirFile.write("ignore me");

        state = repository.getStateFor(task());
        assertTrue(state.isUpToDate());
        assertThat(state.getExecutionHistory().getOutputFiles().getFiles(), not(hasItem((File) outputDirFile)));
        state.afterTask();
    }

    @Test
    public void artifactsAreUpToDateWhenTaskDoesNotAcceptAnyInputs() {
        TaskInternal task = builder().doesNotAcceptInput().task();
        execute(task);

        TaskArtifactState state = repository.getStateFor(task);
        assertTrue(state.isUpToDate());

        outputDirFile.delete();

        state = repository.getStateFor(task);
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreUpToDateWhenTaskHasNoInputFiles() {
        TaskInternal task = builder().withInputFiles().task();
        execute(task);

        TaskArtifactState state = repository.getStateFor(task);
        assertTrue(state.isUpToDate());
    }

    @Test
    public void artifactsAreUpToDateWhenTaskHasNoOutputs() {
        TaskInternal task = builder().withOutputFiles().task();
        execute(task);

        TaskArtifactState state = repository.getStateFor(task);
        assertTrue(state.isUpToDate());
    }

    @Test
    public void taskCanProduceIntoDifferentSetsOfOutputFiles() {
        TestFile outputDir2 = tmpDir.createDir("output-dir-2");
        TestFile outputDirFile2 = outputDir2.file("output-file-2");
        TaskInternal instance1 = builder().withOutputFiles(outputDir).createsFiles(outputDirFile).task();
        TaskInternal instance2 = builder().withOutputFiles(outputDir2).createsFiles(outputDirFile2).task();

        execute(instance1, instance2);

        TaskArtifactState state = repository.getStateFor(instance1);
        assertTrue(state.isUpToDate());
        assertThat(state.getExecutionHistory().getOutputFiles().getFiles(), equalTo(toLinkedSet((File) outputDirFile)));

        state = repository.getStateFor(instance2);
        assertTrue(state.isUpToDate());
        assertThat(state.getExecutionHistory().getOutputFiles().getFiles(), equalTo(toLinkedSet((File) outputDirFile2)));
    }

    private void execute() {
        execute(task());
    }

    private void execute(TaskInternal... tasks) {
        for (TaskInternal task : tasks) {
            TaskArtifactState state = repository.getStateFor(task);
            state.isUpToDate();
            task.execute();
            state.afterTask();
        }
    }

    private TaskInternal task() {
        return builder().task();
    }

    private TaskBuilder builder() {
        return new TaskBuilder();
    }

    private class TaskBuilder {
        private String path = "task";
        private Collection<? extends File> inputs = inputFiles;
        private Collection<? extends File> outputs = outputFiles;
        private Collection<? extends TestFile> create = createFiles;
        private Class<? extends TaskInternal> type = TaskInternal.class;
        private Map<String, Object> inputProperties = new HashMap<String, Object>(toMap("prop", "value"));

        TaskBuilder withInputFiles(File... inputFiles) {
            inputs = Arrays.asList(inputFiles);
            return this;
        }

        TaskBuilder withOutputFiles(File... outputFiles) {
            outputs = Arrays.asList(outputFiles);
            return this;
        }

        TaskBuilder createsFiles(TestFile... outputFiles) {
            create = Arrays.asList(outputFiles);
            return this;
        }

        TaskBuilder withPath(String path) {
            this.path = path;
            return this;
        }

        TaskBuilder withType(Class<? extends TaskInternal> type) {
            this.type = type;
            return this;
        }

        TaskBuilder doesNotAcceptInput() {
            inputs = null;
            inputProperties = null;
            return this;
        }

        public TaskBuilder withProperty(String name, Object value) {
            inputProperties.put(name, value);
            return this;
        }

        TaskInternal task() {
            final TaskInternal task = HelperUtil.createTask(type, project, path);
            if (inputs != null) {
                task.getInputs().files(inputs);
            }
            if (inputProperties != null) {
                task.getInputs().properties(inputProperties);
            }
            if (outputs != null) {
                task.getOutputs().files(outputs);
            }
            task.doLast(new org.gradle.api.Action<Object>() {
                public void execute(Object o) {
                    for (TestFile file : create) {
                        file.createFile();
                    }
                }
            });

            return task;
        }
    }

    public static class TaskSubType extends DefaultTask {
    }

}
