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
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.internal.cache.StringInterner
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

import static org.gradle.api.internal.changedetection.state.TaskFilePropertySnapshotNormalizationStrategy.*

class TaskFilePropertySnapshotNormalizationStrategyTest extends AbstractProjectBuilderSpec {
    StringInterner interner
    FileCollection files

    def setup() {
        interner = Mock(StringInterner) {
            intern(_) >> { String string -> string }
        }

        createFile("dir/libs/library-a.jar") << "JAR file #1"
        createFile("dir/libs/library-b.jar") << "JAR file #2"
        createFile("dir/resources/input.txt") << "main input"
        createFile("dir/resources/a/input-1.txt") << "input #1"
        createFile("dir/resources/b/input-2.txt") << "input #2"

        files = project.files "dir/libs/library-a.jar", "dir/libs/library-b.jar", "dir/resources"
    }

    def "sensitivity NONE"() {
        def snapshots = normalizeWith NONE
        expect:
        snapshots[file("dir/libs/library-a.jar")]      == "IGNORED"
        snapshots[file("dir/libs/library-b.jar")]      == "IGNORED"
        snapshots[file("dir/resources/input.txt")]     == "IGNORED"
        snapshots[file("dir/resources/a")]             == "NO SNAPSHOT"
        snapshots[file("dir/resources/a/input-1.txt")] == "IGNORED"
        snapshots[file("dir/resources/b")]             == "NO SNAPSHOT"
        snapshots[file("dir/resources/b/input-2.txt")] == "IGNORED"
    }

    def "sensitivity NAME_ONLY"() {
        def snapshots = normalizeWith NAME_ONLY
        expect:
        snapshots[file("dir/libs/library-a.jar")]      == "library-a.jar"
        snapshots[file("dir/libs/library-b.jar")]      == "library-b.jar"
        snapshots[file("dir/resources/input.txt")]     == "input.txt"
        snapshots[file("dir/resources/a")]             == "a"
        snapshots[file("dir/resources/a/input-1.txt")] == "input-1.txt"
        snapshots[file("dir/resources/b")]             == "b"
        snapshots[file("dir/resources/b/input-2.txt")] == "input-2.txt"
    }

    def "sensitivity CLASSPATH"() {
        def snapshots = normalizeWith CLASSPATH
        expect:
        snapshots[file("dir/libs/library-a.jar")]      == "IGNORED"
        snapshots[file("dir/libs/library-b.jar")]      == "IGNORED"
        snapshots[file("dir/resources/input.txt")]     == "input.txt"
        snapshots[file("dir/resources/a")]             == "a"
        snapshots[file("dir/resources/a/input-1.txt")] == "a/input-1.txt"
        snapshots[file("dir/resources/b")]             == "b"
        snapshots[file("dir/resources/b/input-2.txt")] == "b/input-2.txt"
    }

    def "sensitivity RELATIVE"() {
        def snapshots = normalizeWith RELATIVE
        expect:
        snapshots[file("dir/libs/library-a.jar")]      == "library-a.jar"
        snapshots[file("dir/libs/library-b.jar")]      == "library-b.jar"
        snapshots[file("dir/resources/input.txt")]     == "input.txt"
        snapshots[file("dir/resources/a")]             == "a"
        snapshots[file("dir/resources/a/input-1.txt")] == "a/input-1.txt"
        snapshots[file("dir/resources/b")]             == "b"
        snapshots[file("dir/resources/b/input-2.txt")] == "b/input-2.txt"
    }

    def "sensitivity ABSOLUTE"() {
        def snapshots = normalizeWith ABSOLUTE
        expect:
        snapshots[file("dir/libs/library-a.jar")]      == file("dir/libs/library-a.jar").absolutePath
        snapshots[file("dir/libs/library-b.jar")]      == file("dir/libs/library-b.jar").absolutePath
        snapshots[file("dir/resources/input.txt")]     == file("dir/resources/input.txt").absolutePath
        snapshots[file("dir/resources/a")]             == file("dir/resources/a").absolutePath
        snapshots[file("dir/resources/a/input-1.txt")] == file("dir/resources/a/input-1.txt").absolutePath
        snapshots[file("dir/resources/b")]             == file("dir/resources/b").absolutePath
        snapshots[file("dir/resources/b/input-2.txt")] == file("dir/resources/b/input-2.txt").absolutePath
    }

    def createFile(String path) {
        def file = file(path)
        file.parentFile.mkdirs()
        return file
    }

    def file(String path) {
        project.file(path)
    }

    private def normalizeWith(TaskFilePropertySnapshotNormalizationStrategy type) {
        List<FileDetails> fileTreeElements = []
        files.asFileTree.visit(new FileVisitor() {
            @Override
            public void visitDir(FileVisitDetails dirDetails) {
                fileTreeElements.add(new DefaultFileDetails(dirDetails.file.path, FileDetails.FileType.Directory, dirDetails))
            }

            @Override
            public void visitFile(FileVisitDetails fileDetails) {
                fileTreeElements.add(new DefaultFileDetails(fileDetails.file.path, FileDetails.FileType.RegularFile, fileDetails))
            }
        })

        Map<File, String> snapshots = [:]
        fileTreeElements.each { details ->
            IncrementalFileSnapshot snapshot
            if (details.type == FileDetails.FileType.Directory) {
                snapshot = DirSnapshot.instance
            } else {
                snapshot = new FileHashSnapshot(HashCode.fromInt(1))
            }
            def normalizedSnapshot = type.getNormalizedSnapshot(details, snapshot, interner)
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
