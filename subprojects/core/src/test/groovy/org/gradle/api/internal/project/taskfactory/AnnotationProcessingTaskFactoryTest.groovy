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
import org.gradle.api.internal.tasks.properties.DefaultPropertyMetadataStore
import org.gradle.api.internal.tasks.properties.DefaultPropertyWalker
import org.gradle.api.tasks.TaskPropertyTestUtils
import org.gradle.api.tasks.TaskValidationException
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GFileUtils
import spock.lang.Unroll

import java.util.concurrent.Callable

import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.*

class AnnotationProcessingTaskFactoryTest extends AbstractProjectBuilderSpec {
    private AnnotationProcessingTaskFactory factory
    private ITaskFactory delegate
    private TaskClassInfoStore taskClassInfoStore
    private classloaderHasher = Mock(ClassLoaderHierarchyHasher)
    def propertyWalker = new DefaultPropertyWalker(new DefaultPropertyMetadataStore([]), classloaderHasher)

    private Map args = new HashMap()

    @SuppressWarnings("GroovyUnusedDeclaration")
    private String inputValue = "value"
    private File testDir
    private File existingFile
    private File missingFile
    private TestFile existingDir
    private File missingDir
    private File missingDir2

    def setup() {
        delegate = Mock(ITaskFactory)
        taskClassInfoStore = new DefaultTaskClassInfoStore()
        factory = new AnnotationProcessingTaskFactory(taskClassInfoStore, delegate)
        testDir = temporaryFolder.testDirectory
        existingFile = testDir.file("file.txt").touch()
        missingFile = testDir.file("missing.txt")
        existingDir = testDir.file("dir").createDir()
        missingDir = testDir.file("missing-dir")
        missingDir2 = testDir.file("missing-dir2")
    }

    def doesNothingToTaskWithNoTaskActionAnnotations() {
        given:
        def task = expectTaskCreated(DefaultTask)

        expect:
        task.getActions().isEmpty()
    }

