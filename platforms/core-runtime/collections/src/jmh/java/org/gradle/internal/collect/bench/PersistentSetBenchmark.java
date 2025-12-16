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

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.gradle.internal.collect.PersistentSet;
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

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.TreeSet;

@Fork(1)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
@State(Scope.Benchmark)
public class PersistentSetBenchmark {

    public enum SetType {
        HashSet(true, new HashSetSetProtocol()),
        TreeSet(true, new TreeSetSetProtocol()),
        fastutil(true, new FastutilSetProtocol()),
        guava(false, new GuavaSetProtocol()),
        // To avoid imposing the required dependencies on every Gradle developer
        // we keep these implementations commented out.
        // Uncomment the dependencies on build.gradle.kts then
        // uncomment the desired implementation(s) here and at the bottom of this file.
//        capsule(false, new CapsuleSetProtocol()),
//        clojure(false, new ClojureSetProtocol()),
//        scala(false, new ScalaSetProtocol()),
//        pcollections(false, new PCollectionsSetProtocol()),
        gradle(false, new GradleSetProtocol());

        final boolean mutable;
        final SetProtocol protocol;

        SetType(boolean mutable, SetProtocol protocol) {
            this.mutable = mutable;
            this.protocol = protocol;
        }
    }

    //    @Param({"16", "1024", "65536"})
//    @Param({"16", "64", "512", "1024", "65536"})
//    @Param({"16", "64", "512"})
    @Param({"64", "1024"})
    int size;

    //    @Param({"gradle", "fastutil", "HashSet", "TreeSet", "guava", "capsule", "clojure", "scala"})
//    @Param({"HashSet", "gradle"})
//    @Param({"gradle", "capsule"})
//    @Param({"gradle"})
//    @Param({"gradle", "scala", "clojure", "capsule", "guava"})
//    @Param({"gradle", "clojure", "scala", "capsule"})
//    @Param({"gradle", "pcollections"})
    @Param({"gradle", "guava"})
    SetType type;
    SetProtocol protocol;
    SetFixture fixture;
    Object set;

    @Setup(Level.Iteration)
    public void setup() throws CloneNotSupportedException {

        fixture = SetFixture.of(size);

        protocol = type.protocol;
        set = protocol.newInstance();
        for (Object key : fixture.present()) {
            set = protocol.insert(set, key);
        }
    }

    @Benchmark
    public void constructionInBulk(Blackhole blackhole) {
        blackhole.consume(protocol.copyOf(fixture.present()));
    }

    @Benchmark
    public void constructionOneByOne(Blackhole blackhole) {
        Object set = protocol.newInstance();
        for (Object key : fixture.present()) {
            set = protocol.insert(set, key);
        }
        blackhole.consume(set);
    }

    @Benchmark
    public void iteration(Blackhole blackhole) {
        for (Object key : protocol.iterable(set)) {
            blackhole.consume(key);
        }
    }

    @Benchmark
    public void randomInsert(Blackhole blackhole) {
        if (type.mutable) {
            // non-applicable
            throw new UnsupportedOperationException("Operation not supported on mutable collections.");
        }
        blackhole.consume(protocol.insert(set, fixture.randomAbsent()));
    }

    @Benchmark
    public void removePresent(Blackhole blackhole) {
        if (type.mutable) {
            // non-applicable
            throw new UnsupportedOperationException("Operation not supported on mutable collections.");
        }
        blackhole.consume(protocol.remove(set, fixture.randomPresent()));
    }

    @Benchmark
    public void removeAbsent(Blackhole blackhole) {
        if (type.mutable) {
            // non-applicable
            throw new UnsupportedOperationException("Operation not supported on mutable collections.");
        }
        blackhole.consume(protocol.remove(set, fixture.randomAbsent()));
    }

    @Benchmark
    public void removeMany(Blackhole blackhole) {
        blackhole.consume(protocol.removeAll(set, fixture.present().subList(0, size >> 2)));
    }

    @Benchmark
    public void randomLookup(Blackhole blackhole) {
        blackhole.consume(protocol.contains(set, fixture.randomPresent()));
        blackhole.consume(protocol.contains(set, fixture.randomAbsent()));
    }

    @Benchmark
    public void containsAll(Blackhole blackhole) {
        for (Object key : fixture.present()) {
            blackhole.consume(protocol.contains(set, key));
        }
    }

    @Benchmark
    public void containsNone(Blackhole blackhole) {
        for (Object key : fixture.absent()) {
            blackhole.consume(protocol.contains(set, key));
        }
    }

    interface SetProtocol {

        Object newInstance();

