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
package org.gradle.api.internal.project;

import org.gradle.api.*;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.TaskAction;
import org.gradle.util.GFileUtils;
import org.gradle.util.HelperUtil;
import static org.gradle.util.Matchers.*;
import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@RunWith(JMock.class)
public class AnnotationProcessingTaskFactoryTest {
    private final JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    private final ITaskFactory delegate = context.mock(ITaskFactory.class);
    private final Project project = context.mock(Project.class);
    private final Map args = new HashMap();
    private final File testDir = HelperUtil.makeNewTestDir();
    private final AnnotationProcessingTaskFactory factory = new AnnotationProcessingTaskFactory(delegate);

    @Test
    public void attachesAnActionToTaskForMethodMarkedWithTaskActionAnnotation() {
        final Runnable action = context.mock(Runnable.class);
        final TestTask task = new TestTask(action);

        expectTaskCreated(task);

        context.checking(new Expectations() {{
            one(action).run();
        }});
        task.execute();
    }

    private void expectTaskCreated(final Task task) {
        context.checking(new Expectations() {{
            one(delegate).createTask(project, args);
            will(returnValue(task));
        }});

        assertThat(factory.createTask(project, args), sameInstance((Object) task));
    }

    @Test
    public void doesNothingToTaskWithNoTaskActionAnnotations() {
        final TaskInternal task = new DefaultTask(HelperUtil.createRootProject(), "name");

        expectTaskCreated(task);

        assertThat(task.getActions(), isEmpty());
    }

    @Test
    public void propagatesExceptionThrownByTaskActionMethod() {
        final Runnable action = context.mock(Runnable.class);
        TestTask task = new TestTask(action);

        expectTaskCreated(task);

        final RuntimeException failure = new RuntimeException();
        context.checking(new Expectations() {{
            one(action).run();
            will(throwException(failure));
        }});

        try {
            task.getActions().get(0).execute(task);
            fail();
        } catch (RuntimeException e) {
            assertThat(e, sameInstance(failure));
        }
    }

    @Test
    public void canHaveMultipleMethodsWithTaskActionAnnotation() {
        final Runnable action = context.mock(Runnable.class);
        TaskWithMultipleMethods task = new TaskWithMultipleMethods(action);

        expectTaskCreated(task);

        context.checking(new Expectations() {{
            exactly(3).of(action).run();
        }});

        task.execute();
    }

    @Test
    public void cachesClassMetaInfo() {
        TaskWithInputFile task = new TaskWithInputFile(null);
        expectTaskCreated(task);

        TaskWithInputFile task2 = new TaskWithInputFile(null);
        expectTaskCreated(task2);

        assertThat(task.getActions().get(0), sameInstance((Action) task2.getActions().get(0)));
    }
    
    @Test
    public void failsWhenStaticMethodHasTaskActionAnnotation() {
        TaskWithStaticMethod task = new TaskWithStaticMethod();
        assertTaskCreationFails(task,
                "Cannot use @TaskAction annotation on static method TaskWithStaticMethod.doStuff().");
    }

    @Test
    public void failsWhenMethodWithParametersHasTaskActionAnnotation() {
        TaskWithParamMethod task = new TaskWithParamMethod();
        assertTaskCreationFails(task,
                "Cannot use @TaskAction annotation on method TaskWithParamMethod.doStuff() as this method takes parameters.");
    }

    private void assertTaskCreationFails(Task task, String message) {
        try {
            expectTaskCreated(task);
            fail();
        } catch (GradleException e) {
            assertThat(e.getMessage(), equalTo(message));
        }
    }

    @Test
    public void taskActionWorksForInheritedMethods() {
        final Runnable action = context.mock(Runnable.class);
        final TaskWithInheritedMethod task = new TaskWithInheritedMethod(action);

        expectTaskCreated(task);

        context.checking(new Expectations() {{
            one(action).run();
        }});
        task.execute();
    }

    @Test
    public void taskActionWorksForProtectedMethods() {
        final Runnable action = context.mock(Runnable.class);
        final TaskWithProtectedMethod task = new TaskWithProtectedMethod(action);

        expectTaskCreated(task);

        context.checking(new Expectations() {{
            one(action).run();
        }});
        task.execute();
    }

    @Test
    public void validationActionSucceedsWhenSpecifiedInputFileExists() {
        TaskWithInputFile task = new TaskWithInputFile(new File(testDir, "input.txt"));

        expectTaskCreated(task);
        GFileUtils.touch(task.inputFile);

        task.execute();
    }

    @Test
    public void validationActionFailsWhenInputFileNotSpecified() {
        TaskWithInputFile task = new TaskWithInputFile(null);

        expectTaskCreated(task);

        assertValidationFails(task, "No value has been specified for property 'inputFile'.");
    }

