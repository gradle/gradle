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

import org.gradle.api.GradleException
import org.gradle.internal.serialize.graph.CloseableReadContext
import org.gradle.internal.serialize.graph.CloseableWriteContext
import org.gradle.internal.serialize.graph.FilePrefixedTree
import org.gradle.internal.serialize.graph.FilePrefixedTree.Node
import org.gradle.internal.serialize.graph.FileSystemTreeDecoder
import org.gradle.internal.serialize.graph.FileSystemTreeEncoder
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class DefaultFileSystemTreeEncoder(
    private val globalContext: CloseableWriteContext,
    private val prefixedTree: FilePrefixedTree
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
        writeSmallInt(node.children.size)
        node.children.forEach { child ->
            writeString(child.key)
            writePrefixedTreeNode(child.value)
        }
    }
}

class DefaultFileSystemTreeDecoder(
    private val globalContext: CloseableReadContext,
    private val prefixedTree: FilePrefixedTree
) : FileSystemTreeDecoder {

    private val files = mutableMapOf<Int, File>()

    override fun readFile(readContext: ReadContext): File {
        val index = readContext.readSmallInt()
        val file = files[index] ?: throw GradleException("Cannot read a file with index=$index")
        return file
    }

    override suspend fun readTree() {
        files.putAll(prefixedTree.buildIndexes(globalContext.readPrefixedTreeNode()))
    }

    override fun close() {
        globalContext.close()
    }

    private fun ReadContext.readPrefixedTreeNode(): Node {
        val index = readNullableSmallInt()
        val segment = readString()
        val childrenCount = readSmallInt()
        val children = ConcurrentHashMap<String, Node>()
        repeat(childrenCount) {
            children[readString()] = readPrefixedTreeNode()
        }
        return Node(index, segment, children)
    }
}
