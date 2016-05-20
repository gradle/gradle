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

package org.gradle.api.internal.project.taskfactory

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.internal.AbstractTask
import org.gradle.api.internal.ClassGenerator
import org.gradle.api.internal.TaskInternal
import org.gradle.api.tasks.TaskValidationException
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GFileUtils
import spock.lang.Issue

import java.util.concurrent.Callable

import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.*

class AnnotationProcessingTaskFactoryTest extends AbstractProjectBuilderSpec {
    private AnnotationProcessingTaskFactory factory
    private ITaskFactory delegate
    private Map args = new HashMap()

    private File testDir
    private File existingFile
    private File missingFile
    private TestFile existingDir
    private File missingDir
    private File missingDir2

    def setup() {
        delegate = Mock(ITaskFactory)
        factory = new AnnotationProcessingTaskFactory(new DefaultTaskClassInfoStore(), delegate)
        testDir = temporaryFolder.testDirectory
        existingFile = testDir.file("file.txt").touch()
        missingFile = testDir.file("missing.txt")
        existingDir = testDir.file("dir").createDir()
        missingDir = testDir.file("missing-dir")
        missingDir2 = testDir.file("missing-dir2")
    }

    def attachesAnActionToTaskForMethodMarkedWithTaskActionAnnotation() {
        given:
        def action = Mock(Runnable)
        def task = expectTaskCreated(TestTask, action)

        when:
        task.execute()

        then:
        1 * action.run()
    }

    def doesNothingToTaskWithNoTaskActionAnnotations() {
        given:
        def task = expectTaskCreated(DefaultTask)

        expect:
        task.getActions().isEmpty()
    }

    def propagatesExceptionThrownByTaskActionMethod() {
        given:
        def action = Mock(Runnable, {
            run() >> {
                throw new RuntimeException()
            }
        })
        def task = expectTaskCreated(TestTask, action)

        when:
        task.getActions().get(0).execute(task)

        then:
        thrown(RuntimeException)
    }

    def canHaveMultipleMethodsWithTaskActionAnnotation() {
        given:
        def action = Mock(Runnable)
        def task = expectTaskCreated(TaskWithMultipleMethods, action)

        when:
        task.execute()

        then:
        3 * action.run()
    }

    def createsContextualActionFoIncrementalTaskAction() {
        given:
        def Action<IncrementalTaskInputs> action = Mock(Action)
        def task = expectTaskCreated(TaskWithIncrementalAction, action)

        when:
        task.execute()

        then:
        1 * action.execute(_ as IncrementalTaskInputs)
    }

    def cachesClassMetaInfo() {
        given:
        def task = expectTaskCreated(TaskWithInputFile, existingFile)
        def task2 = expectTaskCreated(TaskWithInputFile, missingFile)

        expect:
        task.actions[0].action.is(task2.actions[0].action)
    }

    def failsWhenMultipleActionsAreIncremental() {
        when:
        expectTaskCreated(TaskWithMultipleIncrementalActions)

        then:
        GradleException e = thrown()
        e.getMessage().equals("Cannot have multiple @TaskAction methods accepting an IncrementalTaskInputs parameter.")
    }

    def failsWhenStaticMethodHasTaskActionAnnotation() {
        when:
        expectTaskCreated(TaskWithStaticMethod)

        then:
        GradleException e = thrown()
        e.getMessage().equals("Cannot use @TaskAction annotation on static method TaskWithStaticMethod.doStuff().")
    }

    def failsWhenMethodWithParametersHasTaskActionAnnotation() {
        when:
        expectTaskCreated(TaskWithMultiParamAction)

        then:
        GradleException e = thrown()
        e.getMessage().equals("Cannot use @TaskAction annotation on method TaskWithMultiParamAction.doStuff() as this method takes multiple parameters.")
    }

    def failsWhenMethodWithInvalidParameterHasTaskActionAnnotation() {
        when:
        expectTaskCreated(TaskWithSingleParamAction)

        then:
        GradleException e = thrown()
        e.getMessage().equals("Cannot use @TaskAction annotation on method TaskWithSingleParamAction.doStuff() because int is not a valid parameter to an action method.")
    }

    def taskActionWorksForInheritedMethods() {
        given:
        def action = Mock(Runnable)
        def task = expectTaskCreated(TaskWithInheritedMethod, action)

        when:
        task.execute()

        then:
        1 * action.run()
    }

