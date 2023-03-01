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

package org.gradle.api.internal.file.archive

import org.gradle.api.file.FileVisitor
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.file.collections.DirectoryFileTree
import org.gradle.api.internal.file.collections.MinimalFileTree
import org.gradle.api.provider.Provider
import org.gradle.cache.internal.DecompressionCache
import org.gradle.cache.internal.TestCaches
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

/**
 * Tests core functionality in {@link AbstractArchiveFileTree} using a minimal test implementation.
 */
class AbstractArchiveFileTreeSpec extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def "visits structure when backing file is known"() {
        def owner = Stub(FileTreeInternal)
        def visitor = Mock(MinimalFileTree.MinimalFileTreeStructureVisitor)
        def backingFile = tmpDir.createFile("thing.bin")

        def fileTree = new TestArchiveFileTree(TestCaches.decompressionCache(tmpDir.createDir("cache-dir")), backingFile)

        when:
        fileTree.visitStructure(visitor, owner)

        then:
        1 * visitor.visitFileTreeBackedByFile(backingFile, owner, fileTree)
        0 * _
    }

    static class TestArchiveFileTree extends AbstractArchiveFileTree {
        File backingFile
        final String displayName = "<display>"

        TestArchiveFileTree(DecompressionCache decompressionCache, File backingFile) {
            super(decompressionCache)
            this.backingFile = backingFile
        }

        @Override
        DirectoryFileTree getMirror() {
            throw new UnsupportedOperationException()
        }

        @Override
        void visit(FileVisitor visitor) {
            throw new UnsupportedOperationException()
        }

        @Override
        protected Provider<File> getBackingFileProvider() {
            TestUtil.providerFactory().provider { backingFile }
        }
    }
}
