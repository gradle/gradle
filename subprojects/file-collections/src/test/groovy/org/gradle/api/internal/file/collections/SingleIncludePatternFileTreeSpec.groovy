/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.internal.file.collections

import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.specs.Spec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.ClassRule
import spock.lang.Shared
import spock.lang.Specification

@UsesNativeServices
class SingleIncludePatternFileTreeSpec extends Specification {
    @Shared @ClassRule TestNameTestDirectoryProvider tempDir = new TestNameTestDirectoryProvider(SingleIncludePatternFileTreeSpec)

    def visitor = Mock(FileVisitor)

    def fileTree

    def setupSpec() {
        tempDir.testDirectory.create {
            dir1 {
                file("file1")
                file("file2")
                file("file3")
            }

            dir2 {
                file("file1")
                file("file2")
                file("file3")

                dir1 {
                    file("file1")
                    file("file2")
                    file("file3")
                }

                dir2 {}
            }

            dir3 {
                file("file1")
                file("file2")
                file("file3")
            }
        }
    }

    def "visits structure"() {
        def spec = Stub(Spec)
        def owner = Stub(FileTreeInternal)
        def visitor = Mock(MinimalFileTree.MinimalFileTreeStructureVisitor)

        fileTree = new SingleIncludePatternFileTree(tempDir.testDirectory, "pattern", spec)

        when:
        fileTree.visitStructure(visitor, owner)

        then:
        1 * visitor.visitFileTree(tempDir.testDirectory, _, owner) >> { dir, patterns, fileTree ->
            assert patterns.includes == ["pattern"] as Set
            assert patterns.excludes.isEmpty()
            assert patterns.includeSpecs.isEmpty()
            assert patterns.excludeSpecs == [spec] as Set
        }
        0 * visitor._
    }

    def "include leaf file"() {
        fileTree = new SingleIncludePatternFileTree(tempDir.testDirectory, "dir1/file2")
        def expectedDir = tempDir.file("dir1")
        def expectedFile = tempDir.file("dir1/file2")

        when:
        fileTree.visit(visitor)

        then:
        1 * visitor.visitDir(_) >> { FileVisitDetails details ->
            with(details) {
                file == expectedDir
                directory
                size == expectedDir.size()
                name == expectedDir.name
                path == "dir1"
                relativePath.segments == ["dir1"]
            }
        }

        then:
        1 * visitor.visitFile(_) >> { FileVisitDetails details ->
            with(details) {
                file == expectedFile
                !directory
                size == expectedFile.size()
                name == expectedFile.name
                path == "dir1/file2"
                relativePath.segments == ["dir1", "file2"]
            }
        }

        then:
        0 * _
    }

    def "include inner file"() {
        fileTree = new SingleIncludePatternFileTree(tempDir.testDirectory, "dir2/file2")

        when:
        fileTree.visit(visitor)

        then:
        1 * visitor.visitDir({ it.file == tempDir.file("dir2") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir2/file2") })
        0 * _
    }

    def "include leaf dir"() {
        fileTree = new SingleIncludePatternFileTree(tempDir.testDirectory, "dir2/dir2")

        when:
        fileTree.visit(visitor)

        then:
        1 * visitor.visitDir({ it.file == tempDir.file("dir2") })
        1 * visitor.visitDir({ it.file == tempDir.file("dir2/dir2") })
        0 * _
    }

    def "include inner dir"() {
        fileTree = new SingleIncludePatternFileTree(tempDir.testDirectory, "dir2")

        when:
        fileTree.visit(visitor)

        then:
        1 * visitor.visitDir({ it.file == tempDir.file("dir2") })

        then:
        0 * _
    }

    def "include directory non-recursively"() {
        fileTree = new SingleIncludePatternFileTree(tempDir.testDirectory, "dir2/*")

        when:
        fileTree.visit(visitor)

        then:
        1 * visitor.visitFile({ it.file == tempDir.file("dir2/file1") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir2/file2") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir2/file3") })
        1 * visitor.visitDir({ it.file == tempDir.file("dir2") })
        1 * visitor.visitDir({ it.file == tempDir.file("dir2/dir1") })
        1 * visitor.visitDir({ it.file == tempDir.file("dir2/dir2") })
        0 * _
    }

    def "include directory recursively"() {
        fileTree = new SingleIncludePatternFileTree(tempDir.testDirectory, includePattern)

        when:
        fileTree.visit(visitor)

        then:
        1 * visitor.visitDir({ it.file == tempDir.file("dir2") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir2/file1") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir2/file2") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir2/file3") })
        1 * visitor.visitDir({ it.file == tempDir.file("dir2/dir1") })
        1 * visitor.visitDir({ it.file == tempDir.file("dir2/dir2") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir2/dir1/file1") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir2/dir1/file2") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir2/dir1/file3") })
        0 * _

        where:
        includePattern << ["dir2/**", "dir2/"]
    }

    def "find all file1's"() {
        fileTree = new SingleIncludePatternFileTree(tempDir.testDirectory, "**/file1")

        when:
        fileTree.visit(visitor)

        then:
        1 * visitor.visitDir({ it.file == tempDir.file("dir1") })
        1 * visitor.visitDir({ it.file == tempDir.file("dir2") })
        1 * visitor.visitDir({ it.file == tempDir.file("dir3") })
        1 * visitor.visitDir({ it.file == tempDir.file("dir2/dir1") })
        1 * visitor.visitDir({ it.file == tempDir.file("dir2/dir2") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir1/file1") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir2/file1") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir3/file1") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir2/dir1/file1") })
        0 * _
    }

    def "find all file1's under dir2"() {
        fileTree = new SingleIncludePatternFileTree(tempDir.testDirectory, "dir2/**/file1")

        when:
        fileTree.visit(visitor)

        then:
        1 * visitor.visitDir({ it.file == tempDir.file("dir2") })
        1 * visitor.visitDir({ it.file == tempDir.file("dir2/dir1") })
        1 * visitor.visitDir({ it.file == tempDir.file("dir2/dir2") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir2/file1") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir2/dir1/file1") })
        0 * _
    }

