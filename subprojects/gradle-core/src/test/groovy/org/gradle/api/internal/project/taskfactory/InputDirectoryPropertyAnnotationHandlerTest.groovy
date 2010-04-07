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
package org.gradle.api.internal.project.taskfactory

import java.lang.reflect.AnnotatedElement
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.internal.project.taskfactory.PropertyAnnotationHandler.PropertyActions
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.SkipWhenEmpty
import java.lang.annotation.Annotation
import org.gradle.util.TestFile
import org.gradle.api.internal.tasks.DefaultTaskInputs
import java.util.concurrent.Callable
import org.gradle.api.tasks.TaskInputs

/**
 * @author Hans Dockter
 */
class InputDirectoryPropertyAnnotationHandlerTest extends Specification {
    InputDirectoryPropertyAnnotationHandler handler = new InputDirectoryPropertyAnnotationHandler()

    @Rule
    TemporaryFolder tempDir = new TemporaryFolder()

    def existingDirIsValid() {
        File dir = tempDir.createDir('path')

        expect:
        // no exception is what we expect
        createPropertyActions().validationAction.validate("otherProp", dir)
    }

    def fileTreeWithExistingDirIsValid() {
        File dir = tempDir.createDir('path')
        ConfigurableFileTree fileTree = Mock()
        fileTree.getDir() >> dir

        expect:
        // no exception is what we expect
        createPropertyActions().validationAction.validate("otherProp", fileTree)
    }

    def fileTreeWithNonExistingDirIsInValid() {
        ConfigurableFileTree fileTree = Mock()
        fileTree.getDir() >> new File('nonExistingDir')

        when:
        createPropertyActions().validationAction.validate("otherProp", fileTree)

        then:
        thrown(InvalidUserDataException)
    }

    def fileAsDirIsInvalid() {
        File file = tempDir.createFile('path')

        when:
        createPropertyActions().validationAction.validate("otherProp", file)

        then:
        thrown(InvalidUserDataException)
    }

    def nonExistingFileIsInvalid() {
        File nonExistingFile = tempDir.file('nonExistingPath')

        when:
        createPropertyActions().validationAction.validate("otherProp", nonExistingFile)

        then:
        thrown(InvalidUserDataException)
    }

    def skipWhenEmptyDirectory() {
        AnnotatedElement targetStub = Mock()
        targetStub.getAnnotation(SkipWhenEmpty) >> Mock(Annotation)
        String dummyProperty = 'someProp'

        File emptyDir = tempDir.createDir('path')

        when:
        createPropertyActionsWithSkipWhenEmpty().skipAction.validate("otherProp", emptyDir)

        then:
        thrown(StopExecutionException)
    }

    def doNotSkipWhenNonEmptyDirectory() {
        TestFile file = tempDir.createFile('dir', 'file')

        expect:
        createPropertyActionsWithSkipWhenEmpty().skipAction.validate("otherProp", file.getParentFile())
    }

    def returnNullSkipActionWithoutSkipWhenEmptyAnnotation() {
        expect:
        createPropertyActions().skipAction == null
    }

    def attachInputsShouldAddToTaskInputsDir() {
        TaskInputs taskInputsMock = Mock(TaskInputs)
        File file = tempDir.file("path")

        when:
        createPropertyActions().attachInputs(taskInputsMock, { file } as Callable)

        then:
        1 * taskInputsMock.dir({ callable -> callable.call() == file})
    }

    PropertyActions createPropertyActions() {
        AnnotatedElement targetDummy = Mock()
        String dummyProperty = 'someProp'
        handler.getActions(targetDummy, dummyProperty)
    }

    PropertyActions createPropertyActionsWithSkipWhenEmpty() {
        AnnotatedElement targetStub = Mock()
        targetStub.getAnnotation(SkipWhenEmpty) >> Mock(Annotation)
        String dummyProperty = 'someProp'
        handler.getActions(targetStub, dummyProperty)
    }
}
