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
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.internal.AbstractTask
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.tasks.properties.DefaultPropertyWalker
import org.gradle.api.internal.tasks.properties.DefaultTaskProperties
import org.gradle.api.internal.tasks.properties.DefaultTypeMetadataStore
import org.gradle.api.internal.tasks.properties.ModifierAnnotationCategory
import org.gradle.api.internal.tasks.properties.annotations.PropertyAnnotationHandler
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskPropertyTestUtils
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.execution.WorkValidationException
import org.gradle.internal.execution.WorkValidationExceptionChecker
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.JavaReflectionUtil
import org.gradle.internal.reflect.annotations.impl.DefaultTypeAnnotationMetadataStore
import org.gradle.internal.reflect.problems.ValidationProblemId
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.internal.reflect.validation.ValidationTestFor
import org.gradle.internal.service.ServiceRegistryBuilder
import org.gradle.internal.service.scopes.ExecutionGlobalServices
import org.gradle.internal.snapshot.impl.ImplementationValue
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.work.InputChanges

import java.util.concurrent.Callable

import static org.apache.commons.io.FileUtils.touch
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.Bean
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.Bean2
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.BrokenTaskWithInputDir
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.BrokenTaskWithInputFiles
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.NamedBean
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskOverridingDeprecatedIncrementalChangesActions
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskOverridingInputChangesActions
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskUsingInputChanges
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithBooleanInput
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithBridgeMethod
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithDestroyable
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithIncrementalAction
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithInheritedMethod
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithInput
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithInputDir
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithInputFile
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithInputFiles
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithJavaBeanCornerCaseProperties
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithLocalState
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithMixedMultipleIncrementalActions
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithMultiParamAction
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithMultipleIncrementalActions
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithMultipleInputChangesActions
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithMultipleMethods
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithMultipleProperties
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithNestedBean
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithNestedBeanWithPrivateClass
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithNestedIterable
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithNestedObject
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithOptionalInputFile
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithOptionalNestedBean
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithOptionalNestedBeanWithPrivateType
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithOptionalOutputDir
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithOptionalOutputDirs
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithOptionalOutputFile
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithOptionalOutputFiles
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithOutputDir
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithOutputDirs
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithOutputFile
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithOutputFiles
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithOverloadedActions
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithOverloadedDeprecatedIncrementalAndInputChangesActions
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithOverloadedIncrementalAndInputChangesActions
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithOverloadedInputChangesActions
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithOverriddenIncrementalAction
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithOverriddenInputChangesAction
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithOverriddenMethod
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithProtectedMethod
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithSingleParamAction
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TaskWithStaticMethod
import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.TestTask
import static org.gradle.internal.service.scopes.ExecutionGlobalServices.IGNORED_METHOD_ANNOTATIONS
import static org.gradle.internal.service.scopes.ExecutionGlobalServices.PROPERTY_TYPE_ANNOTATIONS

