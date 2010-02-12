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

import org.gradle.api.DefaultTask;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.util.HelperUtil;
import org.gradle.util.TemporaryFolder;
import org.gradle.util.TestFile;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Ignore;
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
    private final TestFile missingInputFile = tmpDir.file("missing-input-file");
    private final Set<TestFile> inputFiles = toSet(inputFile, inputDir, missingInputFile);
    private final Set<TestFile> outputFiles = toSet(outputFile, outputDir, emptyOutputDir, missingOutputFile);
    private final Set<TestFile> createFiles = toSet(outputFile, outputDirFile, outputDirFile2);
    private final FileSnapshotter fileSnapshotter = new DefaultFileSnapshotter(new DefaultHasher());
    private PersistentCache persistentCache;
    private final DefaultTaskArtifactStateRepository repository = new DefaultTaskArtifactStateRepository(cacheRepository,
            fileSnapshotter);

    @Test
    public void artifactsAreNotUpToDateWhenCacheIsEmpty() {
        expectEmptyCacheLocated();

        TaskArtifactState state = repository.getStateFor(task());
        assertNotNull(state);
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyOutputFileDoesNotExist() {
        execute();

        outputFile.delete();

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyOutputDirFileDoesNotExist() {
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
    public void artifactsAreNotUpToDateWhenAnyOutputDirFileHasChangedType() {
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
    public void artifactsAreNotUpToDateWhenAnyOutputDirFileHasChangedHash() {
        execute();

        outputDirFile.write("new content");

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyOutputFilesAdded() {
        execute();

        TaskInternal task = builder().withOutputFiles(outputFile, outputDir, tmpDir.createFile("output-file-2")).task();

        TaskArtifactState state = repository.getStateFor(task);
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyOutputFilesRemoved() {
        execute();

        TaskInternal task = builder().withOutputFiles(outputFile).task();

        TaskArtifactState state = repository.getStateFor(task);
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenTaskWithDifferentPathGeneratedAnyOutputFiles() {
        TaskInternal task = builder().withPath("other").withOutputFiles(outputFile, tmpDir.createFile("other-output")).task();

        execute(task, task());

        TaskArtifactState state = repository.getStateFor(task);
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenTaskWithDifferentTypeGeneratedAnyOutputFiles() {
        TaskInternal task = builder().withType(TaskSubType.class).withOutputFiles(outputFile, tmpDir.createFile("other-output")).task();

        execute(task, task());

        TaskArtifactState state = repository.getStateFor(task);
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyInputFilesAdded() {
        execute();

        TaskInternal task = builder().withInputFiles(inputFile, inputDir, tmpDir.createFile("other-input")).task();
        TaskArtifactState state = repository.getStateFor(task);
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyInputFilesRemoved() {
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
    public void artifactsAreNotUpToDateWhenStateHasBeenInvalidated() {
        System.out.println("----------------------------------------");
        execute();

        TaskArtifactState state = repository.getStateFor(task());
        assertTrue(state.isUpToDate());

        state.invalidate();

        state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
        System.out.println("----------------------------------------");
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
    public void multipleTasksCanProduceTheSameOutputDirectory() {
        TaskInternal task1 = task();
        TaskInternal task2 = builder().withPath("other").withOutputFiles(outputDir).task();
        execute(task1, task2);

        TaskArtifactState state = repository.getStateFor(task1);
        assertTrue(state.isUpToDate());

        state = repository.getStateFor(task2);
        assertTrue(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenOutputDirHasBeenDeletedAndRecreatedBySomeOtherTask() {
        TaskInternal task1 = task();
        TaskInternal task2 = builder().withPath("other").withOutputFiles(outputDir).task();
        execute(task1, task2);

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
        execute(task);

        TaskArtifactState state = repository.getStateFor(task);
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
    public void artifactsAreNotUpToDateWhenTaskHasNoOutputs() {
        TaskInternal task = builder().withOutputFiles().task();
        execute(task);

        TaskArtifactState state = repository.getStateFor(task);
        assertFalse(state.isUpToDate());
    }

    @Test @Ignore
    public void ignoresExistingFilesInOutputDirectory() {
        fail();
    }
    
    private void execute() {
        execute(task());
    }

    private void execute(TaskInternal... tasks) {
        expectEmptyCacheLocated();
        for (TaskInternal task : tasks) {
            TaskArtifactState state = repository.getStateFor(task);
            task.execute();
            state.update();
        }
    }
    
    private void expectEmptyCacheLocated() {
        context.checking(new Expectations(){{
            persistentCache = context.mock(PersistentCache.class);
            one(cacheRepository).getCacheFor(gradle, "taskArtifacts");
            will(returnValue(persistentCache));
            one(persistentCache).openIndexedCache();
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
            task.getInputs().properties(inputProperties);
            task.getOutputs().files(outputs);
            task.doLast(new org.gradle.api.Action<Object>() {
                public void execute(Object o) {
                    for (TestFile file : create) {
                        file.touch();
                    }
                }
            });

            return task;
        }
    }

    public static class TaskSubType extends DefaultTask {
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