        Object copyOf(Collection<Object> keys);

        Object insert(Object set, Object key);

        default Object remove(Object set, Object key) {
            throw new UnsupportedOperationException();
        }

        default Object removeAll(Object set, Iterable<Object> keys) {
            throw new UnsupportedOperationException();
        }

        boolean contains(Object set, Object key);

        @SuppressWarnings("unchecked")
        default Iterable<Object> iterable(Object set) {
            return (Iterable<Object>) set;
        }
    }

    @SuppressWarnings("unchecked")
    static class GradleSetProtocol implements SetProtocol {

        @Override
        public Object newInstance() {
            return PersistentSet.of();
        }

        @Override
        public Object copyOf(Collection<Object> keys) {
            return PersistentSet.copyOf(keys);
        }

        @Override
        public Object insert(Object set, Object key) {
            return ((PersistentSet<Object>) set).plus(key);
        }

        @Override
        public Object remove(Object set, Object key) {
            return ((PersistentSet<Object>) set).minus(key);
        }

        @Override
        public Object removeAll(Object set, Iterable<Object> keys) {
            return ((PersistentSet<Object>) set).minusAll(keys);
        }

        @Override
        public boolean contains(Object set, Object key) {
            return ((PersistentSet<Object>) set).contains(key);
        }
    }

    @SuppressWarnings("unchecked")
    static class GuavaSetProtocol implements SetProtocol {

        @Override
        public Object newInstance() {
            return ImmutableSet.of();
        }

        @Override
        public Object copyOf(Collection<Object> keys) {
            return ImmutableSet.copyOf(keys);
        }

        @Override
        public Object insert(Object set, Object key) {
            ImmutableSet<Object> typed = (ImmutableSet<Object>) set;
            return ImmutableSet.builderWithExpectedSize(typed.size() + 1)
                .addAll(typed)
                .add(key)
                .build();
        }

        @Override
        public Object remove(Object set, Object key) {
            ImmutableSet<Object> typed = (ImmutableSet<Object>) set;
            if (!typed.contains(key)) {
                return typed;
            }
            ImmutableSet.Builder<Object> builder = ImmutableSet.builderWithExpectedSize(typed.size() - 1);
            for (Object k : typed) {
                if (!Objects.equals(k, key)) {
                    builder.add(k);
                }
            }
            return builder.build();
        }

        @Override
        public boolean contains(Object set, Object key) {
            return ((ImmutableSet<Object>) set).contains(key);
        }
    }

    @SuppressWarnings("unchecked")
    static class HashSetSetProtocol implements SetProtocol {

        @Override
        public Object newInstance() {
            return new HashSet<>();
        }

        @Override
        public Object copyOf(Collection<Object> keys) {
            return new HashSet<>(keys);
        }

        @Override
        public Object insert(Object set, Object key) {
            ((HashSet<Object>) set).add(key);
            return set;
        }

        @Override
        public boolean contains(Object set, Object key) {
            return ((HashSet<Object>) set).contains(key);
        }
    }

    @SuppressWarnings("unchecked")
    static class TreeSetSetProtocol implements SetProtocol {

        @Override
        public Object newInstance() {
            return new TreeSet<>();
        }

        @Override
        public Object copyOf(Collection<Object> keys) {
            return new TreeSet<>(keys);
        }

        @Override
        public Object insert(Object set, Object key) {
            ((TreeSet<Object>) set).add(key);
            return set;
        }

        @Override
        public boolean contains(Object set, Object key) {
            return ((TreeSet<Object>) set).contains(key);
        }
    }

    @SuppressWarnings("unchecked")
    static class FastutilSetProtocol implements SetProtocol {

        @Override
        public Object newInstance() {
            return new ObjectOpenHashSet<>();
        }

        @Override
        public Object copyOf(Collection<Object> keys) {
            return new ObjectOpenHashSet<>(keys);
        }

        @Override
        public Object insert(Object set, Object key) {
            ((ObjectOpenHashSet<Object>) set).add(key);
            return set;
        }

