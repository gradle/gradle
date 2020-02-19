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
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.tasks.util.PatternSet
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

@UsesNativeServices
class GeneratedSingletonFileTreeSpec extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def "visiting structure generates content when listener requests it"() {
        def owner = Stub(FileTreeInternal)
        def visitor = Mock(FileCollectionStructureVisitor)
        def tmpDir = tmpDir.createDir("dir")
        def patterns = Stub(PatternSet)
        def contentWriter = Mock(Action)

        def fileTree = new GeneratedSingletonFileTree({ tmpDir }, "file.bin", patterns, Stub(GeneratedSingletonFileTree.FileGenerationListener), contentWriter)

        when:
        fileTree.visitStructure(visitor, owner)

        then:
        1 * visitor.prepareForVisit(fileTree) >> FileCollectionStructureVisitor.VisitType.Visit
        1 * contentWriter.execute(_) >> { OutputStream outputStream ->
            outputStream << "contents!"
        }
        1 * visitor.visitFileTree(tmpDir.file("file.bin"), patterns, owner) >> { file, p, o ->
            assert file.isFile()
            assert file.text == "contents!"
        }
        0 * _
    }

    def "visiting structure does not generate content when listener does not need it"() {
        def owner = Stub(FileTreeInternal)
        def visitor = Mock(FileCollectionStructureVisitor)
        def tmpDir = tmpDir.createDir("dir")
        def patterns = Stub(PatternSet)
        def contentWriter = Mock(Action)

        def fileTree = new GeneratedSingletonFileTree({ tmpDir }, "file.bin", patterns, Stub(GeneratedSingletonFileTree.FileGenerationListener), contentWriter)

        when:
        fileTree.visitStructure(visitor, owner)

        then:
        1 * visitor.prepareForVisit(fileTree) >> FileCollectionStructureVisitor.VisitType.NoContents
        1 * visitor.visitCollection(fileTree, [])
        0 * _
    }

}
