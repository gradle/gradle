/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.cc.jmh

import org.openjdk.jmh.annotations.AuxCounters
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import org.openjdk.jmh.infra.Blackhole


/**
 * Auxiliary counter to track compressed byte sizes in JMH results.
 * The field name becomes the column header in results.csv.
 */
@State(Scope.Thread)
@AuxCounters(AuxCounters.Type.EVENTS)
open class ByteCounter {
    @Suppress("PropertyName")
    var `compressedSize`: Long = 0
}


@Fork(value = 2)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
@State(Scope.Benchmark)
open class CCRoundtripBenchmark {

    private
    lateinit var state: List<Peano>

    @Setup
    fun setup() {
        state = (1..1024).map { Peano.fromInt(1024) }
    }

    @Benchmark
    fun withoutCompression(bh: Blackhole, counter: ByteCounter) {
        val compressed = CCLoadScenarios.writeUncompressed(state)
        counter.`compressedSize` += compressed.size.toLong()
        bh.consume(CCLoadScenarios.readUncompressed(compressed))
    }

    @Benchmark
    fun withSnappyCompression(bh: Blackhole, counter: ByteCounter) {
        val compressed = CCLoadScenarios.writeWithSnappy(state)
        counter.`compressedSize` += compressed.size.toLong()
        bh.consume(CCLoadScenarios.readWithSnappy(compressed))
    }

    @Benchmark
    fun withGZIPCompression(bh: Blackhole, counter: ByteCounter) {
        val compressed = CCLoadScenarios.writeWithGZIP(state)
        counter.`compressedSize` += compressed.size.toLong()
        bh.consume(CCLoadScenarios.readWithGZIP(compressed))
    }

    @Benchmark
    fun withZstdCompression(bh: Blackhole, counter: ByteCounter) {
        val compressed = CCLoadScenarios.writeWithZstd(state)
        counter.`compressedSize` += compressed.size.toLong()
        bh.consume(CCLoadScenarios.readWithZstd(compressed))
    }

    @Benchmark
    fun withZstdLevel1(bh: Blackhole, counter: ByteCounter) {
        val compressed = CCLoadScenarios.writeWithZstdLevel(state, 1)
        counter.`compressedSize` += compressed.size.toLong()
        bh.consume(CCLoadScenarios.readWithZstdLevel(compressed))
    }

    @Benchmark
    fun withZstdLevel6(bh: Blackhole, counter: ByteCounter) {
        val compressed = CCLoadScenarios.writeWithZstdLevel(state, 6)
        counter.`compressedSize` += compressed.size.toLong()
        bh.consume(CCLoadScenarios.readWithZstdLevel(compressed))
    }

    @Benchmark
    fun withZstdLevel9(bh: Blackhole, counter: ByteCounter) {
        val compressed = CCLoadScenarios.writeWithZstdLevel(state, 9)
        counter.`compressedSize` += compressed.size.toLong()
        bh.consume(CCLoadScenarios.readWithZstdLevel(compressed))
    }

    @Benchmark
    fun withLz4Fast(bh: Blackhole, counter: ByteCounter) {
        val compressed = CCLoadScenarios.writeWithLz4Fast(state)
        counter.`compressedSize` += compressed.size.toLong()
        bh.consume(CCLoadScenarios.readWithLz4(compressed))
    }
}