    def taskActionWorksForOverriddenMethods() {
        given:
        def action = Mock(Runnable)
        def task = expectTaskCreated(TaskWithOverriddenMethod, action)

        when:
        task.execute()

        then:
        1 * action.run()
    }

    def taskActionWorksForProtectedMethods() {
        given:
        def action = Mock(Runnable)
        def task = expectTaskCreated(TaskWithProtectedMethod, action)

        when:
        task.execute()

        then:
        1 * action.run()
    }

    def validationActionSucceedsWhenSpecifiedInputFileExists() {
        given:
        def task = expectTaskCreated(TaskWithInputFile, existingFile)

        expect:
        task.execute()
    }

    def validationActionFailsWhenInputFileNotSpecified() {
        given:
        def task = expectTaskCreated(TaskWithInputFile, [null] as Object[])

        when:
        task.execute()

        then:
        TaskValidationException e = thrown()
        e.message.contains("A problem was found with the configuration of $task")
        e.causes.collect({ it.message }) as Set == ["No value has been specified for property 'inputFile'."] as Set
    }

    def validationActionFailsWhenInputFileDoesNotExist() {
        given:
        def task = expectTaskCreated(TaskWithInputFile, missingFile)

        when:
        task.execute()

        then:
        TaskValidationException e = thrown()
        e.message.contains("A problem was found with the configuration of $task")
        e.causes.collect({ it.message }) as Set == ["File '$task.inputFile' specified for property 'inputFile' does not exist.".toString()] as Set
    }

    def validationActionFailsWhenInputFileIsADirectory() {
        given:
        def task = expectTaskCreated(TaskWithInputFile, existingDir)

        when:
        task.execute()

        then:
        TaskValidationException e = thrown()
        e.message.contains("A problem was found with the configuration of $task")
        e.causes.collect({ it.message }) as Set == ["File '$task.inputFile' specified for property 'inputFile' is not a file.".toString()] as Set
    }

    def registersSpecifiedInputFile() {
        given:
        def task = expectTaskCreated(TaskWithInputFile, existingFile)

        expect:
        task.inputs.files.files == [existingFile] as Set
    }

    def doesNotRegistersInputFileWhenNoneSpecified() {
        given:
        def task = expectTaskCreated(TaskWithInputFile, [null] as Object[])

        expect:
        task.inputs.files.files.isEmpty()
    }

    def validationActionSucceedsWhenSpecifiedOutputFileIsAFile() {
        given:
        def task = expectTaskCreated(TaskWithOutputFile, existingFile)

        expect:
        task.execute()
    }

    def validationActionSucceedsWhenSpecifiedOutputFilesIsAFile() {
        given:
        def task = expectTaskCreated(TaskWithOutputFiles, Collections.singletonList(existingFile))

        expect:
        task.execute()
    }

    def validationActionSucceedsWhenSpecifiedOutputFileDoesNotExist() {
        given:
        def task = expectTaskCreated(TaskWithOutputFile, new File(testDir, "subdir/output.txt"))

        when:
        task.execute()

        then:
        new File(testDir, "subdir").isDirectory()
    }

    def validationActionSucceedsWhenSpecifiedOutputFilesDoesNotExist() {
        given:
        def task = expectTaskCreated(TaskWithOutputFiles, Arrays.asList(new File(testDir, "subdir/output.txt"), new File(testDir, "subdir2/output.txt")))

        when:
        task.execute()

        then:
        new File(testDir, "subdir").isDirectory()
        new File(testDir, "subdir2").isDirectory()
    }

    def validationActionSucceedsWhenOptionalOutputFileNotSpecified() {
        given:
        def task = expectTaskCreated(TaskWithOptionalOutputFile)

        expect:
        task.execute()
    }

    def validationActionSucceedsWhenOptionalOutputFilesNotSpecified() {
        given:
        def task = expectTaskCreated(TaskWithOptionalOutputFiles)

        expect:
        task.execute()
    }

    def validationActionFailsWhenOutputFileNotSpecified() {
        given:
        def task = expectTaskCreated(TaskWithOutputFile, [null] as Object[])

        when:
        task.execute()

        then:
        TaskValidationException e = thrown()
        e.message.contains("A problem was found with the configuration of $task")
        e.causes.collect({ it.message }) as Set == ["No value has been specified for property 'outputFile'."] as Set
    }

