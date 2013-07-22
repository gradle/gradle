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
import org.gradle.api.file.*
import org.gradle.api.internal.ClosureBackedAction
import org.gradle.api.internal.ThreadGlobalInstantiator
import org.gradle.api.internal.tasks.SimpleWorkResult
import org.gradle.api.tasks.WorkResult
import org.gradle.internal.nativeplatform.filesystem.FileSystem
import org.gradle.internal.reflect.Instantiator
import org.gradle.logging.ConfigureLogging
import org.gradle.logging.TestAppender
import org.junit.Rule
import spock.lang.Shared
import spock.lang.Specification

class DuplicateHandlingCopyActionDecoratorTest extends Specification {

    private static interface MyCopySpec extends CopySpec, CopySpecInternal {}

    def fileSystem = Mock(FileSystem)
    def delegateAction = Mock(Action)
    def delegate = new CopyAction() {
        WorkResult execute(Action<Action<? super FileCopyDetailsInternal>> stream) {
            stream.execute(delegateAction)
            return new SimpleWorkResult(true)
        }
    }

    def appender = new TestAppender()
    @Rule ConfigureLogging logging = new ConfigureLogging(appender)

    @Shared Instantiator instantiator = ThreadGlobalInstantiator.getOrCreate()
    def driver = new CopyActionExecuter(instantiator, fileSystem)
    def copySpec = Mock(MyCopySpec) {
        getChildren() >> []
    }

    def duplicatesIncludedByDefault() {
        given:
        files 'path/file1.txt', 'path/file2.txt', 'path/file1.txt'
        actions {}

        when:
        visit()

        then:
        2 * delegateAction.execute({ it.relativePath.pathString == '/root/path/file1.txt' })
        1 * delegateAction.execute({ it.relativePath.pathString == '/root/path/file2.txt' })
    }

    def duplicatesExcludedByPerFileConfiguration() {
        given:
        files 'path/file1.txt', 'path/file2.txt', 'path/file1.txt'
        actions { it.duplicatesStrategy = 'exclude' }

        when:
        visit()

        then:
        1 * delegateAction.execute({ FileCopyDetails it ->
            it.relativePath.pathString == '/root/path/file1.txt'
        })
        1 * delegateAction.execute({ it.relativePath.pathString == '/root/path/file2.txt' })
    }


    def duplicatesExcludedEvenWhenRenamed() {
        given:
        files 'module1/path/file1.txt', 'module1/path/file2.txt', 'module2/path/file1.txt'

        actions({ it.name = it.name.replaceAll('module[0-9]+/', '') }, { it.duplicatesStrategy = 'exclude' })

        when:
        visit()

        then:
        1 * delegateAction.execute({ it.relativePath.pathString == '/root/path/file1.txt' })
        1 * delegateAction.execute({ it.relativePath.pathString == '/root/path/file2.txt' })
    }

    def duplicatesExcludedByDefaultConfiguration() {
        given:
        files 'path/file1.txt', 'path/file2.txt', 'path/file1.txt'
        actions {}
        copySpec.duplicatesStrategy >> DuplicatesStrategy.EXCLUDE

        when:
        visit()

        then:
        1 * delegateAction.execute({ it.relativePath.pathString == '/root/path/file1.txt' })
        1 * delegateAction.execute({ it.relativePath.pathString == '/root/path/file2.txt' })
    }

    def duplicatesFailByDefaultConfiguration() {
        given:
        files 'path/file1.txt', 'path/file2.txt', 'path/file1.txt'
        actions {}
        copySpec.duplicatesStrategy >> DuplicatesStrategy.FAIL

        when:
        visit()

        then:
        1 * delegateAction.execute({ it.relativePath.pathString == '/root/path/file1.txt' })
        1 * delegateAction.execute({ it.relativePath.pathString == '/root/path/file2.txt' })
        thrown(DuplicateFileCopyingException)
    }

    def duplicatesWarnByDefaultConfiguration() {
        given:
        files 'path/file1.txt', 'path/file2.txt', 'path/file1.txt'
        actions {}
        copySpec.duplicatesStrategy >> DuplicatesStrategy.WARN

        when:
        visit()

        then:
        2 * delegateAction.execute({ it.relativePath.pathString == '/root/path/file1.txt' })
        1 * delegateAction.execute({ it.relativePath.pathString == '/root/path/file2.txt' })
        appender.toString().contains('WARN Encountered duplicate path "/root/path/file1.txt"')
    }


    def duplicatesWarnByPerFileConfiguration() {
        given:
        files 'path/file1.txt', 'path/file2.txt', 'path/file1.txt'
        actions { it.duplicatesStrategy = 'warn' }

        when:
        visit()

        then:
        2 * delegateAction.execute({ it.relativePath.pathString == '/root/path/file1.txt' })
        1 * delegateAction.execute({ it.relativePath.pathString == '/root/path/file2.txt' })
        appender.toString().contains('WARN Encountered duplicate path "/root/path/file1.txt"')
    }

    def duplicatesFailByPerFileConfiguration() {
        given:
        files 'path/file1.txt', 'path/file2.txt', 'path/file1.txt'
        actions { it.duplicatesStrategy = 'fail' }

        when:
        visit()

        then:
        1 * delegateAction.execute({ it.relativePath.pathString == '/root/path/file1.txt' })
        1 * delegateAction.execute({ it.relativePath.pathString == '/root/path/file2.txt' })
        thrown(DuplicateFileCopyingException)
    }


    void files(String... fileNames) {
        copySpec.destPath >> new RelativePath(false, '/root')
        def fileTree = Mock(FileTree)
        copySpec.getSource() >> fileTree
        fileTree.visit(_ as FileVisitor) >> { FileVisitor visitor ->
            fileNames.each { filename ->
                def fvd = Mock(FileVisitDetails) {
                    getRelativePath() >> new RelativePath(true, filename)
                }
                visitor.visitFile(fvd)
            }
            fileTree
        }
        copySpec.walk(_) >> { Action it -> it.execute(copySpec) }
    }

    void actions(Closure... actions) {
        copySpec.allCopyActions >> actions.collect { new ClosureBackedAction<>(it) }
    }

    void visit() {
        driver.execute(copySpec, delegate)
    }

}
