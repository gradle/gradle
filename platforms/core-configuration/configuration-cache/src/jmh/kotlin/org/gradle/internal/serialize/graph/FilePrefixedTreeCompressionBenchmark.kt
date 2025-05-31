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

package org.gradle.internal.serialize.graph

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import org.openjdk.jmh.infra.Blackhole
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit


@Fork(1)
@Warmup(iterations = 1)
@Measurement(iterations = 10)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
open class FilePrefixedTreeCompressionBenchmark {

    private var treeToCompress: FilePrefixedTree = FilePrefixedTree()
    private lateinit var fileTree: Path

    @Setup(Level.Trial)
    fun setup() {
        fileTree = Files.createTempDirectory("tree")
        generateFileTree(fileTree, 7, 7) // 137257 nodes
        Files.walk(fileTree).forEach { treeToCompress.insert(it.toFile()) }
    }

    @Setup(Level.Trial)
    fun tearDown() {
        fileTree.toFile().deleteRecursively()
    }

    // avgt   10  11.551 ± 0.110  ms/op
    @Benchmark
    fun prefixedTreeCompress(bh: Blackhole) {
        bh.consume(treeToCompress.compress())
    }
}
