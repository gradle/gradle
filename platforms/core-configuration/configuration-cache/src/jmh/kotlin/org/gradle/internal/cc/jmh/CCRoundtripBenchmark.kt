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

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import org.openjdk.jmh.infra.Blackhole


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
    fun withoutCompression(bh: Blackhole) {
        bh.consume(
            CCLoadScenarios.readUncompressed(
                CCLoadScenarios.writeUncompressed(
                    state
                )
            )
        )
    }

    @Benchmark
    fun withSnappyCompression(bh: Blackhole) {
        bh.consume(
            CCLoadScenarios.readWithSnappy(
                CCLoadScenarios.writeWithSnappy(
                    state
                )
            )
        )
    }

    @Benchmark
    fun withGZIPCompression(bh: Blackhole) {
        bh.consume(
            CCLoadScenarios.readWithGZIP(
                CCLoadScenarios.writeWithGZIP(
                    state
                )
            )
        )
    }
}
