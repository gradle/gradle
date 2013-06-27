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

import org.gradle.api.file.*
import org.gradle.api.internal.ClosureBackedAction
import org.gradle.internal.nativeplatform.filesystem.FileSystem
import spock.lang.Specification

/**
 * Unit tests for DuplicateHandlingCopySpecVisitor
 * @author Kyle Mahan
 */
class DuplicateHandlingCopySpecVisitorTest extends Specification {

    private static interface MyCopySpec extends CopySpec, ReadableCopySpec {}

    def fileSystem = Mock(FileSystem)
    def delegate = Mock(FileCopySpecVisitor)
    def visitor = new DuplicateHandlingCopySpecVisitor(delegate, false)
    def driver = new CopySpecVisitorDriver(fileSystem)
    def copySpec = Mock(MyCopySpec)

    def duplicatesIncludedByDefault() {
        given:
        files 'path/file1.txt', 'path/file2.txt', 'path/file1.txt'
        actions {}

        when:
        visit()

        then:
        2 * delegate.visitFile({ it.relativePath.pathString == '/root/path/file1.txt' })
        1 * delegate.visitFile({ it.relativePath.pathString == '/root/path/file2.txt' })
    }

    def duplicatesExcludedByPerFileConfiguration() {
        given:
        files 'path/file1.txt', 'path/file2.txt', 'path/file1.txt'
        actions { it.duplicatesStrategy = 'exclude' }

        when:
        visit()

        then:
        1 * delegate.visitFile({ it.relativePath.pathString == '/root/path/file1.txt' })
        1 * delegate.visitFile({ it.relativePath.pathString == '/root/path/file2.txt' })
    }


    def duplicatesExcludedEvenWhenRenamed() {
        given:
        files 'module1/path/file1.txt', 'module1/path/file2.txt', 'module2/path/file1.txt'

        actions({ it.name = it.name.replaceAll('module[0-9]+/', '') }, { it.duplicatesStrategy = 'exclude' })

        when:
        visit()

        then:
        1 * delegate.visitFile({ it.relativePath.pathString == '/root/path/file1.txt' })
        1 * delegate.visitFile({ it.relativePath.pathString == '/root/path/file2.txt' })
    }

    def duplicatesExcludedByDefaultConfiguration() {
        given:
        files 'path/file1.txt', 'path/file2.txt', 'path/file1.txt'
        actions {}
        copySpec.duplicatesStrategy >> DuplicatesStrategy.exclude

        when:
        visit()

        then:
        1 * delegate.visitFile({ it.relativePath.pathString == '/root/path/file1.txt' })
        1 * delegate.visitFile({ it.relativePath.pathString == '/root/path/file2.txt' })
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
    }

    void actions(Closure... actions) {
        copySpec.allCopyActions >> actions.collect { new ClosureBackedAction<>(it) }
    }

    void visit() {
        driver.visit(null, [copySpec], visitor)
    }

}
