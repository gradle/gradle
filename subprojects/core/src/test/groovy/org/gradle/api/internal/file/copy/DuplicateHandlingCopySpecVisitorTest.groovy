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
import spock.lang.Specification

/**
 * Unit tests for DuplicateHandlingCopySpecVisitor
 * @author Kyle Mahan
 */
class DuplicateHandlingCopySpecVisitorTest extends Specification {

    private static interface MyCopySpec extends CopySpec, ReadableCopySpec { }

    FileCopySpecVisitor delegate = Mock()
    org.gradle.internal.nativeplatform.filesystem.FileSystem fileSystem = Mock()
    MyCopySpec copySpec = Mock()
    FileTree fileTree = Mock()
    def visitor = new MappingCopySpecVisitor(
            new DuplicateHandlingCopySpecVisitor(delegate, false), fileSystem)

    
    def duplicatesIncludedByDefault() {
        given:
        buildCopySpec(['path/file1.txt', 'path/file2.txt', 'path/file1.txt'])
        copySpec.allCopyActions >> []

        when:
        visitor.startVisit(null);
        visitor.visitSpec(copySpec)
        fileTree.files.each { visitor.visitFile it }
        visitor.endVisit();

        then:
        _ * delegate.visitSpec(copySpec)
        2 * delegate.visitFile({ it.relativePath.pathString ==  '/root/path/file1.txt' })
        1 * delegate.visitFile({ it.relativePath.pathString ==  '/root/path/file2.txt' })

    }

    def duplicatesExcludedByPerFileConfiguration() {
        given:
        buildCopySpec(['path/file1.txt', 'path/file2.txt', 'path/file1.txt'])
        copySpec.allCopyActions >> [ { it.duplicatesStrategy = 'exclude'} as org.gradle.api.Action ]

        when:
        visitor.startVisit(null);
        visitor.visitSpec(copySpec)
        fileTree.files.each { visitor.visitFile it }
        visitor.endVisit();

        then:
        _ * delegate.visitSpec(copySpec)
        1 * delegate.visitFile({ it.relativePath.pathString ==  '/root/path/file1.txt' })
        1 * delegate.visitFile({ it.relativePath.pathString ==  '/root/path/file2.txt' })
    }


    def duplicatesExcludedEvenWhenRenamed() {
        given:
        buildCopySpec(['module1/path/file1.txt', 'module1/path/file2.txt', 'module2/path/file1.txt'])

        copySpec.allCopyActions >> [
                {it.name = it.name.replaceAll('module[0-9]+/', '')} as org.gradle.api.Action,
                {it.duplicatesStrategy = 'exclude'} as org.gradle.api.Action ]

        when:
        visitor.startVisit(null);
        visitor.visitSpec(copySpec)
        fileTree.files.each { visitor.visitFile it }
        visitor.endVisit();

        then:
        _ * delegate.visitSpec(copySpec)
        1 * delegate.visitFile({ it.relativePath.pathString ==  '/root/path/file1.txt' })
        1 * delegate.visitFile({ it.relativePath.pathString ==  '/root/path/file2.txt' })
    }

    def duplicatesExcludedByDefaultConfiguration() {
        given:
        buildCopySpec(['path/file1.txt', 'path/file2.txt', 'path/file1.txt'])
        copySpec.allCopyActions >> []
        copySpec.duplicatesStrategy >> DuplicatesStrategy.exclude

        when:
        visitor.startVisit(null);
        visitor.visitSpec(copySpec)
        fileTree.files.each { visitor.visitFile it }
        visitor.endVisit();

        then:
        _ * delegate.visitSpec(copySpec)
        1 * delegate.visitFile({ it.relativePath.pathString ==  '/root/path/file1.txt' })
        1 * delegate.visitFile({ it.relativePath.pathString ==  '/root/path/file2.txt' })
    }

    def buildCopySpec(List<String> fileNames) {
        fileTree.files >> fileNames.collect { fileName ->
            def file = Mock(FileVisitDetails)
            file.relativePath >> new RelativePath(true, fileName)
            return file
        }
        copySpec.destPath >> new RelativePath(false, '/root')
        copySpec.source >> fileTree
    }
}
