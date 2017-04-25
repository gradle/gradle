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

package org.gradle.api.internal.changedetection.state

import com.google.common.hash.HashCode
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

class AbstractSnapshotNormalizationStrategyTest extends AbstractProjectBuilderSpec {
    StringInterner interner
    FileCollectionInternal files

    def setup() {
        interner = Mock(StringInterner) {
            intern(_) >> { String string -> string }
        }

        createFile("dir/libs/library-a.jar") << "JAR file #1"
        createFile("dir/libs/library-b.jar") << "JAR file #2"
        createFile("dir/resources/input.txt") << "main input"
        createFile("dir/resources/a/input-1.txt") << "input #1"
        createFile("dir/resources/b/input-2.txt") << "input #2"

        files = project.files("dir/libs/library-a.jar", "dir/libs/library-b.jar", "dir/resources")
    }

    private def createFile(String path) {
        def file = file(path)
        file.parentFile.mkdirs()
        return file
    }

    protected def file(String path) {
        project.file(path)
    }

    protected def normalizeWith(SnapshotNormalizationStrategy type) {
        List<FileSnapshot> fileTreeElements = []
        files.each { f ->
            if (f.file) {
                fileTreeElements.add(new RegularFileSnapshot(f.path, new RelativePath(true, f.name), true, new FileHashSnapshot(HashCode.fromInt(1))))
            } else {
                fileTreeElements.add(new DirectoryFileSnapshot(f.path, new RelativePath(false, f.name), true))
                project.fileTree(f).visit(new FileVisitor() {
                    @Override
                    void visitDir(FileVisitDetails dirDetails) {
                        fileTreeElements.add(new DirectoryFileSnapshot(dirDetails.file.path, dirDetails.relativePath, false))
                    }

                    @Override
                    void visitFile(FileVisitDetails fileDetails) {
                        fileTreeElements.add(new RegularFileSnapshot(fileDetails.file.path, fileDetails.relativePath, false, new FileHashSnapshot(HashCode.fromInt(1))))
                    }
                })
            }
        }

        Map<File, String> snapshots = [:]
        fileTreeElements.each { details ->
            def normalizedSnapshot = type.getNormalizedSnapshot(details, interner)
            String normalizedPath
            if (normalizedSnapshot == null) {
                normalizedPath = "NO SNAPSHOT"
            } else if (normalizedSnapshot instanceof IgnoredPathFileSnapshot) {
                normalizedPath = "IGNORED"
            } else {
                normalizedPath = normalizedSnapshot.normalizedPath
            }
            snapshots.put(new File(details.path), normalizedPath)
        }
        return snapshots
    }
}
