/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.cc.impl.serialize

import org.gradle.internal.serialize.graph.CloseableReadContext
import org.gradle.internal.serialize.graph.CloseableWriteContext
import org.gradle.internal.serialize.graph.FileSystemTreeDecoder
import org.gradle.internal.serialize.graph.FileSystemTreeEncoder
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.StringPrefixedTree
import org.gradle.internal.serialize.graph.StringPrefixedTree.Node
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.readCollection
import org.gradle.internal.serialize.graph.readCollectionInto
import org.gradle.internal.serialize.graph.writeCollection
import java.io.File

class DefaultFileSystemTreeEncoder(
    private val globalContext: CloseableWriteContext,
    private val prefixedTree: StringPrefixedTree
) : FileSystemTreeEncoder {

    override fun writeFile(writeContext: WriteContext, file: File) {
        writeContext.writeSmallInt(prefixedTree.insert(file))
    }

    override suspend fun writeTree() {
        globalContext.writePrefixedTreeNode(prefixedTree.compress())
    }

    override fun close() {
        globalContext.close()
    }

    private fun WriteContext.writePrefixedTreeNode(node: Node) {
        writeNullableSmallInt(node.index)
        writeString(node.segment)
        writeCollection(node.children) { writePrefixedTreeNode(it) }
    }
}

class DefaultFileSystemTreeDecoder(
    private val globalContext: CloseableReadContext,
    private val prefixedTree: StringPrefixedTree
) : FileSystemTreeDecoder {

    private val files = mutableMapOf<Int, File>()

    override fun readFile(readContext: ReadContext): File = files[readContext.readSmallInt()]!!

    override suspend fun readTree() {
        files.putAll(prefixedTree.buildIndexes(globalContext.readPrefixedTreeNode()))
    }

    override fun close() {
        globalContext.close()
    }

    private fun ReadContext.readPrefixedTreeNode(): Node = Node(
        readNullableSmallInt(),
        readString(),
        readCollectionInto({ mutableListOf() }) { readPrefixedTreeNode() }
    )
}