    def validationActionFailsWhenOutputFilesNotSpecified() {
        given:
        def task = expectTaskCreated(TaskWithOutputFiles, [null] as Object[])

        when:
        task.execute()

        then:
        TaskValidationException e = thrown()
        e.message.contains("A problem was found with the configuration of $task")
        e.causes.collect({ it.message }) as Set == ["No value has been specified for property 'outputFiles'."] as Set
    }

    def validationActionFailsWhenSpecifiedOutputFileIsADirectory() {
        given:
        def task = expectTaskCreated(TaskWithOutputFile, existingDir)

        when:
        task.execute()

        then:
        TaskValidationException e = thrown()
        e.message.contains("A problem was found with the configuration of $task")
        e.causes.collect({ it.message }) as Set == ["Cannot write to file '${task.outputFile}' specified for property 'outputFile' as it is a directory.".toString()] as Set
    }

    def validationActionFailsWhenSpecifiedOutputFilesIsADirectory() {
        given:
        def task = expectTaskCreated(TaskWithOutputFiles, Collections.singletonList(existingDir))

        when:
        task.execute()

        then:
        TaskValidationException e = thrown()
        e.message.contains("A problem was found with the configuration of $task")
        e.causes.collect({ it.message }) as Set == ["Cannot write to file '${task.outputFiles[0]}' specified for property 'outputFiles' as it is a directory.".toString()] as Set
    }

    def validationActionFailsWhenSpecifiedOutputFileParentIsAFile() {
        given:
        def task = expectTaskCreated(TaskWithOutputFile, new File(testDir, "subdir/output.txt"))
        GFileUtils.touch(task.outputFile.getParentFile())

        when:
        task.execute()

        then:
        TaskValidationException e = thrown()
        e.message.contains("A problem was found with the configuration of $task")
        e.causes.collect({ it.message }) as Set == [
            "Cannot write to file '${task.outputFile}' specified for property 'outputFile', as ancestor '${task.outputFile.parentFile}' is not a directory.".toString()] as Set
    }

    def validationActionFailsWhenSpecifiedOutputFilesParentIsAFile() {
        given:
        def task = expectTaskCreated(TaskWithOutputFiles, Collections.singletonList(new File(testDir, "subdir/output.txt")))
        GFileUtils.touch(task.outputFiles.get(0).getParentFile())

        when:
        task.execute()

        then:
        TaskValidationException e = thrown()
        e.message.contains("A problem was found with the configuration of $task")
        e.causes.collect({ it.message }) as Set == [
            "Cannot write to file '${task.outputFiles[0]}' specified for property 'outputFiles', as ancestor '${task.outputFiles[0].parentFile}' is not a directory.".toString()] as Set
    }

    def registersSpecifiedOutputFile() {
        given:
        def task = expectTaskCreated(TaskWithOutputFile, existingFile)

        expect:
        task.outputs.files.files == [existingFile] as Set
    }

    def registersSpecifiedOutputFiles() {
        given:
        def task = expectTaskCreated(TaskWithOutputFiles, Collections.singletonList(existingFile))

        expect:
        task.outputs.files.files == [existingFile] as Set
    }

    def doesNotRegisterOutputFileWhenNoneSpecified() {
        given:
        def task = expectTaskCreated(TaskWithOutputFile, [null] as Object[])

        expect:
        task.outputs.files.files.isEmpty()
    }

    def doesNotRegisterOutputFilesWhenNoneSpecified() {
        given:
        def task = expectTaskCreated(TaskWithOutputFiles, [null] as Object[])

        expect:
        task.outputs.files.files.isEmpty()

        when:
        task = expectTaskCreated(TaskWithOutputFiles, Collections.<File>emptyList())

        then:
        task.outputs.files.files.isEmpty()
    }

    def validationActionSucceedsWhenInputFilesSpecified() {
        given:
        def task = expectTaskCreated(TaskWithInputFiles, [testDir] as List)

        expect:
        task.execute()
    }

    def validationActionFailsWhenInputFilesNotSpecified() {
        given:
        def task = expectTaskCreated(TaskWithInputFiles, [null] as Object[])

        when:
        task.execute()

        then:
        TaskValidationException e = thrown()
        e.message.contains("A problem was found with the configuration of $task")
        e.causes.collect({ it.message }) as Set == ["No value has been specified for property 'input'."] as Set
    }