    def "include everything"() {
        fileTree = new SingleIncludePatternFileTree(tempDir.testDirectory, "**")

        when:
        fileTree.visit(visitor)

        then:
        1 * visitor.visitDir({ it.file == tempDir.file("dir1") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir1/file1") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir1/file2") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir1/file3") })
        1 * visitor.visitDir({ it.file == tempDir.file("dir2") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir2/file1") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir2/file2") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir2/file3") })
        1 * visitor.visitDir({ it.file == tempDir.file("dir3") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir3/file1") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir3/file2") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir3/file3") })
        1 * visitor.visitDir({ it.file == tempDir.file("dir2/dir1") })
        1 * visitor.visitDir({ it.file == tempDir.file("dir2/dir2") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir2/dir1/file1") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir2/dir1/file2") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir2/dir1/file3") })
        0 * _
    }

    def "inner *"() {
        fileTree = new SingleIncludePatternFileTree(tempDir.testDirectory, "*/file1")

        when:
        fileTree.visit(visitor)

        then:
        1 * visitor.visitDir({ it.file == tempDir.file("dir1") })
        1 * visitor.visitDir({ it.file == tempDir.file("dir2") })
        1 * visitor.visitDir({ it.file == tempDir.file("dir3") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir1/file1") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir2/file1") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir3/file1") })
        0 * _
    }

    def "inner ?"() {
        fileTree = new SingleIncludePatternFileTree(tempDir.testDirectory, "dir?/file1")

        when:
        fileTree.visit(visitor)

        then:
        1 * visitor.visitDir({ it.file == tempDir.file("dir1") })
        1 * visitor.visitDir({ it.file == tempDir.file("dir2") })
        1 * visitor.visitDir({ it.file == tempDir.file("dir3") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir1/file1") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir2/file1") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir3/file1") })
        0 * _
    }

    def "stop visiting"() {
        fileTree = new SingleIncludePatternFileTree(tempDir.testDirectory, "dir?/file1")

        when:
        fileTree.visit(visitor)

        then:
        1 * visitor.visitDir({ it.file == tempDir.file("dir2") }) >> { FileVisitDetails details -> details.stopVisiting() }
        0 * visitor.visitDir({ it.path.startsWith("dir2") })
        0 * visitor.visitFile({ it.path.startsWith("dir2") })
    }

    def "use exclude spec"() {
        fileTree = new SingleIncludePatternFileTree(tempDir.testDirectory, "dir?/file1", { it.path.startsWith("dir2") } as Spec)

        when:
        fileTree.visit(visitor)

        then:
        1 * visitor.visitDir({ it.file == tempDir.file("dir1") })
        1 * visitor.visitDir({ it.file == tempDir.file("dir3") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir1/file1") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir3/file1") })
        0 * _
    }

    def "use backslashes"() {
        fileTree = new SingleIncludePatternFileTree(tempDir.testDirectory, "dir?\\file1")

        when:
        fileTree.visit(visitor)

        then:
        1 * visitor.visitDir({ it.file == tempDir.file("dir1") })
        1 * visitor.visitDir({ it.file == tempDir.file("dir2") })
        1 * visitor.visitDir({ it.file == tempDir.file("dir3") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir1/file1") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir2/file1") })
        1 * visitor.visitFile({ it.file == tempDir.file("dir3/file1") })
        0 * _
    }

    def "display name"() {
        fileTree = new SingleIncludePatternFileTree(tempDir.testDirectory, "dir?/file1")

        expect:
        fileTree.displayName == "directory '$tempDir.testDirectory' include 'dir?/file1'"
    }
}
