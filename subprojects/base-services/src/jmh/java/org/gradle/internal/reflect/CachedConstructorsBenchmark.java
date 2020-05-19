/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.reflect;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;

@Fork(4)
@Threads(2)
@Warmup(iterations = 10)
@State(Scope.Benchmark)
public class CachedConstructorsBenchmark {

    private final static Class<?>[] CLAZZ_ARRAY = new Class<?>[]{ArrayList.class, LinkedList.class, String.class, HashMap.class};
    private final static int ARR_LEN = 1024;
    private final static Random RANDOM = new Random();
    public static final Class<?>[] EMPTY = new Class<?>[0];

    private final DirectInstantiator.ConstructorCache cache = new DirectInstantiator.ConstructorCache();
    private Class<?>[] randomClasses;

    @Setup(Level.Iteration)
    public void configClasses() {
        randomClasses = new Class<?>[ARR_LEN];
        for (int i = 0; i < randomClasses.length; i++) {
            randomClasses[i] = CLAZZ_ARRAY[RANDOM.nextInt(CLAZZ_ARRAY.length)];
        }
    }

    private int i;

    @Benchmark
    public void uncached(Blackhole bh) {
        bh.consume(randomClasses[++i % ARR_LEN].getConstructors());
    }

    @Benchmark
    public void cached(Blackhole bh) {
        bh.consume(cache.get(randomClasses[++i % ARR_LEN], EMPTY));
    }
}
