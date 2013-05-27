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

import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.RelativePath
import spock.lang.Specification

class DuplicateHandlingCopySpecVisitorTest extends Specification {

    private static interface MyCopySpec extends CopySpec, ReadableCopySpec { }

    FileCopySpecVisitor delegate = Mock()
    org.gradle.internal.nativeplatform.filesystem.FileSystem fileSystem = Mock()
    MyCopySpec copySpec = Mock()
    FileTree fileTree = Mock()
    def visitor = new MappingCopySpecVisitor(
            new DuplicateHandlingCopySpecVisitor(delegate), fileSystem)

    
    def duplicatesIncludedByDefault() {
        FileVisitDetails file1 = Mock()
        FileVisitDetails file2 = Mock()
        FileVisitDetails file3 = Mock()

        given:
        file1.relativePath >> new RelativePath(true, 'path/file1.txt')
        file2.relativePath >> new RelativePath(true, 'path/file2.txt')
        file3.relativePath >> new RelativePath(true, 'path/file1.txt')

        fileTree.files >> [ file1, file2, file3 ]
        copySpec.destPath >> new RelativePath(false, '/root')
        copySpec.allCopyActions >> []
        copySpec.source >> fileTree

        when:
        visitor.visitSpec(copySpec)
        fileTree.files.each { visitor.visitFile it }

        then:
        1 * delegate.visitSpec(copySpec)
        2 * delegate.visitFile({ it.relativePath.pathString ==  '/root/path/file1.txt' })
        1 * delegate.visitFile({ it.relativePath.pathString ==  '/root/path/file2.txt' })

    }

    def duplicatesExcludedByConfiguration() {
        FileVisitDetails file1 = Mock()
        FileVisitDetails file2 = Mock()
        FileVisitDetails file3 = Mock()

        given:
        file1.relativePath >> new RelativePath(true, 'path/file1.txt')
        file2.relativePath >> new RelativePath(true, 'path/file2.txt')
        file3.relativePath >> new RelativePath(true, 'path/file1.txt')

        fileTree.files >> [ file1, file2, file3 ]
        copySpec.destPath >> new RelativePath(false, '/root')
        copySpec.allCopyActions >> [ {it.setDuplicatesStrategy('exclude')} as org.gradle.api.Action ]
        copySpec.source >> fileTree

        when:
        visitor.visitSpec(copySpec)
        fileTree.files.each { visitor.visitFile it }

        then:
        1 * delegate.visitSpec(copySpec)
        1 * delegate.visitFile({ it.relativePath.pathString ==  '/root/path/file1.txt' })
        1 * delegate.visitFile({ it.relativePath.pathString ==  '/root/path/file2.txt' })

    }


    def duplicatesExcludedEvenWhenRenamed() {
        FileVisitDetails file1 = Mock()
        FileVisitDetails file2 = Mock()
        FileVisitDetails file3 = Mock()

        given:
        file1.relativePath >> new RelativePath(true, 'module1/path/file1.txt')
        file2.relativePath >> new RelativePath(true, 'module1/path/file2.txt')
        file3.relativePath >> new RelativePath(true, 'module2/path/file1.txt')

        fileTree.files >> [ file1, file2, file3 ]
        copySpec.destPath >> new RelativePath(false, '/root')

        copySpec.allCopyActions >> [
                {it.name = it.name.replaceAll('module[0-9]+/', '')} as org.gradle.api.Action,
                {it.setDuplicatesStrategy('exclude')} as org.gradle.api.Action ]
        copySpec.source >> fileTree

        when:
        visitor.visitSpec(copySpec)
        fileTree.files.each { visitor.visitFile it }

        then:
        1 * delegate.visitSpec(copySpec)
        1 * delegate.visitFile({ it.relativePath.pathString ==  '/root/path/file1.txt' })
        1 * delegate.visitFile({ it.relativePath.pathString ==  '/root/path/file2.txt' })

    }
}
