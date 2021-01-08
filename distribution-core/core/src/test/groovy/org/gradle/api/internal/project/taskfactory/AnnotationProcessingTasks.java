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

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Named;
import org.gradle.api.tasks.Destroys;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.LocalState;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectories;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.work.InputChanges;

import javax.annotation.Nullable;
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

    public static class TaskWithAction extends DefaultTask {
        @TaskAction
        public void doStuff() {}
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

        @Optional
        @OutputFile
        @Nullable
        public File getOutputFile() {
            return null;
        }
    }

    @SuppressWarnings("GrDeprecatedAPIUsage")
    public static class TaskWithOverriddenIncrementalAction extends TaskWithIncrementalAction {
        private final Action<IncrementalTaskInputs> action;

        public TaskWithOverriddenIncrementalAction(Action<IncrementalTaskInputs> action, Action<IncrementalTaskInputs> superAction) {
            super(superAction);
            this.action = action;
        }

        @Override
        @TaskAction
        public void doStuff(IncrementalTaskInputs changes) {
            action.execute(changes);
        }
    }

    public static class TaskWithOverloadedActions extends DefaultTask {
        @TaskAction
        public void doStuff() {}

        @TaskAction
        public void doStuff(IncrementalTaskInputs changes) {}
    }

    public static class TaskUsingInputChanges extends DefaultTask {
        private final Action<InputChanges> action;

        public TaskUsingInputChanges(Action<InputChanges> action) {
            this.action = action;
        }

        @TaskAction
        public void doStuff(InputChanges changes) {
            action.execute(changes);
        }

        @Optional
        @OutputFile
        @Nullable
        public File getOutputFile() {
            return null;
        }
    }

    public static class TaskWithOverriddenInputChangesAction extends TaskUsingInputChanges {
        private final Action<InputChanges> action;

        public TaskWithOverriddenInputChangesAction(Action<InputChanges> action, Action<InputChanges> superAction) {
            super(superAction);
            this.action = action;
        }

        @Override
        @TaskAction
        public void doStuff(InputChanges changes) {
            action.execute(changes);
        }
    }

    public static class TaskWithMultipleInputChangesActions extends DefaultTask {

        @TaskAction
        public void doStuff(InputChanges changes) {
        }

        @TaskAction
        public void doStuff2(InputChanges changes) {
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

    public static class TaskWithMixedMultipleIncrementalActions extends DefaultTask {

        @TaskAction
        public void doStuff(IncrementalTaskInputs changes) {
        }

        @TaskAction
        public void doStuff2(InputChanges changes) {
        }
    }

    public static class TaskWithOverloadedInputChangesActions extends DefaultTask {
        @TaskAction
        public void doStuff() {}

        @TaskAction
        public void doStuff(InputChanges changes) {}
    }

    public static class TaskWithOverloadedIncrementalAndInputChangesActions extends DefaultTask {
        @TaskAction
        public void doStuff(IncrementalTaskInputs changes) {}

        @TaskAction
        public void doStuff(InputChanges changes) {}
    }

    public static class TaskWithOverloadedDeprecatedIncrementalAndInputChangesActions extends DefaultTask {
        private final Action<Object> changesAction;

        public TaskWithOverloadedDeprecatedIncrementalAndInputChangesActions(Action<Object> changesAction) {
            this.changesAction = changesAction;
        }

        @TaskAction
        @Deprecated
        public void doStuff(IncrementalTaskInputs changes) {
            changesAction.execute(changes);
            doStuff((InputChanges) changes);
        }

        @TaskAction
        public void doStuff(InputChanges changes) {
            changesAction.execute(changes);
        }

        @Optional
        @OutputDirectory
        @Nullable
        public File getOutputDirectory() {
            return null;
        }
    }

    public static class TaskOverridingDeprecatedIncrementalChangesActions extends TaskWithOverloadedDeprecatedIncrementalAndInputChangesActions {
        public TaskOverridingDeprecatedIncrementalChangesActions(Action<Object> changesAction) {
            super(changesAction);
        }

        @Override
        public void doStuff(IncrementalTaskInputs changes) {
            super.doStuff(changes);
        }
    }

    public static class TaskOverridingInputChangesActions extends TaskWithOverloadedDeprecatedIncrementalAndInputChangesActions {
        public TaskOverridingInputChangesActions(Action<Object> changesAction) {
            super(changesAction);
        }

        @Override
        public void doStuff(InputChanges changes) {
            super.doStuff(changes);
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

    public static class TaskWithInputFile extends TaskWithAction {
        File inputFile;

        public TaskWithInputFile(File inputFile) {
            this.inputFile = inputFile;
        }

        @InputFile
        public File getInputFile() {
            return inputFile;
        }
    }

    public static class TaskWithInputDir extends TaskWithAction {
        File inputDir;

        public TaskWithInputDir(File inputDir) {
            this.inputDir = inputDir;
        }

        @InputDirectory
        public File getInputDir() {
            return inputDir;
        }
    }

    public static class TaskWithInput extends TaskWithAction {
        String inputValue;

        public TaskWithInput(String inputValue) {
            this.inputValue = inputValue;
        }

        @Input
        public String getInputValue() {
            return inputValue;
        }
    }

    public static class TaskWithBooleanInput extends TaskWithAction {
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

    public static class TaskWithOutputFile extends TaskWithAction {
        File outputFile;

        public TaskWithOutputFile(File outputFile) {
            this.outputFile = outputFile;
        }

        @OutputFile
        public File getOutputFile() {
            return outputFile;
        }
    }

    public static class TaskWithOutputFiles extends TaskWithAction {
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

    public static class TaskWithBridgeMethod extends TaskWithAction implements WithProperty<SpecificProperty> {
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
            return new File("hello");
        }
    }

    public static class TaskWithOptionalOutputFile extends TaskWithAction {
        @OutputFile
        @org.gradle.api.tasks.Optional
        public File getOutputFile() {
            return null;
        }
    }

    public static class TaskWithOptionalOutputFiles extends TaskWithAction {
        @SuppressWarnings("deprecation")
        @OutputFiles
        @org.gradle.api.tasks.Optional
        public List<File> getOutputFiles() {
            return null;
        }
    }

    public static class TaskWithOutputDir extends TaskWithAction {
        File outputDir;

        public TaskWithOutputDir(File outputDir) {
            this.outputDir = outputDir;
        }

        @OutputDirectory
        public File getOutputDir() {
            return outputDir;
        }
    }

    public static class TaskWithOutputDirs extends TaskWithAction {
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

    public static class TaskWithOptionalOutputDir extends TaskWithAction {
        @OutputDirectory
        @org.gradle.api.tasks.Optional
        public File getOutputDir() {
            return null;
        }
    }

    public static class TaskWithOptionalOutputDirs extends TaskWithAction {
        @SuppressWarnings("deprecation")
        @OutputDirectories
        @org.gradle.api.tasks.Optional
        public File getOutputDirs() {
            return null;
        }
    }

    public static class TaskWithInputFiles extends TaskWithAction {
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

    public static class TaskWithOptionalInputFile extends TaskWithAction {
        @InputFile
        @org.gradle.api.tasks.Optional
        public File getInputFile() {
            return null;
        }
    }

    public static class TaskWithLocalState extends TaskWithAction {
        private File localStateFile;

        public TaskWithLocalState(File localStateFile) {
            this.localStateFile = localStateFile;
        }

        @LocalState
        public File getLocalStateFile() {
            return localStateFile;
        }
    }

    public static class TaskWithDestroyable extends TaskWithAction {
        File destroyable;
        public TaskWithDestroyable(File destroyable) {
            this.destroyable = destroyable;
        }

        @Destroys
        public File getDestroyable() {
            return destroyable;
        }
    }

    public static class TaskWithNestedBean extends TaskWithAction {
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

    public static class TaskWithNestedObject extends TaskWithAction {
        Object bean;

        public TaskWithNestedObject(Object bean) {
            this.bean = bean;
        }

        @Nested
        public Object getBean() {
            return bean;
        }
    }

    public static class TaskWithNestedIterable extends TaskWithAction {
        Object bean;

        public TaskWithNestedIterable(Object nested) {
            bean = nested;
        }

        @Nested
        public List<?> getBeans() {
            return ImmutableList.of(bean);
        }
    }

    public static class TaskWithNestedBeanWithPrivateClass extends TaskWithAction {
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

    public static class TaskWithOptionalNestedBean extends TaskWithAction {
        private final Bean bean;

        public TaskWithOptionalNestedBean(Bean bean) {
            this.bean = bean;
        }

        @Nested
        @org.gradle.api.tasks.Optional
        public Bean getBean() {
            return bean;
        }
    }

    public static class TaskWithOptionalNestedBeanWithPrivateType extends TaskWithAction {
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

    public static class BeanWithInput {
        private final String input;

        public BeanWithInput(String input) {
            this.input = input;
        }

        @Input
        public String getInput() {
            return input;
        }
    }

    public static class NamedBean implements Named {
        private final String name;
        private final String value;

        public NamedBean(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() {
            return name;
        }

        @Input
        public String getValue() {
            return value;
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
    public static class TaskWithJavaBeanCornerCaseProperties extends TaskWithAction {
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
