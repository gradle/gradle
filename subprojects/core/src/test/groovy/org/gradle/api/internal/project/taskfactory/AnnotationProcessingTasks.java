/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.project.taskfactory;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectories;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;

import java.io.File;
import java.util.List;

import static org.junit.Assert.fail;

/**
 * A set of classes for use in the AnnotationProcessingTaskFactoryTest.
 */
public class AnnotationProcessingTasks {
    public static class TestTask extends DefaultTask {
        final Runnable action;

        public TestTask(Runnable action) {
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

    public static class TaskWithOverriddenMethod extends TestTask {
        private final Runnable action;

        public TaskWithOverriddenMethod(Runnable action) {
            super(null);
            this.action = action;
        }

        @Override
        @TaskAction
        public void doStuff() {
            action.run();
        }
    }

    public static class TaskWithProtectedMethod extends DefaultTask {
        private final Runnable action;

        public TaskWithProtectedMethod(Runnable action) {
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

    public static class TaskWithIncrementalAction extends DefaultTask {
        private final Action<IncrementalTaskInputs> action;

        public TaskWithIncrementalAction(Action<IncrementalTaskInputs> action) {
            this.action = action;
        }

        @TaskAction
        public void doStuff(IncrementalTaskInputs changes) {
            action.execute(changes);
        }
    }

    public static class TaskWithMultipleIncrementalActions extends DefaultTask {

        @TaskAction
        public void doStuff(IncrementalTaskInputs changes) {
        }

        @TaskAction
        public void doStuff2(IncrementalTaskInputs changes) {
        }
    }

    public static class TaskWithSingleParamAction extends DefaultTask {
        @TaskAction
        public void doStuff(int value1) {
        }
    }

    public static class TaskWithMultiParamAction extends DefaultTask {
        @TaskAction
        public void doStuff(int value1, int value2) {
        }
    }

    public static class TaskWithInputFile extends DefaultTask {
        File inputFile;

        public TaskWithInputFile(File inputFile) {
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
            this.inputDir = inputDir;
        }

        @InputDirectory
        public File getInputDir() {
            return inputDir;
        }
    }

    public static class TaskWithInput extends DefaultTask {
        String inputValue;

        public TaskWithInput(String inputValue) {
            this.inputValue = inputValue;
        }

        @Input
        public String getInputValue() {
            return inputValue;
        }
    }

    public static class TaskWithBooleanInput extends DefaultTask {
        boolean inputValue;

        public TaskWithBooleanInput(boolean inputValue) {
            this.inputValue = inputValue;
        }

        @Input
        public boolean isInputValue() {
            return inputValue;
        }
    }

    public static class BrokenTaskWithInputDir extends TaskWithInputDir {
        public BrokenTaskWithInputDir(File inputDir) {
            super(inputDir);
        }

        @Override
        @InputDirectory
        @SkipWhenEmpty
        public File getInputDir() {
            return super.getInputDir();
        }

        @TaskAction
        public void doStuff() {
            fail();
        }

    }

    public static class TaskWithOutputFile extends DefaultTask {
        File outputFile;

        public TaskWithOutputFile(File outputFile) {
            this.outputFile = outputFile;
        }

        @OutputFile
        public File getOutputFile() {
            return outputFile;
        }
    }

    public static class TaskWithOutputFiles extends DefaultTask {
        List<File> outputFiles;

        public TaskWithOutputFiles(List<File> outputFiles) {
            this.outputFiles = outputFiles;
        }

        @SuppressWarnings("deprecation")
        @OutputFiles
        public List<File> getOutputFiles() {
            return outputFiles;
        }
    }

    public static class TaskWithBridgeMethod extends DefaultTask implements WithProperty<SpecificProperty> {
        @org.gradle.api.tasks.Nested
        private SpecificProperty nestedProperty = new SpecificProperty();
        public int traversedOutputsCount;

        public SpecificProperty getNestedProperty() {
            traversedOutputsCount++;
            return nestedProperty;
        }
    }

    public interface WithProperty<T extends PropertyContainer> {
        T getNestedProperty();
    }
    public interface PropertyContainer<T extends SomeProperty> {}
    public static class SpecificProperty extends SomePropertyContainer<SomeProperty> {}
    public static class SomeProperty {}

    public static abstract class SomePropertyContainer<T extends SomeProperty> implements PropertyContainer {
        @OutputFile
        public File getSomeOutputFile() {
            return null;
        }
    }

    public static class TaskWithOptionalOutputFile extends DefaultTask {
        @OutputFile
        @org.gradle.api.tasks.Optional
        public File getOutputFile() {
            return null;
        }
    }

    public static class TaskWithOptionalOutputFiles extends DefaultTask {
        @SuppressWarnings("deprecation")
        @OutputFiles
        @org.gradle.api.tasks.Optional
        public List<File> getOutputFiles() {
            return null;
        }
    }

    public static class TaskWithOutputDir extends DefaultTask {
        File outputDir;

        public TaskWithOutputDir(File outputDir) {
            this.outputDir = outputDir;
        }

        @OutputDirectory
        public File getOutputDir() {
            return outputDir;
        }
    }

    public static class TaskWithOutputDirs extends DefaultTask {
        List<File> outputDirs;

        public TaskWithOutputDirs(List<File> outputDirs) {
            this.outputDirs = outputDirs;
        }

        @SuppressWarnings("deprecation")
        @OutputDirectories
        public List<File> getOutputDirs() {
            return outputDirs;
        }
    }

    public static class TaskWithOptionalOutputDir extends DefaultTask {
        @OutputDirectory
        @org.gradle.api.tasks.Optional
        public File getOutputDir() {
            return null;
        }
    }

    public static class TaskWithOptionalOutputDirs extends DefaultTask {
        @SuppressWarnings("deprecation")
        @OutputDirectories
        @org.gradle.api.tasks.Optional
        public File getOutputDirs() {
            return null;
        }
    }

    public static class TaskWithInputFiles extends DefaultTask {
        Iterable<? extends File> input;

        public TaskWithInputFiles(Iterable<? extends File> input) {
            this.input = input;
        }

        @InputFiles
        public Iterable<? extends File> getInput() {
            return input;
        }
    }

    public static class BrokenTaskWithInputFiles extends TaskWithInputFiles {
        public BrokenTaskWithInputFiles(Iterable<? extends File> input) {
            super(input);
        }

        @InputFiles
        @SkipWhenEmpty
        public Iterable<? extends File> getInput() {
            return input;
        }

        @TaskAction
        public void doStuff() {
            fail();
        }
    }

    public static class TaskWithOptionalInputFile extends DefaultTask {
        @InputFile
        @org.gradle.api.tasks.Optional
        public File getInputFile() {
            return null;
        }
    }

    public static class TaskWithNestedBean extends DefaultTask {
        Bean bean = new Bean();

        public TaskWithNestedBean(File inputFile) {
            bean.inputFile = inputFile;
        }

        @Nested
        public Bean getBean() {
            return bean;
        }

        public void clearBean() {
            bean = null;
        }
    }


    public static class TaskWithNestedBeanWithPrivateClass extends DefaultTask {
        Bean2 bean = new Bean2();

        public TaskWithNestedBeanWithPrivateClass(File inputFile, File inputFile2) {
            bean.inputFile = inputFile;
            bean.inputFile2 = inputFile2;
        }

        @Nested
        public Bean getBean() {
            return bean;
        }

        public void clearBean() {
            bean = null;
        }
    }

    public static class TaskWithMultipleProperties extends TaskWithNestedBean {
        public TaskWithMultipleProperties(File inputFile) {
            super(inputFile);
        }

        @OutputFile
        public File getOutputFile() {
            return bean.getInputFile();
        }
    }

    public static class TaskWithOptionalNestedBean extends DefaultTask {
        @Nested
        @org.gradle.api.tasks.Optional
        public Bean getBean() {
            return null;
        }
    }

    public static class TaskWithOptionalNestedBeanWithPrivateType extends DefaultTask {
        Bean2 bean = new Bean2();

        @Nested
        @org.gradle.api.tasks.Optional
        public Bean getBean() {
            return null;
        }
    }

    public static class Bean {
        @InputFile
        File inputFile;

        public File getInputFile() {
            return inputFile;
        }
    }

    public static class Bean2 extends Bean {
        @InputFile
        File inputFile2;

        public File getInputFile() {
            return inputFile2;
        }
    }

    //CHECKSTYLE:OFF
    public static class TaskWithJavaBeanCornerCaseProperties extends DefaultTask {
        private String cCompiler;
        private String CFlags;
        private String dns;
        private String URL;
        private String a;
        private String b;

        public TaskWithJavaBeanCornerCaseProperties(String cCompiler, String CFlags, String dns, String URL, String a, String b) {
            this.cCompiler = cCompiler;
            this.CFlags = CFlags;
            this.dns = dns;
            this.URL = URL;
            this.a = a;
            this.b = b;
        }

        @Input
        public String getcCompiler() {
            return cCompiler;
        }

        @Input
        public String getCFlags() {
            return CFlags;
        }

        @Input
        public String getDns() {
            return dns;
        }

        @Input
        public String getURL() {
            return URL;
        }

        @Input
        public String getA() {
            return a;
        }

        @Input
        public String getb() {
            return b;
        }
    }
    //CHECKSTYLE:ON
}
