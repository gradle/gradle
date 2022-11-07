/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.Action
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

@UsesNativeServices
class GeneratedSingletonFileTreeSpec extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def fileSystem = Stub(FileSystem)

    def "visiting creates file if visitor queries the file location"() {
        def contentWriter = Mock(Action)
        def generationListener = Mock(Action)
        def tmpDir = tmpDir.createDir("dir")
        def generatedFile = tmpDir.file("file.bin")
        def visitor = Mock(FileVisitor)

        def fileTree = new GeneratedSingletonFileTree({ tmpDir }, "file.bin", generationListener, contentWriter, fileSystem)

        when:
        fileTree.visit(visitor)

        then:
        1 * visitor.visitFile(_) >> { FileVisitDetails details ->
            assert details.path == "file.bin"
            assert !generatedFile.exists()
            assert details.file == generatedFile
            assert generatedFile.text == "contents!"
        }
        1 * generationListener.execute(generatedFile)
        1 * contentWriter.execute(_) >> { OutputStream outputStream ->
            outputStream << "contents!"
        }
        0 * _
    }

    def "visiting does not create file if visitor does not query the file location"() {
        def contentWriter = Mock(Action)
        def tmpDir = tmpDir.createDir("dir")
        def generatedFile = tmpDir.file("file.bin")
        def generationListener = Mock(Action)
        def visitor = Mock(FileVisitor)

        def fileTree = new GeneratedSingletonFileTree({ tmpDir }, "file.bin", generationListener, contentWriter, fileSystem)

        when:
        fileTree.visit(visitor)

        then:
        1 * visitor.visitFile(_) >> { FileVisitDetails details ->
            assert details.path == "file.bin"
            assert !generatedFile.exists()
        }
        0 * _
    }

    def "visiting structure eagerly generates content when listener requests content but does not use it"() {
        def owner = Stub(FileTreeInternal)
        def visitor = Mock(MinimalFileTree.MinimalFileTreeStructureVisitor)
        def tmpDir = tmpDir.createDir("dir")
        def generatedFile = tmpDir.file("file.bin")
        def generationListener = Mock(Action)
        def contentWriter = Mock(Action)

        def fileTree = new GeneratedSingletonFileTree({ tmpDir }, "file.bin", generationListener, contentWriter, fileSystem)

        when:
        fileTree.visitStructure(visitor, owner)

        then:
        1 * visitor.visitFileTree(generatedFile, _, owner) >> { d, p, t ->
            assert p.isEmpty()
            assert generatedFile.isFile()
            assert generatedFile.text == "contents!"
        }
        1 * generationListener.execute(generatedFile)
        1 * contentWriter.execute(_) >> { OutputStream outputStream ->
            outputStream << "contents!"
        }
        0 * _
    }
}