    @Test
    public void validationActionFailsWhenInputFileDoesNotExist() {
        TaskWithInputFile task = new TaskWithInputFile(new File(testDir, "input.txt"));

        expectTaskCreated(task);

        assertValidationFails(task, String.format("File '%s' specified for property 'inputFile' does not exist.",
                task.inputFile));
    }

    @Test
    public void validationActionFailsWhenInputFileIsADirectory() {
        TaskWithInputFile task = new TaskWithInputFile(testDir);

        expectTaskCreated(task);

        assertValidationFails(task, String.format("File '%s' specified for property 'inputFile' is not a file.",
                task.inputFile));
    }

    @Test
    public void validationActionSucceedsWhenSpecifiedOutputFileIsAFile() {
        TaskWithOutputFile task = new TaskWithOutputFile(new File(testDir, "output.txt"));

        expectTaskCreated(task);
        GFileUtils.touch(task.outputFile);

        task.execute();
    }

    @Test
    public void validationActionSucceedsWhenSpecifiedOutputFileIsNotAFile() {
        TaskWithOutputFile task = new TaskWithOutputFile(new File(testDir, "subdir/output.txt"));

        expectTaskCreated(task);

        task.execute();

        assertTrue(new File(testDir, "subdir").isDirectory());
    }

    @Test
    public void validationActionFailsWhenOutputFileNotSpecified() {
        TaskWithOutputFile task = new TaskWithOutputFile(null);

        expectTaskCreated(task);

        assertValidationFails(task, "No value has been specified for property 'outputFile'.");
    }

    @Test
    public void validationActionFailsWhenSpecifiedOutputFileIsADirectory() {
        TaskWithOutputFile task = new TaskWithOutputFile(testDir);

        expectTaskCreated(task);

        assertValidationFails(task, String.format(
                "Cannot write to file '%s' specified for property 'outputFile' as it is a directory.", task.outputFile));
    }

    @Test
    public void validationActionFailsWhenSpecifiedOutputFileParentIsAFile() {
        TaskWithOutputFile task = new TaskWithOutputFile(new File(testDir, "subdir/output.txt"));
        GFileUtils.touch(task.outputFile.getParentFile());

        expectTaskCreated(task);

        assertValidationFails(task, String.format(
                "Cannot create parent directory '%s' of file specified for property 'outputFile'.",
                task.outputFile.getParentFile()));
    }

    @Test
    public void validationActionSucceedsWhenInputFilesSpecified() {
        TaskWithInputFiles task = new TaskWithInputFiles(toList(testDir));

        expectTaskCreated(task);

        task.execute();
    }

    @Test
    public void validationActionFailsWhenInputFilesNotSpecified() {
        TaskWithInputFiles task = new TaskWithInputFiles(null);
        expectTaskCreated(task);

        assertValidationFails(task, "No value has been specified for property 'input'.");
    }

    @Test
    public void skipsTaskWhenInputFileCollectionIsEmpty() {
        final FileCollection inputFiles = context.mock(FileCollection.class);
        context.checking(new Expectations() {{
            one(inputFiles).stopExecutionIfEmpty();
            will(throwException(new StopExecutionException()));
        }});

        TaskWithInputFiles task = new TaskWithInputFiles(inputFiles) {
            @TaskAction
            void doStuff() {
                fail("task action should be skipped");
            }
        };
        expectTaskCreated(task);

        task.execute();
    }

    @Test
    public void validationActionSucceedsWhenSpecifiedOutputDirectoryDoesNotExist() {
        TaskWithOutputDir task = new TaskWithOutputDir(new File(testDir, "subdir"));
        expectTaskCreated(task);

        task.execute();

        assertTrue(task.outputDir.isDirectory());
    }

    @Test
    public void validationActionSucceedsWhenSpecifiedOutputDirectoryIsDirectory() {
        TaskWithOutputDir task = new TaskWithOutputDir(testDir);
        expectTaskCreated(task);

        task.execute();
    }

    @Test
    public void validationActionFailsWhenOutputDirectoryNotSpecified() {
        TaskWithOutputDir task = new TaskWithOutputDir(null);
        expectTaskCreated(task);

        assertValidationFails(task, "No value has been specified for property 'outputDir'.");
    }

    @Test
    public void validationActionFailsWhenOutputDirectoryIsAFile() {
        TaskWithOutputDir task = new TaskWithOutputDir(new File(testDir, "output"));
        expectTaskCreated(task);
        GFileUtils.touch(task.outputDir);

        assertValidationFails(task, String.format("Cannot create directory '%s' specified for property 'outputDir'.",
                task.outputDir));
    }

    @Test
    public void validationActionFailsWhenParentOfOutputDirectoryIsAFile() {
        TaskWithOutputDir task = new TaskWithOutputDir(new File(testDir, "subdir/output"));
        expectTaskCreated(task);
        GFileUtils.touch(task.outputDir.getParentFile());

        assertValidationFails(task, String.format("Cannot create directory '%s' specified for property 'outputDir'.",
                task.outputDir));
    }