    def registersSpecifiedInputFiles() {
        given:
        def task = expectTaskCreated(TaskWithInputFiles, [testDir, missingFile] as List)

        expect:
        task.inputs.files.files == [testDir, missingFile] as Set
    }

    def doesNotRegisterInputFilesWhenNoneSpecified() {
        given:
        def task = expectTaskCreated(TaskWithInputFiles, [null] as Object[])

        expect:
        task.inputs.files.files.isEmpty()
    }

    def skipsTaskWhenInputFileCollectionIsEmpty() {
        given:
        def inputFiles = new ArrayList<File>()
        BrokenTaskWithInputFiles task = expectTaskCreated(BrokenTaskWithInputFiles, inputFiles)

        expect:
        task.execute()
    }

    def validationActionSucceedsWhenSpecifiedOutputDirectoryDoesNotExist() {
        given:
        def task = expectTaskCreated(TaskWithOutputDir, missingDir)

        when:
        task.execute()

        then:
        task.outputDir.isDirectory()
    }

    def validationActionSucceedsWhenSpecifiedOutputDirectoriesDoesNotExist() {
        given:
        def task = expectTaskCreated(TaskWithOutputDirs, Collections.singletonList(missingDir))

        when:
        task.execute()

        then:
        task.outputDirs.get(0).isDirectory()
    }

    def validationActionSucceedsWhenSpecifiedOutputDirectoryIsDirectory() {
        given:
        def task = expectTaskCreated(TaskWithOutputDir, existingDir)

        expect:
        task.execute()
    }

    def validationActionSucceedsWhenSpecifiedOutputDirectoriesAreDirectories() {
        given:
        def task = expectTaskCreated(TaskWithOutputDirs, Collections.singletonList(existingDir))

        expect:
        task.execute()
    }

    def validationActionSucceedsWhenOptionalOutputDirectoryNotSpecified() {
        given:
        def task = expectTaskCreated(TaskWithOptionalOutputDir)

        expect:
        task.execute()
    }

    def validationActionSucceedsWhenOptionalOutputDirectoriesNotSpecified() {
        given:
        def task = expectTaskCreated(TaskWithOptionalOutputDirs)

        expect:
        task.execute()
    }

    def validationActionFailsWhenOutputDirectoryNotSpecified() {
        given:
        def task = expectTaskCreated(TaskWithOutputDir, [null] as Object[])

        when:
        task.execute()

        then:
        TaskValidationException e = thrown()
        e.message.contains("A problem was found with the configuration of $task")
        e.causes.collect({ it.message }) as Set == ["No value has been specified for property 'outputDir'."] as Set
    }

    def validationActionFailsWhenOutputDirectoriesNotSpecified() {
        given:
        def task = expectTaskCreated(TaskWithOutputDirs, [null] as Object[])

        when:
        task.execute()

        then:
        TaskValidationException e = thrown()
        e.message.contains("A problem was found with the configuration of $task")
        e.causes.collect({ it.message }) as Set == ["No value has been specified for property 'outputDirs'."] as Set
    }

    def validationActionFailsWhenOutputDirectoryIsAFile() {
        given:
        def task = expectTaskCreated(TaskWithOutputDir, existingFile)

        when:
        task.execute()

        then:
        TaskValidationException e = thrown()
        e.message.contains("A problem was found with the configuration of $task")
        e.causes.collect({ it.message }) as Set == ["Directory '${task.outputDir}' specified for property 'outputDir' is not a directory.".toString()] as Set
    }

    def validationActionFailsWhenOutputDirectoriesIsAFile() {
        given:
        def task = expectTaskCreated(TaskWithOutputDirs, Collections.singletonList(existingFile))

        when:
        task.execute()

        then:
        TaskValidationException e = thrown()
        e.message.contains("A problem was found with the configuration of $task")
        e.causes.collect({ it.message }) as Set == ["Directory '${task.outputDirs[0]}' specified for property 'outputDirs' is not a directory.".toString()] as Set
    }

    def validationActionFailsWhenParentOfOutputDirectoryIsAFile() {
        given:
        def task = expectTaskCreated(TaskWithOutputDir, new File(testDir, "subdir/output"))
        GFileUtils.touch(task.outputDir.getParentFile())

        when:
        task.execute()

        then:
        TaskValidationException e = thrown()
        e.message.contains("A problem was found with the configuration of $task")
        e.causes.collect({ it.message }) as Set == [
            "Cannot write to directory '${task.outputDir}' specified for property 'outputDir', as ancestor '${task.outputDir.parentFile}' is not a directory.".toString()] as Set
    }

