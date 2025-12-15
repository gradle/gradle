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

package org.gradle.internal.collect.bench;

import com.google.common.collect.ImmutableList;
import org.gradle.internal.collect.PersistentArray;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Fork(1)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
@State(Scope.Benchmark)
public class PersistentArrayBenchmark {

    public enum ArrayType {
        ArrayList(true, new ArrayListArrayProtocol()),
        CopyOnWriteArrayList(true, new CopyOnWriteArrayListArrayProtocol()),
        guava(false, new GuavaArrayProtocol()),
        // To avoid imposing the required dependencies on every Gradle developer
        // we keep these implementations commented out.
        // Uncomment the dependencies on build.gradle.kts then
        // uncomment the desired implementation(s) here and at the bottom of this file.
//        capsule(false, new CapsuleArrayProtocol()),
//        clojure(false, new ClojureArrayProtocol()),
//        scala(false, new ScalaArrayProtocol()),
        gradle(false, new GradleArrayProtocol());

        final boolean mutable;
        final ArrayProtocol protocol;

        ArrayType(boolean mutable, ArrayProtocol protocol) {
            this.mutable = mutable;
            this.protocol = protocol;
        }
    }

    Random random;

    //    @Param({"16", "64", "512", "4096", "65536"})
//    @Param({"1024"})
    @Param({"64", "1024"})
    int size;

//    @Param({"gradle", "clojure"})
//    @Param({"gradle", "scala", "clojure"})
//    @Param({"gradle", "guava", "ArrayList", "CopyOnWriteArrayList"})
//    @Param({"gradle"})
    @Param({"gradle", "guava"})
    ArrayType type;
    ArrayProtocol protocol;
    Object array;

    private List<Object> present;

    @Setup(Level.Iteration)
    public void setup() throws CloneNotSupportedException {
        random = new Random(42);

        present = random.ints(size).boxed().collect(Collectors.toList());

        protocol = type.protocol;
        array = protocol.newInstance();
        for (Object key : present) {
            array = protocol.append(array, key);
        }
    }

    @Benchmark
    public void append(Blackhole blackhole) {
        if (type.mutable) {
            // non-applicable
            throw new UnsupportedOperationException("Operation not supported on mutable collections.");
        }
        blackhole.consume(protocol.append(array, random.nextInt()));
    }

    @Benchmark
    public void constructionOneByOne(Blackhole blackhole) {
        Object array = protocol.newInstance();
        for (Object key : present) {
            array = protocol.append(array, key);
        }
        blackhole.consume(array);
    }

    @Benchmark
    public void randomAccess(Blackhole blackhole) {
        blackhole.consume(protocol.get(array, random.nextInt(size)));
    }

    @Benchmark
    public void iteration(Blackhole blackhole) {
        for (Object value : protocol.iterable(array)) {
            blackhole.consume(value);
        }
    }

    @Benchmark
    public void iterationByIndex(Blackhole blackhole) {
        for (int i = present.size() - 1; i >= 0; i--) {
            blackhole.consume(protocol.get(array, i));
        }
    }

    interface ArrayProtocol {

        Object newInstance();

        Object append(Object array, Object key);

        Object get(Object array, int index);

        @SuppressWarnings("unchecked")
        default Iterable<Object> iterable(Object array) {
            return (Iterable<Object>) array;
        }
    }

    @SuppressWarnings("unchecked")
    static class GradleArrayProtocol implements ArrayProtocol {

        @Override
        public Object newInstance() {
            return PersistentArray.of();
        }

        @Override
        public Object append(Object array, Object key) {
            return ((PersistentArray<Object>) array).plus(key);
        }

        @Override
        public Object get(Object array, int index) {
            return ((PersistentArray<Object>) array).get(index);
        }
    }

    @SuppressWarnings("unchecked")
    static class GuavaArrayProtocol implements ArrayProtocol {

        @Override
        public Object newInstance() {
            return ImmutableList.of();
        }

        @Override
        public Object append(Object array, Object key) {
            ImmutableList<Object> typed = (ImmutableList<Object>) array;
            return ImmutableList.builderWithExpectedSize(typed.size() + 1)
                .addAll(typed)
                .add(key)
                .build();
        }

        @Override
        public Object get(Object array, int index) {
            return ((ImmutableList<Object>) array).contains(index);
        }
    }

    @SuppressWarnings("unchecked")
    static class ArrayListArrayProtocol implements ArrayProtocol {

        @Override
        public Object newInstance() {
            return new ArrayList<Object>();
        }

        @Override
        public Object append(Object array, Object key) {
            ((ArrayList<Object>) array).add(key);
            return array;
        }

        @Override
        public Object get(Object array, int index) {
            return ((ArrayList<Object>) array).get(index);
        }
    }

    @SuppressWarnings("unchecked")
    static class CopyOnWriteArrayListArrayProtocol implements ArrayProtocol {

        @Override
        public Object newInstance() {
            return new CopyOnWriteArrayList<>();
        }

        @Override
        public Object append(Object array, Object key) {
            ((CopyOnWriteArrayList<Object>) array).add(key);
            return array;
        }

        @Override
        public Object get(Object array, int index) {
            return ((CopyOnWriteArrayList<Object>) array).get(index);
        }
    }

    // simulate an array using Map.Immutable<Integer, Object>
//    @SuppressWarnings("unchecked")
//    static class CapsuleArrayProtocol implements ArrayProtocol {
//
//        @Override
//        public Object newInstance() {
//            return io.usethesource.capsule.Map.Immutable.of();
//        }
//
//        @Override
//        public Object append(Object array, Object key) {
//            io.usethesource.capsule.Map.Immutable<Integer, Object> map = (io.usethesource.capsule.Map.Immutable<Integer, Object>) array;
//            return map.__put(map.size(), key);
//        }
//
//        @Override
//        public Object get(Object array, int index) {
//            return ((io.usethesource.capsule.Map.Immutable<Integer, Object>) array).get(index);
//        }
//    }

//    @SuppressWarnings("unchecked")
//    static class ClojureArrayProtocol implements ArrayProtocol {
//
//        @Override
//        public Object newInstance() {
//            return com.github.krukow.clj_ds.Persistents.vector();
//        }
//
//        @Override
//        public Object append(Object array, Object key) {
//            return ((com.github.krukow.clj_ds.PersistentVector<Object>) array).plus(key);
//        }
//
//        @Override
//        public Object get(Object array, int index) {
//            return ((com.github.krukow.clj_ds.PersistentVector<Object>) array).get(index);
//        }
//    }

//    @SuppressWarnings("unchecked")
//    static class ScalaArrayProtocol implements ArrayProtocol {
//
//        @Override
//        public Object newInstance() {
//            return scala.collection.immutable.Vector$.MODULE$.empty();
//        }
//
//        @Override
//        public Iterable<Object> iterable(Object array) {
//            return scala.jdk.CollectionConverters$.MODULE$.IterableHasAsJava((scala.collection.Iterable<Object>) array).asJava();
//        }
//
//        @Override
//        public Object append(Object array, Object key) {
//            return ((scala.collection.immutable.Vector<Object>) array).$colon$plus(key);
//        }
//
//        @Override
//        public Object get(Object array, int index) {
//            return ((scala.collection.immutable.Vector<Object>) array).apply(index);
//        }
//    }

}
