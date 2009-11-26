/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.tasks.TaskInputs;
import org.gradle.api.tasks.TaskOutputs;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.integtests.TestFile;
import org.gradle.util.TemporaryFolder;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.*;

import static java.util.Collections.*;
import static org.gradle.util.WrapUtil.*;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class DefaultTaskArtifactStateRepositoryTest {
    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final CacheRepository cacheRepository = context.mock(CacheRepository.class);
    private final Gradle gradle = context.mock(Gradle.class);
    private final Project project = context.mock(Project.class);
    private final TestFile outputFile = tmpDir.file("output-file");
    private final TestFile outputDir = tmpDir.dir("output-dir");
    private final TestFile emptyOutputDir = tmpDir.dir("empty-output-dir");
    private final TestFile missingOutputFile = tmpDir.getDir().file("missing-output-file");
    private final TestFile inputFile = tmpDir.file("input-file");
    private final TestFile inputDir = tmpDir.dir("input-dir");
    private final TestFile missingInputFile = tmpDir.getDir().file("missing-input-file");
    private final Set<TestFile> inputFiles = toSet(inputFile, inputDir, missingInputFile);
    private final Set<TestFile> outputFiles = toSet(outputFile, outputDir, emptyOutputDir, missingOutputFile);
    private final Hasher hasher = new DefaultHasher();
    private int counter;
    private final DefaultTaskArtifactStateRepository repository = new DefaultTaskArtifactStateRepository(cacheRepository,
            hasher);

    @Before
    public void setup() {
        outputDir.file("some-file").touch();

        context.checking(new Expectations() {{
            allowing(project).getGradle();
            will(returnValue(gradle));
        }});
    }

    @Test
    public void artifactsAreNotUpToDateWhenCacheIsEmpty() {
        expectEmptyCacheLocated();

        TaskArtifactState state = repository.getStateFor(task());
        assertNotNull(state);
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyOutputFileDoesNotExist() {
        writeTaskState();

        outputFile.delete();

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyNonEmptyOutputDirIsNowEmpty() {
        writeTaskState();

        outputDir.deleteDir().createDir();

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyOutputFileHasChangedType() {
        writeTaskState();

        outputFile.delete();
        outputFile.createDir();

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyOutputFilesAdded() {
        writeTaskState();

        TaskInternal task = builder().withOutputFiles(outputFile, outputDir, tmpDir.file("output-file-2")).task();

        TaskArtifactState state = repository.getStateFor(task);
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyOutputFilesRemoved() {
        writeTaskState();

        TaskInternal task = builder().withOutputFiles(outputFile).task();

        TaskArtifactState state = repository.getStateFor(task);
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenTaskWithDifferentPathGeneratedAnyOutputFiles() {
        TaskInternal task = builder().withPath("other").withOutputFiles(outputFile, tmpDir.file("other-output")).task();

        writeTaskState(task, task());

        TaskArtifactState state = repository.getStateFor(task);
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenTaskWithDifferentTypeGeneratedAnyOutputFiles() {
        TaskInternal task = builder().withType(TaskSubType.class).withOutputFiles(outputFile, tmpDir.file("other-output")).task();

        writeTaskState(task, task());

        TaskArtifactState state = repository.getStateFor(task);
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyInputFilesAdded() {
        writeTaskState();

        TaskInternal task = builder().withInputFiles(inputFile, inputDir, tmpDir.file("other-input")).task();
        TaskArtifactState state = repository.getStateFor(task);
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyInputFilesRemoved() {
        writeTaskState();

        TaskInternal task = builder().withInputFiles(inputFile).task();
        TaskArtifactState state = repository.getStateFor(task);
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyInputFileHasChangedHash() {
        writeTaskState();

        inputFile.write("some new content");

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyInputFileHasChangedType() {
        writeTaskState();

        inputFile.delete();
        inputFile.createDir();

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyInputFileNoLongerExists() {
        writeTaskState();

        inputFile.delete();

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenStateHasBeenInvalidated() {
        writeTaskState();

        TaskArtifactState state = repository.getStateFor(task());
        assertTrue(state.isUpToDate());

        state.invalidate();

        state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenStateWasNotUpdated() {
        expectEmptyCacheLocated();
        repository.getStateFor(task());

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreUpToDateWhenNothingHasChangedSinceOutputFilesWereGenerated() {
        writeTaskState();

        TaskArtifactState state = repository.getStateFor(task());
        assertTrue(state.isUpToDate());

        state = repository.getStateFor(task());
        assertTrue(state.isUpToDate());
    }

    @Test
    public void artifactsAreUpToDateWhenOutputFileWhichDidNotExistNowExists() {
        writeTaskState();

        missingOutputFile.touch();

        TaskArtifactState state = repository.getStateFor(task());
        assertTrue(state.isUpToDate());
    }

    @Test
    public void artifactsAreUpToDateWhenOutputFileWhichWasEmptyIsNoLongerEmpty() {
        writeTaskState();

        emptyOutputDir.file("some-file").touch();

        TaskArtifactState state = repository.getStateFor(task());
        assertTrue(state.isUpToDate());
    }

    @Test
    public void multipleTasksCanProduceTheSameOutputDirectory() {
        TaskInternal task1 = task();
        TaskInternal task2 = builder().withPath("other").withOutputFiles(outputDir).task();
        writeTaskState(task1, task2);

        TaskArtifactState state = repository.getStateFor(task1);
        assertTrue(state.isUpToDate());

        state = repository.getStateFor(task2);
        assertTrue(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenOutputDirHasBeenDeletedAndRecreatedBySomeOtherTask() {
        TaskInternal task1 = task();
        TaskInternal task2 = builder().withPath("other").withOutputFiles(outputDir).task();
        writeTaskState(task1, task2);

        outputDir.deleteDir();

        TaskArtifactState state = repository.getStateFor(task2);
        assertFalse(state.isUpToDate());

        outputDir.createDir().file("some-file").touch();
        state.update();
        
        state = repository.getStateFor(task2);
        assertTrue(state.isUpToDate());

        state = repository.getStateFor(task1);
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenTaskDoesNotAcceptAnyInputs() {
        TaskInternal task = builder().doesNotAcceptInput().task();
        writeTaskState(task);

        TaskArtifactState state = repository.getStateFor(task);
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreUpToDateWhenTaskHasNoInputFiles() {
        TaskInternal task = builder().withInputFiles().task();
        writeTaskState(task);

        TaskArtifactState state = repository.getStateFor(task);
        assertTrue(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenTaskHasNoOutputs() {
        TaskInternal task = builder().withOutputFiles().task();
        writeTaskState(task);

        TaskArtifactState state = repository.getStateFor(task);
        assertFalse(state.isUpToDate());
    }

    private void writeTaskState() {
        writeTaskState(task());
    }

    private void writeTaskState(TaskInternal... tasks) {
        expectEmptyCacheLocated();
        for (TaskInternal task : tasks) {
            repository.getStateFor(task).update();
        }
    }
    
    private void expectEmptyCacheLocated() {
        context.checking(new Expectations(){{
            one(cacheRepository).getIndexedCacheFor(gradle, "taskArtifacts", EMPTY_MAP);
            will(returnValue(new TestIndexedCache()));
        }});
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
        private Class<? extends TaskInternal> type = TaskInternal.class;

        TaskBuilder withInputFiles(File... inputFiles) {
            inputs = Arrays.asList(inputFiles);
            return this;
        }

        TaskBuilder withOutputFiles(File... outputFiles) {
            outputs = Arrays.asList(outputFiles);
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
            return this;
        }

        TaskInternal task() {
            final TaskInternal task = context.mock(type, String.format("task%d", counter++));
            context.checking(new Expectations(){{
                TaskInputs taskInputs = context.mock(TaskInputs.class, String.format("inputs%d", counter++));
                TaskOutputs taskOutputs = context.mock(TaskOutputs.class, String.format("outputs%d", counter++));
                FileCollection outputFileCollection = context.mock(FileCollection.class, String.format("taskOutputFiles%d", counter++));
                FileCollection inputFileCollection = context.mock(FileCollection.class, String.format(
                        "taskInputFiles%d", counter++));

                allowing(task).getProject();
                will(returnValue(project));
                allowing(task).getPath();
                will(returnValue(path));
                allowing(task).getInputs();
                will(returnValue(taskInputs));
                allowing(taskInputs).getHasInputFiles();
                will(returnValue(inputs != null));
                allowing(taskInputs).getFiles();
                will(returnValue(inputFileCollection));
                allowing(inputFileCollection).iterator();
                will(returnIterator(inputs == null ? emptySet() : inputs));
                allowing(task).getOutputs();
                will(returnValue(taskOutputs));
                allowing(taskOutputs).getFiles();
                will(returnValue(outputFileCollection));
                allowing(outputFileCollection).iterator();
                will(returnIterator(outputs));
            }});
            return task;
        }
    }

    public interface TaskSubType extends TaskInternal {
    }

    public static class TestIndexedCache implements PersistentIndexedCache<File, Object> {
        Map<File, Object> entries = new HashMap<File, Object>();

        public Object get(File key) {
            return entries.get(key);
        }

        public void put(File key, Object value) {
            entries.put(key, value);
        }

        public void remove(File key) {
            entries.remove(key);
        }
    }
}