    def validationActionFailsWhenParentOfOutputDirectoriesIsAFile() {
        given:
        def task = expectTaskCreated(TaskWithOutputDirs, Collections.singletonList(new File(testDir, "subdir/output")))
        GFileUtils.touch(task.outputDirs.get(0).getParentFile())

        when:
        task.execute()

        then:
        TaskValidationException e = thrown()
        e.message.contains("A problem was found with the configuration of $task")
        e.causes.collect({ it.message }) as Set == [
            "Cannot write to directory '${task.outputDirs[0]}' specified for property 'outputDirs', as ancestor '${task.outputDirs[0].parentFile}' is not a directory.".toString()] as Set
    }

    def registersSpecifiedOutputDirectory() {
        given:
        def task = expectTaskCreated(TaskWithOutputDir, missingDir)

        expect:
        task.outputs.files.files == [missingDir] as Set
    }

    def registersSpecifiedOutputDirectories() {
        given:
        def task = expectTaskCreated(TaskWithOutputDirs, Arrays.<File>asList(missingDir, missingDir2))

        expect:
        task.outputs.files.files == [missingDir, missingDir2] as Set
    }

    def doesNotRegisterOutputDirectoryWhenNoneSpecified() {
        given:
        def task = expectTaskCreated(TaskWithOutputDir, [null] as Object[])

        expect:
        task.outputs.files.files.isEmpty()
    }

    def doesNotRegisterOutputDirectoriesWhenNoneSpecified() {
        given:
        def task = expectTaskCreated(TaskWithOutputDirs, [null] as Object[])

        expect:
        task.outputs.files.files.isEmpty()

        when:
        task = expectTaskCreated(TaskWithOutputDirs, Collections.<File>emptyList())

        then:
        task.outputs.files.files.isEmpty()
    }

    def validationActionSucceedsWhenSpecifiedInputDirectoryIsDirectory() {
        given:
        def task = expectTaskCreated(TaskWithInputDir, existingDir)

        expect:
        task.execute()
    }

    def validationActionFailsWhenInputDirectoryNotSpecified() {
        given:
        def task = expectTaskCreated(TaskWithInputDir, [null] as Object[])

        when:
        task.execute()

        then:
        TaskValidationException e = thrown()
        e.message.contains("A problem was found with the configuration of $task")
        e.causes.collect({ it.message }) as Set == ["No value has been specified for property 'inputDir'."] as Set
    }

    def validationActionFailsWhenInputDirectoryDoesNotExist() {
        given:
        def task = expectTaskCreated(TaskWithInputDir, missingDir)

        when:
        task.execute()

        then:
        TaskValidationException e = thrown()
        e.message.contains("A problem was found with the configuration of $task")
        e.causes.collect({ it.message }) as Set == ["Directory '${task.inputDir}' specified for property 'inputDir' does not exist.".toString()] as Set
    }

    def validationActionFailsWhenInputDirectoryIsAFile() {
        given:
        def task = expectTaskCreated(TaskWithInputDir, existingFile)
        GFileUtils.touch(task.inputDir)

        when:
        task.execute()

        then:
        TaskValidationException e = thrown()
        e.message.contains("A problem was found with the configuration of $task")
        e.causes.collect({ it.message }) as Set == ["Directory '${task.inputDir}' specified for property 'inputDir' is not a directory.".toString()] as Set
    }

    def registersSpecifiedInputDirectory() {
        given:
        def task = expectTaskCreated(TaskWithInputDir, existingDir)
        File file = existingDir.file("some-file").createFile()

        expect:
        task.inputs.files.files == [file] as Set
    }

    def doesNotRegisterInputDirectoryWhenNoneSpecified() {
        given:
        def task = expectTaskCreated(TaskWithInputDir, [null] as Object[])

        expect:
        task.inputs.files.files.isEmpty()
    }

    def skipsTaskWhenInputDirectoryIsEmptyAndSkipWhenEmpty() {
        given:
        def task = expectTaskCreated(BrokenTaskWithInputDir, existingDir)

        expect:
        task.execute()
    }

