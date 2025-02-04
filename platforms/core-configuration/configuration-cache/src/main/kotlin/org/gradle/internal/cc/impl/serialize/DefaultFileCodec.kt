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
import org.gradle.internal.serialize.graph.FileDecoder
import org.gradle.internal.serialize.graph.FileEncoder
import org.gradle.internal.serialize.graph.FilePrefixedTree
import org.gradle.internal.serialize.graph.FilePrefixedTree.Node
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread

private const val EOF = (-1).toByte()
private const val NODE_FINAL_START = 1.toByte()
private const val NODE_INTERMEDIATE_START = 2.toByte()
private const val NODE_END = 3.toByte()

class DefaultFileEncoder(
    private val globalContext: CloseableWriteContext,
    private val prefixedTree: FilePrefixedTree
) : FileEncoder {

    override fun writeFile(writeContext: WriteContext, file: File) {
        writeContext.writeSmallInt(prefixedTree.insert(file))
    }

    override fun close() {
        globalContext.use {
            it.writePrefixedTreeNode(prefixedTree.compress(), null)
            it.writeByte(EOF)
        }
    }

    private fun WriteContext.writePrefixedTreeNode(node: Node, parent: Node?) {
        val nodeHeader =
            if (node.isFinal) NODE_FINAL_START
            else NODE_INTERMEDIATE_START

        writeByte(nodeHeader)
        writeSmallInt(node.index - (parent?.index ?: 0)) // delta encoding
        writeString(node.segment)
        node.children.values.forEach { child ->
            writePrefixedTreeNode(child, node)
        }
        writeByte(NODE_END)
    }
}

class DefaultFileDecoder(
    private val globalContext: CloseableReadContext,
) : FileDecoder {

    private
    class FutureFile {

        private
        val latch = CountDownLatch(1)

        private
        var file: File? = null

        fun complete(file: File) {
            this.file = file
            latch.countDown()
        }

        fun get(): File {
            if (!latch.await(1, TimeUnit.MINUTES)) {
                throw TimeoutException("Timeout while waiting for file")
            }
            return file!!
        }
    }

    private val files = ConcurrentHashMap<Int, Any>()

    @Suppress("LoopWithTooManyJumpStatements")
    private
    val reader = thread(isDaemon = true) {
        val segments = HashMap<Int, String>()
        val stack = ArrayDeque<Int>()

        globalContext.use { context ->
            while (true) {
                val nodeHeader = context.readByte()
                if (nodeHeader == EOF) break

                if (nodeHeader == NODE_END) {
                    stack.removeLastOrNull() ?: break
                    continue
                }

                val deltaId = context.readSmallInt()
                val segment = context.readString()
                val parent = stack.lastOrNull()
                val isFinal = nodeHeader == NODE_FINAL_START
                val id = deltaId + (parent ?: 0)

                val path = parent?.let {
                    val parentSegment = segments[it]
                    if (parentSegment.isNullOrEmpty()) segment else "$parentSegment${File.separatorChar}$segment"
                } ?: segment

                segments[id] = path

                if (isFinal) {
                    files.compute(id) { _, value ->
                        val file = File(path)
                        when (value) {
                            is FutureFile -> value.complete(file)
                            else -> require(value == null)
                        }
                        file
                    }
                }

                stack.addLast(id)
            }
        }
    }

    override fun readFile(readContext: ReadContext): File =
        when (val file = files.computeIfAbsent(readContext.readSmallInt()) { FutureFile() }) {
            is FutureFile -> file.get()
            is File -> file
            else -> error("$file is unsupported")
        }

    override fun close() {
        reader.join(TimeUnit.MINUTES.toMillis(1))
    }
}