        @Override
        public boolean contains(Object set, Object key) {
            return ((ObjectOpenHashSet<Object>) set).contains(key);
        }
    }

//    @SuppressWarnings("unchecked")
//    static class CapsuleSetProtocol implements SetProtocol {
//
//        @Override
//        public Object newInstance() {
//            return io.usethesource.capsule.Set.Immutable.of();
//        }
//
//        @Override
//        public Object copyOf(Collection<Object> keys) {
//            io.usethesource.capsule.Set.Transient<Object> builder = io.usethesource.capsule.Set.Immutable.of().asTransient();
//            for (Object key : keys) {
//                builder.__insert(key);
//            }
//            return builder.freeze();
//        }
//
//        @Override
//        public Object insert(Object set, Object key) {
//            return ((io.usethesource.capsule.Set.Immutable<Object>) set).__insert(key);
//        }
//
//        @Override
//        public Object remove(Object set, Object key) {
//            return ((io.usethesource.capsule.Set.Immutable<Object>) set).__remove(key);
//        }
//
//        @Override
//        public Object removeAll(Object set, Iterable<Object> keys) {
//            Set.Transient<Object> builder = ((io.usethesource.capsule.Set.Immutable<Object>) set).asTransient();
//            for (Object key : keys) {
//                builder.__remove(key);
//            }
//            return builder.freeze();
//        }
//
//        @Override
//        public boolean contains(Object set, Object key) {
//            return ((io.usethesource.capsule.Set.Immutable<Object>) set).contains(key);
//        }
//    }

//    @SuppressWarnings("unchecked")
//    static class ClojureSetProtocol implements SetProtocol {
//
//        @Override
//        public Object newInstance() {
//            return com.github.krukow.clj_ds.Persistents.hashSet();
//        }
//
//        @Override
//        public Object copyOf(Collection<Object> keys) {
//            com.github.krukow.clj_ds.TransientCollection<Object> builder = com.github.krukow.clj_ds.Persistents.hashSet().asTransient();
//            for (Object key : keys) {
//                builder = builder.plus(key);
//            }
//            return builder.persist();
//        }
//
//        @Override
//        public Object insert(Object set, Object key) {
//            return ((com.github.krukow.clj_ds.PersistentSet<Object>) set).plus(key);
//        }
//
//        @Override
//        public Object remove(Object set, Object key) {
//            return ((com.github.krukow.clj_ds.PersistentSet<Object>) set).minus(key);
//        }
//
//        @Override
//        public Object removeAll(Object set, Iterable<Object> keys) {
//            com.github.krukow.clj_ds.PersistentSet<Object> result = (com.github.krukow.clj_ds.PersistentSet<Object>) set;
//            for (Object key : keys) {
//                result = result.minus(key);
//            }
//            return result;
//        }
//
//        @Override
//        public boolean contains(Object set, Object key) {
//            return ((com.github.krukow.clj_ds.PersistentSet<Object>) set).contains(key);
//        }
//    }

//    @SuppressWarnings("unchecked")
//    static class ScalaSetProtocol implements SetProtocol {
//
//        @Override
//        public Object newInstance() {
//            return scala.collection.immutable.HashSet$.MODULE$.empty();
//        }
//
//        @Override
//        public Object copyOf(Collection<Object> keys) {
//            return scala.collection.immutable.HashSet$.MODULE$.from(
//                scala.jdk.CollectionConverters$.MODULE$.IterableHasAsScala(keys).asScala()
//            );
//        }
//
//        @Override
//        public Iterable<Object> iterable(Object set) {
//            return scala.jdk.CollectionConverters$.MODULE$.IterableHasAsJava((scala.collection.Iterable<Object>) set).asJava();
//        }
//
//        @Override
//        public Object insert(Object set, Object key) {
//            return ((scala.collection.immutable.HashSet<Object>) set).incl(key);
//        }
//
//        @Override
//        public Object remove(Object set, Object key) {
//            return ((scala.collection.immutable.HashSet<Object>) set).excl(key);
//        }
//
//        @Override
//        public boolean contains(Object set, Object key) {
//            return ((scala.collection.immutable.HashSet<Object>) set).contains(key);
//        }
//    }

//    @SuppressWarnings("unchecked")
//    static class PCollectionsSetProtocol implements SetProtocol {
//
//        @Override
//        public Object newInstance() {
//            return org.pcollections.HashTreePSet.empty();
//        }
//
//        @Override
//        public Object copyOf(Collection<Object> keys) {
//            return org.pcollections.HashTreePSet.from(keys);
//        }
//
//        @Override
//        public Object insert(Object set, Object key) {
//            return ((org.pcollections.PSet<Object>) set).plus(key);
//        }
//
//        @Override
//        public Object remove(Object set, Object key) {
//            return ((org.pcollections.PSet<Object>) set).minus(key);
//        }
//
//        @Override
//        public Object removeAll(Object set, Iterable<Object> keys) {
//            return ((org.pcollections.PSet<Object>) set).minusAll((Collection<Object>) keys);
//        }
//
//        @Override
//        public boolean contains(Object set, Object key) {
//            return ((org.pcollections.PSet<Object>) set).contains(key);
//        }
//    }
}
