/*
 * Copyright 2019 the original author or authors.
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
package org.gradle;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static java.util.concurrent.TimeUnit.SECONDS;

/*
 * Benchmark                     Mode  Cnt          Score         Error  Units
 * OptionalBenchmark.nullCheck  thrpt   20  107887111.061 ±  882182.482  ops/s
 * OptionalBenchmark.optional   thrpt   20   86746312.090 ± 1150860.296  ops/s
 **/
@Fork(2)
@Warmup(iterations = 10, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = SECONDS)
@State(Scope.Benchmark)
public class OptionalBenchmark {

    final Path path = Paths.get(".");
    final Path pathRoot = Paths.get(".").toAbsolutePath().getRoot();

    @Benchmark
    public void nullCheck(Blackhole bh) {
        bh.consume(nullCheck(path));
        bh.consume(nullCheck(pathRoot));
    }

    private static Object nullCheck(Path path) {
        Path fileName = path.getFileName();
        if (fileName != null) {
            return fileName.toString();
        } else {
            return "";
        }
    }

    @Benchmark
    public void optional(Blackhole bh) {
        bh.consume(optional(path));
        bh.consume(optional(pathRoot));
    }

    private static Object optional(Path path) {
        return Optional.ofNullable(path.getFileName())
            .map(Object::toString)
            .orElse("");
    }
}