    def validationActionSucceedsWhenInputValueSpecified() {
        given:
        def task = expectTaskCreated(TaskWithInput, "value")

        expect:
        task.execute()
    }

    def validationActionFailsWhenInputValueNotSpecified() {
        given:
        def task = expectTaskCreated(TaskWithInput, [null] as Object[])

        when:
        task.execute()

        then:
        TaskValidationException e = thrown()
        e.message.contains("A problem was found with the configuration of $task")
        e.causes.collect({ it.message }) as Set == ["No value has been specified for property 'inputValue'."] as Set
    }

    def registersSpecifiedInputValue() {
        given:
        def task = expectTaskCreated(TaskWithInput, "value")

        expect:
        task.inputs.properties["inputValue"] == "value"
    }

    @Issue("https://issues.gradle.org/Browse/GRADLE-2815")
    def registersSpecifiedBooleanInputValue() {
        given:
        def task = expectTaskCreated(TaskWithBooleanInput, true)

        expect:
        task.inputs.properties["inputValue"]
    }

    def validationActionSucceedsWhenPropertyMarkedWithOptionalAnnotationNotSpecified() {
        given:
        def task = expectTaskCreated(TaskWithOptionalInputFile.class)

        expect:
        task.execute()
    }

    def validatesNestedBeans() {
        given:
        def task = expectTaskCreated(TaskWithNestedBean, [null] as Object[])

        when:
        task.execute()

        then:
        TaskValidationException e = thrown()
        e.message.contains("A problem was found with the configuration of $task")
        e.causes.collect({ it.message }) as Set == ["No value has been specified for property 'bean.inputFile'."] as Set
    }

    def validatesNestedBeansWithPrivateType() {
        given:
        def task = expectTaskCreated(TaskWithNestedBeanWithPrivateClass, [existingFile, null] as Object[])

        when:
        task.execute()

        then:
        TaskValidationException e = thrown()
        e.message.contains("A problem was found with the configuration of $task")
        e.causes.collect({ it.message }) as Set == ["No value has been specified for property 'bean.inputFile'."] as Set
    }

    def registersInputPropertyForNestedBeanClass() {
        given:
        def task = expectTaskCreated(TaskWithNestedBean, [null] as Object[])

        expect:
        task.inputs.properties["bean.class"] == Bean.class.getName()
    }

    def registersInputPropertyForNestedBeanClassWithPrivateType() {
        given:
        def task = expectTaskCreated(TaskWithNestedBeanWithPrivateClass, [null, null] as Object[])

        expect:
        task.inputs.properties["bean.class"] == Bean2.class.getName()
    }

    def doesNotRegisterInputPropertyWhenNestedBeanIsNull() {
        given:
        def task = expectTaskCreated(TaskWithOptionalNestedBean)

        expect:
        task.inputs.properties["bean.class"] == null
    }

    def doesNotRegisterInputPropertyWhenNestedBeanWithPrivateTypeIsNull() {
        given:
        def task = expectTaskCreated(TaskWithOptionalNestedBeanWithPrivateType)

        expect:
        task.inputs.properties["bean.class"] == null
    }

    def validationFailsWhenNestedBeanIsNull() {
        given:
        def task = expectTaskCreated(TaskWithNestedBean, [null] as Object[])
        task.clearBean()

        when:
        task.execute()

        then:
        TaskValidationException e = thrown()
        e.message.contains("A problem was found with the configuration of $task")
        e.causes.collect({ it.message }) as Set == ["No value has been specified for property 'bean'."] as Set
    }

    def validationFailsWhenNestedBeanWithPrivateTypeIsNull() {
        given:
        def task = expectTaskCreated(TaskWithNestedBeanWithPrivateClass, [null, null] as Object[])
        task.clearBean()

        when:
        task.execute()

        then:
        TaskValidationException e = thrown()
        e.message.contains("A problem was found with the configuration of $task")
        e.causes.collect({ it.message }) as Set == ["No value has been specified for property 'bean'."] as Set
    }

    def validationSucceedsWhenNestedBeanIsNullAndMarkedOptional() {
        given:
        def task = expectTaskCreated(TaskWithOptionalNestedBean)

        expect:
        task.execute()
    }

    def validationSucceedsWhenNestedBeanWithPrivateTypeIsNullAndMarkedOptional() {
        given:
        def task = expectTaskCreated(TaskWithOptionalNestedBeanWithPrivateType)

        expect:
        task.execute()
    }

