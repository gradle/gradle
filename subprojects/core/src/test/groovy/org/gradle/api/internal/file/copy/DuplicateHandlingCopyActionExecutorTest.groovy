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
package org.gradle.api.internal.file.copy

import org.gradle.api.Action
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DuplicateFileCopyingException
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.file.CopyActionProcessingStreamAction
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.WorkResults
import org.gradle.internal.logging.ConfigureLogging
import org.gradle.internal.logging.TestOutputEventListener
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.internal.reflect.Instantiator
import org.gradle.util.internal.ClosureBackedAction
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Shared
import spock.lang.Specification

class DuplicateHandlingCopyActionExecutorTest extends Specification {

    private static interface MyCopySpec extends CopySpec, CopySpecInternal {}

    def fileSystem = Mock(FileSystem)
    def delegateAction = Mock(CopyActionProcessingStreamAction)
    def delegate = new CopyAction() {
        WorkResult execute(CopyActionProcessingStream stream) {
            stream.process(delegateAction)
            return WorkResults.didWork(true);
        }
    }

    def outputEventListener = new TestOutputEventListener()
    @Rule ConfigureLogging logging = new ConfigureLogging(outputEventListener)

    @Shared Instantiator instantiator = TestUtil.instantiatorFactory().decorateLenient()
    def executer = new CopyActionExecuter(instantiator, TestUtil.propertyFactory(), fileSystem, false, TestFiles.documentationRegistry())
    def copySpec = Mock(MyCopySpec) {
        getChildren() >> []
    }
    def copySpecResolver = Mock(CopySpecResolver)

    def duplicatesIncludedByDefault() {
        given:
        files 'path/file1.txt', 'path/file2.txt', 'path/file1.txt'
        actions {}

        when:
        visit()

        then:
        2 * delegateAction.processFile({ it.relativePath.pathString == '/root/path/file1.txt' })
        1 * delegateAction.processFile({ it.relativePath.pathString == '/root/path/file2.txt' })
    }

    def duplicatesExcludedByPerFileConfiguration() {
        given:
        files 'path/file1.txt', 'path/file2.txt', 'path/file1.txt'
        actions { it.duplicatesStrategy = 'exclude' }

        when:
        visit()

        then:
        1 * delegateAction.processFile({ FileCopyDetails it ->
            it.relativePath.pathString == '/root/path/file1.txt'
        })
        1 * delegateAction.processFile({ it.relativePath.pathString == '/root/path/file2.txt' })
    }


    def duplicatesExcludedEvenWhenRenamed() {
        given:
        files 'module1/path/file1.txt', 'module1/path/file2.txt', 'module2/path/file1.txt'

        actions({ it.name = it.name.replaceAll('module[0-9]+/', '') }, { it.duplicatesStrategy = 'exclude' })

        when:
        visit()

        then:
        1 * delegateAction.processFile({ it.relativePath.pathString == '/root/path/file1.txt' })
        1 * delegateAction.processFile({ it.relativePath.pathString == '/root/path/file2.txt' })
    }

    def duplicatesExcludedByDefaultConfiguration() {
        given:
        files 'path/file1.txt', 'path/file2.txt', 'path/file1.txt'
        actions {}
        copySpecResolver.duplicatesStrategy >> DuplicatesStrategy.EXCLUDE

        when:
        visit()

        then:
        1 * delegateAction.processFile({ it.relativePath.pathString == '/root/path/file1.txt' })
        1 * delegateAction.processFile({ it.relativePath.pathString == '/root/path/file2.txt' })
    }

    def duplicatesFailByDefaultConfiguration() {
        given:
        files 'path/file1.txt', 'path/file2.txt', 'path/file1.txt'
        actions {}
        copySpecResolver.duplicatesStrategy >> DuplicatesStrategy.FAIL

        when:
        visit()

        then:
        1 * delegateAction.processFile({ it.relativePath.pathString == '/root/path/file1.txt' })
        1 * delegateAction.processFile({ it.relativePath.pathString == '/root/path/file2.txt' })
        thrown(DuplicateFileCopyingException)
    }

    def duplicatesWarnByDefaultConfiguration() {
        given:
        files 'path/file1.txt', 'path/file2.txt', 'path/file1.txt'
        actions {}
        copySpecResolver.duplicatesStrategy >> DuplicatesStrategy.WARN

        when:
        visit()

        then:
        2 * delegateAction.processFile({ it.relativePath.pathString == '/root/path/file1.txt' })
        1 * delegateAction.processFile({ it.relativePath.pathString == '/root/path/file2.txt' })
        outputEventListener.toString().contains("[WARN] [org.gradle.api.internal.file.copy.DuplicateHandlingCopyActionDecorator] file 'path/file1.txt' will be copied to '/root/path/file1.txt', overwriting file 'path/file1.txt', which has already been copied there.")
    }


    def duplicatesWarnByPerFileConfiguration() {
        given:
        files 'path/file1.txt', 'path/file2.txt', 'path/file1.txt'
        actions { it.duplicatesStrategy = 'warn' }

        when:
        visit()

        then:
        2 * delegateAction.processFile({ it.relativePath.pathString == '/root/path/file1.txt' })
        1 * delegateAction.processFile({ it.relativePath.pathString == '/root/path/file2.txt' })
        outputEventListener.toString().contains("[WARN] [org.gradle.api.internal.file.copy.DuplicateHandlingCopyActionDecorator] file 'path/file1.txt' will be copied to '/root/path/file1.txt', overwriting file 'path/file1.txt', which has already been copied there.")
    }

    def duplicatesFailByPerFileConfiguration() {
        given:
        files 'path/file1.txt', 'path/file2.txt', 'path/file1.txt'
        actions { it.duplicatesStrategy = 'fail' }

        when:
        visit()

        then:
        1 * delegateAction.processFile({ it.relativePath.pathString == '/root/path/file1.txt' })
        1 * delegateAction.processFile({ it.relativePath.pathString == '/root/path/file2.txt' })
        thrown(DuplicateFileCopyingException)
    }


    void files(String... fileNames) {
        copySpecResolver.destPath >> new RelativePath(false, '/root')
        def fileTree = Mock(FileTree)
        copySpecResolver.getSource() >> fileTree
        fileTree.visit(_ as FileVisitor) >> { FileVisitor visitor ->
            fileNames.each { filename ->
                def fvd = Mock(FileVisitDetails) {
                    getRelativePath() >> new RelativePath(true, filename)
                    toString() >> "file '${filename}'"
                }
                visitor.visitFile(fvd)
            }
            fileTree
        }
        copySpec.walk(_) >> { Action it -> it.execute(copySpecResolver) }
    }

    void actions(Closure... actions) {
        copySpecResolver.allCopyActions >> actions.collect { new ClosureBackedAction<>(it) }
    }

    void visit() {
        executer.execute(copySpec, delegate)
    }

}