    def propagatesExceptionThrownByTaskActionMethod() {
        given:
        def action = Stub(Runnable, {
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

    def createsContextualActionForIncrementalTaskAction() {
        given:
        def action = Mock(Action)
        def task = expectTaskCreated(TaskWithIncrementalAction, action)

        when:
        execute(task)

        then:
        1 * action.execute(_ as IncrementalTaskInputs)
        0 * _
    }

    def createsContextualActionForOverriddenIncrementalTaskAction() {
        given:
        def action = Mock(Action)
        def superAction = Mock(Action)
        def task = expectTaskCreated(TaskWithOverriddenIncrementalAction, action, superAction)

        when:
        execute(task)

        then:
        1 * action.execute(_ as IncrementalTaskInputs)
        0 * _
    }

    def cachesClassMetaInfo() {
        given:
        def taskInfo1 = taskClassInfoStore.getTaskClassInfo(TaskWithInputFile)
        def taskInfo2 = taskClassInfoStore.getTaskClassInfo(TaskWithInputFile)

        expect:
        taskInfo1.is(taskInfo2)
    }

    @Unroll
    def "fails for #type.simpleName"() {
        when:
        expectTaskCreated(type)

        then:
        def e = thrown Exception
        e.cause instanceof GradleException
        e.cause.message == failureMessage

        where:
        type                               | failureMessage
        TaskWithMultipleIncrementalActions | "Cannot have multiple @TaskAction methods accepting an IncrementalTaskInputs parameter."
        TaskWithStaticMethod               | "Cannot use @TaskAction annotation on static method TaskWithStaticMethod.doStuff()."
        TaskWithMultiParamAction           | "Cannot use @TaskAction annotation on method TaskWithMultiParamAction.doStuff() as this method takes multiple parameters."
        TaskWithSingleParamAction          | "Cannot use @TaskAction annotation on method TaskWithSingleParamAction.doStuff() because int is not a valid parameter to an action method."
        TaskWithOverloadedActions          | "Cannot use @TaskAction annotation on multiple overloads of method TaskWithOverloadedActions.doStuff()"
    }

    @Unroll
    def "works for #type.simpleName"() {
        given:
        def action = Mock(Runnable)
        def task = expectTaskCreated(type, action)

        when:
        execute(task)

        then:
        times * action.run()

        where:
        type                     | times
        TestTask                 | 1
        TaskWithInheritedMethod  | 1
        TaskWithOverriddenMethod | 1
        TaskWithProtectedMethod  | 1
        TaskWithMultipleMethods  | 3
    }

    @Unroll
    def "validation succeeds when #property #value on #type.simpleName"() {
        given:
        def task = expectTaskCreated(type, this[value])

        expect:
        execute(task)

        where:
        type                | property       | value
        TaskWithInputFile   | 'input-file'   | 'existingFile'
        TaskWithOutputFile  | 'output-file'  | 'existingFile'
        TaskWithOutputDir   | 'output-dir'   | 'existingDir'
        TaskWithInputDir    | 'input-dir'    | 'existingDir'
        TaskWithInput       | 'input'        | 'inputValue'
    }

    @Unroll
    def "validation succeeds when list #property contains #value on #type.simpleName"() {
        given:
        def task = expectTaskCreated(type, [this[value]] as List)

        expect:
        execute(task)

        where:
        type                | property       | value
        TaskWithInputFiles  | 'input-files'  | 'testDir'
        TaskWithOutputFiles | 'output-files' | 'existingFile'
        TaskWithOutputDirs  | 'output-dirs'  | 'existingDir'
    }

    @Unroll
    def "validation succeeds when optional #property is omitted on #type.simpleName"() {
        given:
        def task = expectTaskCreated(type)

        expect:
        execute(task)

        where:
        type                                      | property
        TaskWithOptionalInputFile                 | 'input-file'
        TaskWithOptionalOutputFile                | 'output-file'
        TaskWithOptionalOutputFiles               | 'output-files'
        TaskWithOptionalOutputDir                 | 'output-dir'
        TaskWithOptionalOutputDirs                | 'output-dirs'
        TaskWithOptionalNestedBean                | 'bean'
        TaskWithOptionalNestedBeanWithPrivateType | 'private-bean'
    }

    def validationActionSucceedsWhenSpecifiedOutputFileDoesNotExist() {
        given:
        def task = expectTaskCreated(TaskWithOutputFile, new File(testDir, "subdir/output.txt"))

        when:
        execute(task)

        then:
        new File(testDir, "subdir").isDirectory()
    }

    def validationActionSucceedsWhenSpecifiedOutputFilesDoesNotExist() {
        given:
        def task = expectTaskCreated(TaskWithOutputFiles, [new File(testDir, "subdir/output.txt"), new File(testDir, "subdir2/output.txt")] as List)

        when:
        execute(task)

        then:
        new File(testDir, "subdir").isDirectory()
        new File(testDir, "subdir2").isDirectory()
    }

    def validationActionSucceedsWhenSpecifiedOutputDirectoryDoesNotExist() {
        given:
        def task = expectTaskCreated(TaskWithOutputDir, missingDir)

        when:
        execute(task)

        then:
        task.outputDir.isDirectory()
    }

    def validationActionSucceedsWhenSpecifiedOutputDirectoriesDoesNotExist() {
        given:
        def task = expectTaskCreated(TaskWithOutputDirs, [missingDir] as List)

        when:
        execute(task)

        then:
        task.outputDirs.get(0).isDirectory()
    }

    @Unroll
    def "validation fails for unspecified #property for #type.simpleName"() {
        given:
        def task = expectTaskCreated(type, [null] as Object[])

        when:
        execute(task)

        then:
        TaskValidationException e = thrown()
        validateException(task, e, "No value has been specified for property '$property'.")

        where:
        type                | property
        TaskWithInputFile   | 'inputFile'
        TaskWithOutputFile  | 'outputFile'
        TaskWithOutputFiles | 'outputFiles'
        TaskWithInputFiles  | 'input'
        TaskWithOutputDir   | 'outputDir'
        TaskWithOutputDirs  | 'outputDirs'
        TaskWithInputDir    | 'inputDir'
        TaskWithInput       | 'inputValue'
        TaskWithNestedBean  | 'bean.inputFile'
    }

    def validationActionFailsWhenSpecifiedOutputFileIsADirectory() {
        given:
        def task = expectTaskCreated(TaskWithOutputFile, existingDir)

        when:
        execute(task)

        then:
        TaskValidationException e = thrown()
        validateException(task, e, "Cannot write to file '$task.outputFile' specified for property 'outputFile' as it is a directory.")
    }

    def validationActionFailsWhenSpecifiedOutputFilesIsADirectory() {
        given:
        def task = expectTaskCreated(TaskWithOutputFiles, [existingDir] as List)

        when:
        execute(task)

        then:
        TaskValidationException e = thrown()
        validateException(task, e, "Cannot write to file '${task.outputFiles[0]}' specified for property 'outputFiles' as it is a directory.")
    }

    def validationActionFailsWhenSpecifiedOutputFileParentIsAFile() {
        given:
        def task = expectTaskCreated(TaskWithOutputFile, new File(testDir, "subdir/output.txt"))
        GFileUtils.touch(task.outputFile.getParentFile())

        when:
        execute(task)

        then:
        TaskValidationException e = thrown()
        validateException(task, e, "Cannot write to file '$task.outputFile' specified for property 'outputFile', as ancestor '$task.outputFile.parentFile' is not a directory.")
    }

    def validationActionFailsWhenSpecifiedOutputFilesParentIsAFile() {
        given:
        def task = expectTaskCreated(TaskWithOutputFiles, [new File(testDir, "subdir/output.txt")] as List)
        GFileUtils.touch(task.outputFiles.get(0).getParentFile())

        when:
        execute(task)

        then:
        TaskValidationException e = thrown()
        validateException(task, e, "Cannot write to file '${task.outputFiles[0]}' specified for property 'outputFiles', as ancestor '${task.outputFiles[0].parentFile}' is not a directory.")
    }

    def validationActionFailsWhenOutputDirectoryIsAFile() {
        given:
        def task = expectTaskCreated(TaskWithOutputDir, existingFile)

        when:
        execute(task)

        then:
        TaskValidationException e = thrown()
        validateException(task, e, "Directory '$task.outputDir' specified for property 'outputDir' is not a directory.")
    }

    def validationActionFailsWhenOutputDirectoriesIsAFile() {
        given:
        def task = expectTaskCreated(TaskWithOutputDirs, [existingFile] as List)

        when:
        execute(task)

        then:
        TaskValidationException e = thrown()
        validateException(task, e, "Directory '${task.outputDirs[0]}' specified for property 'outputDirs' is not a directory.")
    }

    def validationActionFailsWhenParentOfOutputDirectoryIsAFile() {
        given:
        def task = expectTaskCreated(TaskWithOutputDir, new File(testDir, "subdir/output"))
        GFileUtils.touch(task.outputDir.getParentFile())

        when:
        execute(task)

        then:
        TaskValidationException e = thrown()
        validateException(task, e, "Cannot write to directory '$task.outputDir' specified for property 'outputDir', as ancestor '$task.outputDir.parentFile' is not a directory.")
    }

    def validationActionFailsWhenParentOfOutputDirectoriesIsAFile() {
        given:
        def task = expectTaskCreated(TaskWithOutputDirs, [new File(testDir, "subdir/output")])
        GFileUtils.touch(task.outputDirs.get(0).getParentFile())

        when:
        execute(task)

        then:
        TaskValidationException e = thrown()
        validateException(task, e, "Cannot write to directory '${task.outputDirs[0]}' specified for property 'outputDirs', as ancestor '${task.outputDirs[0].parentFile}' is not a directory.")
    }

    def validationActionFailsWhenInputDirectoryDoesNotExist() {
        given:
        def task = expectTaskCreated(TaskWithInputDir, missingDir)

        when:
        execute(task)

        then:
        TaskValidationException e = thrown()
        validateException(task, e, "Directory '$task.inputDir' specified for property 'inputDir' does not exist.")
    }

    def validationActionFailsWhenInputDirectoryIsAFile() {
        given:
        def task = expectTaskCreated(TaskWithInputDir, existingFile)
        GFileUtils.touch(task.inputDir)

        when:
        execute(task)

        then:
        TaskValidationException e = thrown()
        validateException(task, e, "Directory '$task.inputDir' specified for property 'inputDir' is not a directory.")
    }

    def validatesNestedBeansWithPrivateType() {
        given:
        def task = expectTaskCreated(TaskWithNestedBeanWithPrivateClass, [existingFile, null] as Object[])

        when:
        execute(task)

        then:
        TaskValidationException e = thrown()
        validateException(task, e, "No value has been specified for property 'bean.inputFile'.")
    }

    def validationFailsWhenNestedBeanIsNull() {
        given:
        def task = expectTaskCreated(TaskWithNestedBean, [null] as Object[])
        task.clearBean()

        when:
        execute(task)

        then:
        TaskValidationException e = thrown()
        validateException(task, e, "No value has been specified for property 'bean'.")
    }

    def validationFailsWhenNestedBeanWithPrivateTypeIsNull() {
        given:
        def task = expectTaskCreated(TaskWithNestedBeanWithPrivateClass, [null, null] as Object[])
        task.clearBean()

        when:
        execute(task)

        then:
        TaskValidationException e = thrown()
        validateException(task, e, "No value has been specified for property 'bean'.")
    }

    def canAttachAnnotationToGroovyProperty() {
        given:
        def task = expectTaskCreated(InputFileTask)

        when:
        execute(task)

        then:
        TaskValidationException e = thrown()
        validateException(task, e, "No value has been specified for property 'srcFile'.")
    }

    def validationFailureListsViolationsForAllProperties() {
        given:
        def task = expectTaskCreated(TaskWithMultipleProperties, [null] as Object[])

        when:
        execute(task)

        then:
        TaskValidationException e = thrown()
        validateException(task, e,
            "No value has been specified for property 'outputFile'.",
            "No value has been specified for property 'bean.inputFile'.")
    }

    def propertyValidationJavaBeanSpecCase() {
        given:
        def task = expectTaskCreated(TaskWithJavaBeanCornerCaseProperties, [null, null, null, null, "a", "b"] as Object[])

        when:
        execute(task)

        then:
        TaskValidationException e = thrown()
        validateException(task, e,
            "No value has been specified for property 'cCompiler'.",
            "No value has been specified for property 'CFlags'.",
            "No value has been specified for property 'dns'.",
            "No value has been specified for property 'URL'.")
    }

    def propertyValidationJavaBeanSpecSingleChar() {
        given:
        def task = expectTaskCreated(TaskWithJavaBeanCornerCaseProperties, ["c", "C", "d", "U", null, null] as Object[])

        when:
        execute(task)

        then:
        TaskValidationException e = thrown()
        validateException(task, e,
            "No value has been specified for property 'a'.",
            "No value has been specified for property 'b'.")
    }

    @Unroll
    def "registers specified #target for #value on #type.simpleName"() {
        given:
        def task = expectTaskCreated(type, this[value])

        expect:
        task[target].files.files == [this[value]] as Set

        where:
        type               | target    | value
        TaskWithInputFile  | 'inputs'  | 'existingFile'
        TaskWithOutputFile | 'outputs' | 'existingFile'
        TaskWithOutputDir  | 'outputs' | 'missingDir'
    }

    def "registers specified list of inputs on TaskWithInputFiles"() {
        given:
        def values = ['testDir', 'missingFile'].collect({ this[it] })
        def task = expectTaskCreated(TaskWithInputFiles, values as List)

        expect:
        task.inputs.files.files == values as Set
    }

    @Unroll
    def "registers specified list of outputs for #value on #type.simpleName"() {
        given:
        def values = value.collect({ this[it] })
        def task = expectTaskCreated(type, values as List)

        expect:
        task.outputs.files.files == values as Set

        where:
        type                | value
        TaskWithOutputFiles | ['existingFile']
        TaskWithOutputDirs  | ['missingDir', 'missingDir2']
    }

    def registersSpecifiedInputDirectory() {
        given:
        def task = expectTaskCreated(TaskWithInputDir, existingDir)
        File file = existingDir.file("some-file").createFile()

        expect:
        task.inputs.files.files == [file] as Set
    }

    @Unroll
    def "registers input property for #prop on #type.simpleName"() {
        given:
        def task = (value == null) ? expectTaskCreated(type) : expectTaskCreated(type, value)

        expect:
        inputProperties(task)[prop] == expected

        where:
        type                                      | prop         | value                    | expected
        TaskWithNestedBean                        | "bean.class" | [null] as Object[]       | Bean.class.getName()
        TaskWithNestedBeanWithPrivateClass        | "bean.class" | [null, null] as Object[] | Bean2.class.getName()
        TaskWithOptionalNestedBean                | "bean.class" | null                     | null
        TaskWithOptionalNestedBeanWithPrivateType | "bean.class" | null                     | null
        TaskWithInput                             | "inputValue" | "value"                  | "value"
        TaskWithBooleanInput                      | "inputValue" | true                     | true           // https://issues.gradle.org/Browse/GRADLE-2815
    }

    @Unroll
    def "does not register #target for #type when not specified"() {
        given:
        def task = expectTaskCreated(type, value)

        expect:
        task[target].files.files.isEmpty()

        where:
        type                | target    | value
        TaskWithInputFile   | 'inputs'  | [null] as Object[]
        TaskWithOutputFile  | 'outputs' | [null] as Object[]
        TaskWithOutputFiles | 'outputs' | [null] as Object[]
        TaskWithOutputFiles | 'outputs' | [] as List
        TaskWithInputFiles  | 'inputs'  | [null] as Object[]
        TaskWithOutputDir   | 'outputs' | [null] as Object[]
        TaskWithOutputDirs  | 'outputs' | [null] as Object[]
        TaskWithOutputDirs  | 'outputs' | [] as List
        TaskWithInputDir    | 'inputs'  | [null] as Object[]
    }

    def skipsTaskWhenInputDirectoryIsEmptyAndSkipWhenEmpty() {
        given:
        def task = expectTaskCreated(BrokenTaskWithInputDir, existingDir)

        expect:
        execute(task)
    }

    def skipsTaskWhenInputFileCollectionIsEmpty() {
        given:
        def inputFiles = new ArrayList<File>()
        BrokenTaskWithInputFiles task = expectTaskCreated(BrokenTaskWithInputFiles, inputFiles)

        expect:
        execute(task)
    }

    @Unroll
    def "#description are not custom actions"() {
        given:
        def task = expectTaskCreated(type, [null] as Object[])

        expect:
        !task.hasCustomActions

        where:
        description                                         | type
        "task actions registered by processing annotations" | TestTask
        "validation actions"                                | TaskWithInputFile
        "directory creation actions"                        | TaskWithOutputDir
        "file creation actions"                             | TaskWithOutputFile
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
        def properties = inputProperties(task)

        expect:
        properties["cCompiler"] != null
        properties["CFlags"] != null
        properties["dns"] != null
        properties["URL"] != null
        properties["a"] != null
        properties["b"] != null
    }

    private TaskInternal expectTaskCreated(final Class type, final Object... params) {
        final Class decorated = project.getServices().get(ClassGenerator).generate(type)
        TaskInternal task = (TaskInternal) AbstractTask.injectIntoNewInstance(project, "task", type, new Callable<TaskInternal>() {
            TaskInternal call() throws Exception {
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

    private static void validateException(TaskInternal task, TaskValidationException exception, String... causes) {
        def expectedMessage = causes.length > 1 ? "Some problems were found with the configuration of $task" : "A problem was found with the configuration of $task"
        assert exception.message.contains(expectedMessage)
        assert exception.causes.collect({ it.message }) as Set == causes as Set
    }

    private Map<String, Object> inputProperties(TaskInternal task) {
        TaskPropertyTestUtils.getProperties(task, propertyWalker)
    }
}