class AnnotationProcessingTaskFactoryTest extends AbstractProjectBuilderSpec implements ValidationMessageChecker {
    private AnnotationProcessingTaskFactory factory
    private ITaskFactory delegate
    def services = ServiceRegistryBuilder.builder().provider(new ExecutionGlobalServices()).build()
    def taskClassInfoStore = new DefaultTaskClassInfoStore(new TestCrossBuildInMemoryCacheFactory())
    def cacheFactory = new TestCrossBuildInMemoryCacheFactory()
    def typeAnnotationMetadataStore = new DefaultTypeAnnotationMetadataStore(
        [],
        ModifierAnnotationCategory.asMap(PROPERTY_TYPE_ANNOTATIONS),
        ["java", "groovy"],
        [],
        [Object, GroovyObject],
        [ConfigurableFileCollection, Property],
        IGNORED_METHOD_ANNOTATIONS,
        { false },
        cacheFactory
    )
    def propertyWalker = new DefaultPropertyWalker(new DefaultTypeMetadataStore([], services.getAll(PropertyAnnotationHandler), [Optional, SkipWhenEmpty], typeAnnotationMetadataStore, cacheFactory))

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
        factory = new AnnotationProcessingTaskFactory(DirectInstantiator.INSTANCE, taskClassInfoStore, delegate)
        testDir = temporaryFolder.testDirectory
        existingFile = testDir.file("file.txt").touch()
        missingFile = testDir.file("missing.txt")
        existingDir = testDir.file("dir").createDir()
        missingDir = testDir.file("missing-dir")
        missingDir2 = testDir.file("missing-dir2")
    }

    FileResolver getFileResolver() {
        return project.fileResolver
    }

    FileCollectionFactory getFileCollectionFactory() {
        return project.services.get(FileCollectionFactory)
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

    @SuppressWarnings("GrDeprecatedAPIUsage")
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

    def createsContextualActionForInputChangesTaskAction() {
        given:
        def action = Mock(Action)
        def task = expectTaskCreated(TaskUsingInputChanges, action)

        when:
        execute(task)

        then:
        1 * action.execute(_ as InputChanges)
        0 * _
    }

    @SuppressWarnings("GrDeprecatedAPIUsage")
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

    @SuppressWarnings("GrDeprecatedAPIUsage")
    def "uses IncrementalTaskInputsMethod when it is overriden"() {
        given:
        def changesAction = Mock(Action)
        def task = expectTaskCreated(TaskOverridingDeprecatedIncrementalChangesActions, changesAction)

        when:
        execute(task)

        then:
        1 * changesAction.execute(_ as InputChanges)
        1 * changesAction.execute(_ as IncrementalTaskInputs)
        0 * _
    }

    def "uses InputChanges method when two actions are present on the same class"() {
        given:
        def changesAction = Mock(Action)
        def task = expectTaskCreated(TaskWithOverloadedDeprecatedIncrementalAndInputChangesActions, changesAction)

        when:
        execute(task)

        then:
        1 * changesAction.execute(_ as InputChanges)
        0 * _
    }

    def "uses overridden InputChanges when two actions are present on the base class"() {
        given:
        def changesAction = Mock(Action)
        def task = expectTaskCreated(TaskOverridingInputChangesActions, changesAction)

        when:
        execute(task)

        then:
        1 * changesAction.execute(_ as InputChanges)
        0 * _
    }

    def createsContextualActionForOverriddenInputChangesTaskAction() {
        given:
        def action = Mock(Action)
        def superAction = Mock(Action)
        def task = expectTaskCreated(TaskWithOverriddenInputChangesAction, action, superAction)

        when:
        execute(task)

        then:
        1 * action.execute(_ as InputChanges)
        0 * _
    }

    def cachesClassMetaInfo() {
        given:
        def taskInfo1 = taskClassInfoStore.getTaskClassInfo(TaskWithInputFile)
        def taskInfo2 = taskClassInfoStore.getTaskClassInfo(TaskWithInputFile)

        expect:
        taskInfo1.is(taskInfo2)
    }

    def "fails for #type.simpleName"() {
        when:
        expectTaskCreated(type)

        then:
        def e = thrown GradleException
        e.message == failureMessage

        where:
        type                                                | failureMessage
        TaskWithMultipleIncrementalActions                  | "Cannot have multiple @TaskAction methods accepting an InputChanges or IncrementalTaskInputs parameter."
        TaskWithStaticMethod                                | "Cannot use @TaskAction annotation on static method TaskWithStaticMethod.doStuff()."
        TaskWithMultiParamAction                            | "Cannot use @TaskAction annotation on method TaskWithMultiParamAction.doStuff() as this method takes multiple parameters."
        TaskWithSingleParamAction                           | "Cannot use @TaskAction annotation on method TaskWithSingleParamAction.doStuff() because int is not a valid parameter to an action method."
        TaskWithOverloadedActions                           | "Cannot use @TaskAction annotation on multiple overloads of method TaskWithOverloadedActions.doStuff()"
        TaskWithOverloadedInputChangesActions               | "Cannot use @TaskAction annotation on multiple overloads of method TaskWithOverloadedInputChangesActions.doStuff()"
        TaskWithOverloadedIncrementalAndInputChangesActions | "Cannot use @TaskAction annotation on multiple overloads of method TaskWithOverloadedIncrementalAndInputChangesActions.doStuff()"
        TaskWithMultipleInputChangesActions                 | "Cannot have multiple @TaskAction methods accepting an InputChanges or IncrementalTaskInputs parameter."
        TaskWithMixedMultipleIncrementalActions             | "Cannot have multiple @TaskAction methods accepting an InputChanges or IncrementalTaskInputs parameter."
    }

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

    def "validation succeeds when #property #value on #type.simpleName"() {
        given:
        def task = expectTaskCreated(type, this[value])

        expect:
        execute(task)

        where:
        type               | property      | value
        TaskWithInputFile  | 'input-file'  | 'existingFile'
        TaskWithOutputFile | 'output-file' | 'existingFile'
        TaskWithOutputDir  | 'output-dir'  | 'existingDir'
        TaskWithInputDir   | 'input-dir'   | 'existingDir'
        TaskWithInput      | 'input'       | 'inputValue'
    }

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

    def "validation succeeds when optional #property is omitted on #type.simpleName"() {
        given:
        def task = expectTaskCreated(type, arguments as Object[])

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
        arguments = type == TaskWithOptionalNestedBean ? [null] : []
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

    @ValidationTestFor(
        ValidationProblemId.VALUE_NOT_SET
    )
    def "validation fails for unspecified #propName for #type.simpleName"() {
        given:
        def task = expectTaskCreated(type, [null] as Object[])

        when:
        execute(task)

        then:
        def e = thrown WorkValidationException
        String expectedMessage = missingValueMessage { property(propName).includeLink() }
        validateException(task, e, expectedMessage)

        where:
        type                | propName
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

    @ValidationTestFor(
        ValidationProblemId.CANNOT_WRITE_OUTPUT
    )
    def validationActionFailsWhenSpecifiedOutputFileIsADirectory() {
        given:
        def task = expectTaskCreated(TaskWithOutputFile, existingDir)

        when:
        execute(task)

        then:
        def e = thrown WorkValidationException
        validateException(task, e, cannotWriteFileToDirectory {
            property('outputFile')
                .file(task.outputFile)
                .isNotFile()
                .includeLink()
        })
    }

    @ValidationTestFor(
        ValidationProblemId.CANNOT_WRITE_OUTPUT
    )
    def validationActionFailsWhenSpecifiedOutputFilesIsADirectory() {
        given:
        def task = expectTaskCreated(TaskWithOutputFiles, [existingDir] as List)

        when:
        execute(task)

        then:
        def e = thrown WorkValidationException
        validateException(task, e, cannotWriteFileToDirectory {
            property('outputFiles')
                .file(task.outputFiles[0])
                .isNotFile()
                .includeLink()
        })
    }

    @ValidationTestFor(
        ValidationProblemId.CANNOT_WRITE_OUTPUT
    )
    def validationActionFailsWhenSpecifiedOutputFileParentIsAFile() {
        given:
        def task = expectTaskCreated(TaskWithOutputFile, new File(testDir, "subdir/output.txt"))
        touch(task.outputFile.getParentFile())

        when:
        execute(task)

        then:
        def e = thrown WorkValidationException
        validateException(task, e, cannotCreateParentDirectories {
            property('outputFile')
                .file(task.outputFile)
                .ancestorIsNotDirectory(task.outputFile.parentFile)
                .includeLink()
        })
    }

    @ValidationTestFor(
        ValidationProblemId.CANNOT_WRITE_OUTPUT
    )
    def validationActionFailsWhenSpecifiedOutputFilesParentIsAFile() {
        given:
        def task = expectTaskCreated(TaskWithOutputFiles, [new File(testDir, "subdir/output.txt")] as List)
        touch(task.outputFiles.get(0).getParentFile())

        when:
        execute(task)

        then:
        def e = thrown WorkValidationException
        validateException(task, e, cannotCreateParentDirectories {
            property('outputFiles')
                .file(task.outputFiles[0])
                .ancestorIsNotDirectory(task.outputFiles[0].parentFile)
                .includeLink()
        })
    }

    @ValidationTestFor(
        ValidationProblemId.CANNOT_WRITE_OUTPUT
    )
    def validationActionFailsWhenOutputDirectoryIsAFile() {
        given:
        def task = expectTaskCreated(TaskWithOutputDir, existingFile)

        when:
        execute(task)

        then:
        def e = thrown WorkValidationException
        validateException(task, e, cannotWriteToDir {
            property('outputDir')
                .dir(task.outputDir)
                .isNotDirectory()
                .includeLink()
        })
    }

    @ValidationTestFor(
        ValidationProblemId.CANNOT_WRITE_OUTPUT
    )
    def validationActionFailsWhenOutputDirectoriesIsAFile() {
        given:
        def task = expectTaskCreated(TaskWithOutputDirs, [existingFile] as List)

        when:
        execute(task)

        then:
        def e = thrown WorkValidationException
        validateException(task, e, cannotWriteToDir {
            property('outputDirs')
                .dir(task.outputDirs[0])
                .isNotDirectory()
                .includeLink()
        })
    }

    @ValidationTestFor(
        ValidationProblemId.CANNOT_WRITE_OUTPUT
    )
    def validationActionFailsWhenParentOfOutputDirectoryIsAFile() {
        given:
        def task = expectTaskCreated(TaskWithOutputDir, new File(testDir, "subdir/output"))
        touch(task.outputDir.getParentFile())

        when:
        execute(task)

        then:
        def e = thrown WorkValidationException
        validateException(task, e, cannotWriteToDir {
            property('outputDir')
                .dir(task.outputDir)
                .ancestorIsNotDirectory(task.outputDir.parentFile)
                .includeLink()
        })
    }

    @ValidationTestFor(
        ValidationProblemId.CANNOT_WRITE_OUTPUT
    )
    def validationActionFailsWhenParentOfOutputDirectoriesIsAFile() {
        given:
        def task = expectTaskCreated(TaskWithOutputDirs, [new File(testDir, "subdir/output")])
        touch(task.outputDirs.get(0).getParentFile())

        when:
        execute(task)

        then:
        def e = thrown WorkValidationException
        validateException(task, e, cannotWriteToDir {
            property('outputDirs')
                .dir(task.outputDirs[0])
                .ancestorIsNotDirectory(task.outputDirs[0].parentFile)
                .includeLink()
        })
    }

    @ValidationTestFor(
        ValidationProblemId.INPUT_FILE_DOES_NOT_EXIST
    )
    def validationActionFailsWhenInputDirectoryDoesNotExist() {
        given:
        def task = expectTaskCreated(TaskWithInputDir, missingDir)

        when:
        execute(task)

        then:
        def e = thrown WorkValidationException
        validateException(task, e, inputDoesNotExist {
            property('inputDir')
                .dir(missingDir)
                .includeLink()
        })
    }

    @ValidationTestFor(
        ValidationProblemId.UNEXPECTED_INPUT_FILE_TYPE
    )
    def validationActionFailsWhenInputDirectoryIsAFile() {
        given:
        def task = expectTaskCreated(TaskWithInputDir, existingFile)
        touch(task.inputDir)

        when:
        execute(task)

        then:
        def e = thrown WorkValidationException
        validateException(task, e, unexpectedInputType {
            property('inputDir')
                .dir(task.inputDir)
                .includeLink()
        })
    }

    @ValidationTestFor([
        ValidationProblemId.VALUE_NOT_SET,
        ValidationProblemId.IGNORED_ANNOTATIONS_ON_FIELD
    ])
    def validatesNestedBeansWithPrivateType() {
        given:
        def task = expectTaskCreated(TaskWithNestedBeanWithPrivateClass, [existingFile, null] as Object[])

        when:
        execute(task)

        then:
        def e = thrown WorkValidationException
        validateException(task, false, e,
            missingValueMessage { type(TaskWithNestedBeanWithPrivateClass.canonicalName).property('bean.inputFile').includeLink() },
            ignoredAnnotationOnField { type(Bean2.canonicalName).property('inputFile2').annotatedWith('InputFile').includeLink() })
    }

    @ValidationTestFor(
        ValidationProblemId.VALUE_NOT_SET
    )
    def validationFailsWhenNestedBeanIsNull() {
        given:
        def task = expectTaskCreated(TaskWithNestedBean, [null] as Object[])
        task.clearBean()

        when:
        execute(task)

        then:
        def e = thrown WorkValidationException
        validateException(task, e, missingValueMessage { property('bean').includeLink() })
    }

    @ValidationTestFor(
        ValidationProblemId.VALUE_NOT_SET
    )
    def validationFailsWhenNestedBeanWithPrivateTypeIsNull() {
        given:
        def task = expectTaskCreated(TaskWithNestedBeanWithPrivateClass, [null, null] as Object[])
        task.clearBean()

        when:
        execute(task)

        then:
        def e = thrown WorkValidationException
        validateException(task, e, missingValueMessage { property('bean').includeLink() })
    }

    @ValidationTestFor(
        ValidationProblemId.VALUE_NOT_SET
    )
    def canAttachAnnotationToGroovyProperty() {
        given:
        def task = expectTaskCreated(InputFileTask)

        when:
        execute(task)

        then:
        def e = thrown WorkValidationException
        validateException(task, e, missingValueMessage { property('srcFile').includeLink() })
    }

    def validationFailureListsViolationsForAllProperties() {
        given:
        def task = expectTaskCreated(TaskWithMultipleProperties, [null] as Object[])

        when:
        execute(task)

        then:
        def e = thrown WorkValidationException
        validateException(task, e,
            missingValueMessage { property('outputFile').includeLink() },
            missingValueMessage { property('bean.inputFile').includeLink() })
    }

    @ValidationTestFor(
        ValidationProblemId.VALUE_NOT_SET
    )
    def propertyValidationJavaBeanSpecCase() {
        given:
        def task = expectTaskCreated(TaskWithJavaBeanCornerCaseProperties, [null, null, null, null, "a", "b"] as Object[])

        when:
        execute(task)

        then:
        def e = thrown WorkValidationException
        validateException(task, e,
            missingValueMessage { property('cCompiler').includeLink() },
            missingValueMessage { property('CFlags').includeLink() },
            missingValueMessage { property('dns').includeLink() },
            missingValueMessage { property('URL').includeLink() })
    }

    @ValidationTestFor(
        ValidationProblemId.VALUE_NOT_SET
    )
    def propertyValidationJavaBeanSpecSingleChar() {
        given:
        def task = expectTaskCreated(TaskWithJavaBeanCornerCaseProperties, ["c", "C", "d", "U", null, null] as Object[])

        when:
        execute(task)

        then:
        def e = thrown WorkValidationException
        validateException(task, e,
            missingValueMessage { property('a').includeLink() },
            missingValueMessage { property('b').includeLink() })
    }

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

    def "registers input property for #prop on #type.simpleName"() {
        given:
        def task = expectTaskCreated(type, value as Object[])

        expect:
        inputProperties(task)[prop] == expected

        where:
        type                                      | prop                 | value                            | expected
        TaskWithNestedIterable                    | 'beans.name$0.value' | [new NamedBean("name", "value")] | "value"
        TaskWithOptionalNestedBean                | 'bean'               | [null]                           | null
        TaskWithOptionalNestedBeanWithPrivateType | 'bean'               | []                               | null
        TaskWithInput                             | 'inputValue'         | ["value"]                        | "value"
        // https://issues.gradle.org/browse/GRADLE-2815.html
        TaskWithBooleanInput                      | 'inputValue'         | [true]                           | true
    }

    def "registers input property implementation for #prop on #type.simpleName"() {
        given:
        def task = expectTaskCreated(type, value as Object[])

        expect:
        def implementationValue = inputProperties(task)[prop] as ImplementationValue
        implementationValue.implementationClassIdentifier == expected.name

        where:
        type                                      | prop                 | value                            | expected
        TaskWithNestedBean                        | 'bean'               | [null]                           | Bean.class
        TaskWithNestedObject                      | 'bean.key'           | [['key': new Bean()]]            | Bean.class
        TaskWithNestedIterable                    | 'beans.$0'           | [new Bean()]                     | Bean.class
        TaskWithNestedBeanWithPrivateClass        | 'bean'               | [null, null]                     | Bean2.class
    }

    def "iterable nested properties are named by index"() {
        given:
        def task = expectTaskCreated(TaskWithNestedObject, [[new Bean(), new NamedBean('name', 'value'), new Bean()]] as Object[])

        expect:
        inputProperties(task).keySet() == ['bean.$0', 'bean.name$1', 'bean.name$1.value', 'bean.$2'] as Set
    }

    def "registers properties #allTaskProperties on #type.simpleName"() {
        given:
        def task = (value == null) ? expectTaskCreated(type) : expectTaskCreated(type, value as Object[])

        when:
        def taskProperties = DefaultTaskProperties.resolve(propertyWalker, fileCollectionFactory, task)

        then:
        taskProperties.inputProperties*.propertyName as Set == inputs as Set
        taskProperties.inputFileProperties*.propertyName as Set == inputFiles as Set
        taskProperties.outputFileProperties*.propertyName as Set == outputFiles as Set
        taskProperties.destroyableFiles.empty
        taskProperties.localStateFiles.empty

        where:
        type                                      | value                          | inputs                                          | inputFiles              | outputFiles
        TaskWithInput                             | ["value"]                      | ["inputValue"]                                  | []                      | []
        TaskWithBooleanInput                      | [true]                         | ["inputValue"]                                  | []                      | []
        TaskWithInputFile                         | [new File("some")]             | []                                              | ["inputFile"]           | []
        TaskWithInputFiles                        | [[new File("some")]]           | []                                              | ["input"]               | []
        BrokenTaskWithInputFiles                  | [[new File("some")]]           | []                                              | ["input"]               | []
        TaskWithInputDir                          | [new File("some")]             | []                                              | ["inputDir"]            | []
        BrokenTaskWithInputDir                    | [new File("some")]             | []                                              | ["inputDir"]            | []
        TaskWithOptionalInputFile                 | null                           | []                                              | ["inputFile"]           | []
        TaskWithOutputFile                        | [new File("some")]             | []                                              | []                      | ["outputFile"]
        TaskWithOutputFiles                       | [[new File("some")]]           | []                                              | []                      | ["outputFiles\$1"]
        TaskWithOutputDir                         | [new File("some")]             | []                                              | []                      | ["outputDir"]
        TaskWithOutputDirs                        | [[new File("some")]]           | []                                              | []                      | ["outputDirs\$1"]
        TaskWithOptionalOutputFile                | null                           | []                                              | []                      | []
        TaskWithOptionalOutputFiles               | null                           | []                                              | []                      | []
        TaskWithOptionalOutputDir                 | null                           | []                                              | []                      | []
        TaskWithOptionalOutputDirs                | null                           | []                                              | []                      | []
        TaskWithNestedBean                        | [null]                         | ["bean"]                                        | ["bean.inputFile"]      | []
        TaskWithNestedIterable                    | [new Bean()]                   | ["beans.\$0"]                                   | ["beans.\$0.inputFile"] | []
        TaskWithNestedBeanWithPrivateClass        | [null, null]                   | ["bean"]                                        | ["bean.inputFile"]      | []
        TaskWithOptionalNestedBean                | [null]                         | []                                              | []                      | []
        TaskWithOptionalNestedBean                | [new Bean()]                   | ["bean"]                                        | ["bean.inputFile"]      | []
        TaskWithOptionalNestedBeanWithPrivateType | null                           | []                                              | []                      | []
        TaskWithMultipleProperties                | [new File("some")]             | ["bean"]                                        | ["bean.inputFile"]      | ["outputFile"]
        TaskWithBridgeMethod                      | null                           | ["nestedProperty"]                              | []                      | ["nestedProperty.someOutputFile"]
        TaskWithJavaBeanCornerCaseProperties      | ["c", "C", "d", "U", "a", "b"] | ["cCompiler", "CFlags", "dns", "URL", "a", "b"] | []                      | []

        allTaskProperties = inputs + inputFiles + outputFiles
    }

    def "registers local state"() {
        def localState = new File(temporaryFolder.file('localState').createFile().absolutePath)
        given:
        def task = expectTaskCreated(TaskWithLocalState, localState)

        when:
        def taskProperties = DefaultTaskProperties.resolve(propertyWalker, fileCollectionFactory, task)

        then:
        taskProperties.localStateFiles.files as List == [localState]
        taskProperties.inputProperties.empty
        taskProperties.inputFileProperties.empty
        taskProperties.outputFileProperties.empty
        taskProperties.destroyableFiles.empty
    }

    def "registers destroyables"() {
        def destroyable = new File(temporaryFolder.file('destroyable').createFile().absolutePath)
        given:
        def task = expectTaskCreated(TaskWithDestroyable, destroyable)

        when:
        def taskProperties = DefaultTaskProperties.resolve(propertyWalker, fileCollectionFactory, task)

        then:
        taskProperties.destroyableFiles.files as List == [destroyable]
        taskProperties.inputProperties.empty
        taskProperties.inputFileProperties.empty
        taskProperties.outputFileProperties.empty
        taskProperties.localStateFiles.empty
    }

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

    private <T extends TaskInternal> T expectTaskCreated(final Class<T> type, final Object... params) {
        final String name = "task"
        T task = AbstractTask.injectIntoNewInstance(project, TaskIdentity.create(name, type, project), new Callable<T>() {
            T call() throws Exception {
                if (params.length > 0) {
                    return type.cast(type.constructors[0].newInstance(params))
                } else {
                    return JavaReflectionUtil.newInstance(type)
                }
            }
        })
        return expectTaskCreated(name, type, task)
    }

    private <T extends TaskInternal> T expectTaskCreated(String name, final Class<T> type, T task) {
        // We cannot just stub here as we want to return a different task each time.
        def id = new TaskIdentity(type, name, null, null, null, 12)
        1 * delegate.create(id) >> task
        def createdTask = factory.create(id)
        assert createdTask.is(task)
        return task
    }

    private static void validateException(TaskInternal task, WorkValidationException exception, String... causes) {
        validateException(task, true, exception, causes)
    }

    private static void validateException(TaskInternal task, boolean ignoreType, WorkValidationException exception, String... causes) {
        def expectedMessage = causes.length > 1 ? "Some problems were found with the configuration of $task" : "A problem was found with the configuration of $task"
        WorkValidationExceptionChecker.check(exception, ignoreType) {
            messageContains(expectedMessage)
            causes.each { cause ->
                hasProblem(cause)
            }
        }
    }

    private Map<String, Object> inputProperties(TaskInternal task) {
        TaskPropertyTestUtils.getProperties(task, propertyWalker)
    }
}