    @Test
    public void validationActionSucceedsWhenSpecifiedInputDirectoryIsDirectory() {
        TaskWithInputDir task = new TaskWithInputDir(testDir);
        expectTaskCreated(task);

        task.execute();
    }

    @Test
    public void validationActionFailsWhenInputDirectoryNotSpecified() {
        TaskWithInputDir task = new TaskWithInputDir(null);
        expectTaskCreated(task);

        assertValidationFails(task, "No value has been specified for property 'inputDir'.");
    }
    
    @Test
    public void validationActionFailsWhenInputDirectoryDoesNotExist() {
        TaskWithInputDir task = new TaskWithInputDir(new File(testDir, "input"));
        expectTaskCreated(task);

        assertValidationFails(task, String.format("Directory '%s' specified for property 'inputDir' does not exist.",
                task.inputDir));
    }

    @Test
    public void validationActionFailsWhenInputDirectoryIsAFile() {
        TaskWithInputDir task = new TaskWithInputDir(new File(testDir, "input"));
        expectTaskCreated(task);
        GFileUtils.touch(task.inputDir);

        assertValidationFails(task, String.format("Directory '%s' specified for property 'inputDir' is not a directory.",
                task.inputDir));
    }

    @Test
    public void validationActionSucceedsWhenPropertyMarkedWithOptionalAnnotationNotSpecified() {
        TaskWithOptionalInputFile task = new TaskWithOptionalInputFile();
        expectTaskCreated(task);

        task.execute();
    }

    private void assertValidationFails(TaskInternal task, String expectedErrorMessage) {
        try {
            task.execute();
            fail();
        } catch (GradleException e) {
            assertThat(e.getCause(), instanceOf(InvalidUserDataException.class));
            assertThat(e.getCause().getMessage(), equalTo(expectedErrorMessage));
        }
    }

    public static class TestTask extends DefaultTask {
        final Runnable action;

        public TestTask(Runnable action) {
            super(HelperUtil.createRootProject(), "someName");
            this.action = action;
        }

        @TaskAction
        public void doStuff() {
            action.run();
        }
    }

    public static class TaskWithInheritedMethod extends TestTask {
        public TaskWithInheritedMethod(Runnable action) {
            super(action);
        }
    }

    public static class TaskWithProtectedMethod extends DefaultTask {
        private final Runnable action;

        public TaskWithProtectedMethod(Runnable action) {
            super(HelperUtil.createRootProject(), "someName");
            this.action = action;
        }

        @TaskAction
        protected void doStuff() {
            action.run();
        }
    }

    public static class TaskWithStaticMethod extends DefaultTask {
        @TaskAction
        public static void doStuff() {
        }
    }

    public static class TaskWithMultipleMethods extends TestTask {
        public TaskWithMultipleMethods(Runnable action) {
            super(action);
        }

        @TaskAction
        public void aMethod() {
            action.run();
        }

        @TaskAction
        public void anotherMethod() {
            action.run();
        }
    }

    public static class TaskWithParamMethod extends DefaultTask {
        @TaskAction
        public void doStuff(int value) {
        }
    }

    public static class TaskWithInputFile extends DefaultTask {
        File inputFile;

        public TaskWithInputFile(File inputFile) {
            super(HelperUtil.createRootProject(), "someName");
            this.inputFile = inputFile;
        }

        @InputFile
        public File getInputFile() {
            return inputFile;
        }
    }

    public static class TaskWithInputDir extends DefaultTask {
        File inputDir;

        public TaskWithInputDir(File inputDir) {
            super(HelperUtil.createRootProject(), "someName");
            this.inputDir = inputDir;
        }

        @InputDirectory
        public File getInputDir() {
            return inputDir;
        }
    }

    public static class TaskWithOutputFile extends DefaultTask {
        File outputFile;

        public TaskWithOutputFile(File outputFile) {
            super(HelperUtil.createRootProject(), "someName");
            this.outputFile = outputFile;
        }

        @OutputFile
        public File getOutputFile() {
            return outputFile;
        }
    }

    public static class TaskWithOutputDir extends DefaultTask {
        File outputDir;

        public TaskWithOutputDir(File outputDir) {
            super(HelperUtil.createRootProject(), "someName");
            this.outputDir = outputDir;
        }

        @OutputDirectory
        public File getOutputDir() {
            return outputDir;
        }
    }

    public static class TaskWithInputFiles extends DefaultTask {
        Iterable<File> input;

        public TaskWithInputFiles(Iterable<File> input) {
            super(HelperUtil.createRootProject(), "someName");
            this.input = input;
        }

        @InputFiles @SkipWhenEmpty
        public Iterable<File> getInput() {
            return input;
        }
    }

    private static class TaskWithOptionalInputFile extends DefaultTask {
        public TaskWithOptionalInputFile() {
            super(HelperUtil.createRootProject(), "someName");
        }

        @InputFile @Optional
        public File getInputFile() {
            return null;
        }
    }
}