    def canAttachAnnotationToGroovyProperty() {
        given:
        def task = expectTaskCreated(InputFileTask)

        when:
        task.execute()

        then:
        TaskValidationException e = thrown()
        e.message.contains("A problem was found with the configuration of $task")
        e.causes.collect({ it.message }) as Set == ["No value has been specified for property 'srcFile'."] as Set
    }

    def validationFailureListsViolationsForAllProperties() {
        given:
        def task = expectTaskCreated(TaskWithMultipleProperties, [null] as Object[])

        when:
        task.execute()

        then:
        TaskValidationException e = thrown()
        e.message.contains("Some problems were found with the configuration of $task")
        e.causes.collect({ it.message }) as Set == [
            "No value has been specified for property 'outputFile'.",
            "No value has been specified for property 'bean.inputFile'."] as Set
    }

    def taskActionsRegisteredByProcessingAnnotationsAreNotConsideredCustom() {
        given:
        def task = expectTaskCreated(TestTask, [null] as Object[])

        expect:
        !task.hasCustomActions
    }

    def validationActionsAreNotConsideredCustom() {
        given:
        def task = expectTaskCreated(TaskWithInputFile, [null] as Object[])

        expect:
        !task.hasCustomActions
    }

    def directoryCreationActionsAreNotConsideredCustom() {
        given:
        def task = expectTaskCreated(TaskWithOutputDir, [null] as Object[])

        expect:
        !task.hasCustomActions
    }

    def fileCreationActionsAreNotConsideredCustom() {
        given:
        def task = expectTaskCreated(TaskWithOutputFile, [null] as Object[])

        expect:
        !task.hasCustomActions
    }

    def ignoresBridgeMethods() {
        given:
        def task = expectTaskCreated(TaskWithBridgeMethod)

        when:
        task.outputs.files.files

        then:
        task.traversedOutputsCount == 1
    }

    def propertyExtractionJavaBeanSpec() {
        given:
        def task = expectTaskCreated(TaskWithJavaBeanCornerCaseProperties, "c", "C", "d", "U", "a", "b")

        expect:
        task.inputs.properties["cCompiler"] != null
        task.inputs.properties["CFlags"] != null
        task.inputs.properties["dns"] != null
        task.inputs.properties["URL"] != null
        task.inputs.properties["a"] != null
        task.inputs.properties["b"] != null
    }

    def propertyValidationJavaBeanSpecCase() {
        given:
        def task = expectTaskCreated(TaskWithJavaBeanCornerCaseProperties, [null, null, null, null, "a", "b"] as Object[])

        when:
        task.execute()

        then:
        TaskValidationException e = thrown()
        e.message.contains("Some problems were found with the configuration of $task")
        e.causes.collect({ it.message }) as Set == [
            "No value has been specified for property 'cCompiler'.",
            "No value has been specified for property 'CFlags'.",
            "No value has been specified for property 'dns'.",
            "No value has been specified for property 'URL'."] as Set
    }

    def propertyValidationJavaBeanSpecSingleChar() {
        given:
        def task = expectTaskCreated(TaskWithJavaBeanCornerCaseProperties, ["c", "C", "d", "U", null, null] as Object[])

        when:
        task.execute()

        then:
        TaskValidationException e = thrown()
        e.message.contains("Some problems were found with the configuration of $task")
        e.causes.collect({ it.message }) as Set == [
            "No value has been specified for property 'a'.",
            "No value has been specified for property 'b'."] as Set
    }

    private TaskInternal expectTaskCreated(final Class type, final Object... params) {
        final Class decorated = project.getServices().get(ClassGenerator).generate(type)
        TaskInternal task = (TaskInternal) AbstractTask.injectIntoNewInstance(project, "task", type, new Callable<TaskInternal>() {
            public TaskInternal call() throws Exception {
                if (params.length > 0) {
                    return type.cast(decorated.constructors[0].newInstance(params))
                } else {
                    return decorated.newInstance()
                }
            }
        })
        return expectTaskCreated(task)
    }

    private TaskInternal expectTaskCreated(final TaskInternal task) {
        // We cannot just stub here as we want to return a different task each time.
        1 * delegate.createTask(args) >> task
        assert factory.createTask(args).is(task)
        return task
    }
}
