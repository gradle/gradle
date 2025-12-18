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

import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.gradle.internal.collect.PersistentMap;
import org.jspecify.annotations.Nullable;
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

import java.util.HashMap;
import java.util.TreeMap;

@Fork(1)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
@State(Scope.Benchmark)
public class PersistentMapBenchmark {

    public enum MapType {
        HashMap(true, new HashMapMapProtocol()),
        TreeMap(true, new TreeMapMapProtocol()),
        fastutil(true, new FastutilMapProtocol()),
        guava(false, new GuavaMapProtocol()),
        // To avoid imposing the required dependencies on every Gradle developer
        // we keep these implementations commented out.
        // Uncomment the dependencies on build.gradle.kts then
        // uncomment the desired implementation(s) here and at the bottom of this file.
//        capsule(false, new CapsuleMapProtocol()),
//        clojure(false, new ClojureMapProtocol()),
//        scala(false, new ScalaMapProtocol()),
//        pcollections(false, new PCollectionsMapProtocol()),
        gradle(false, new GradleMapProtocol());

        final boolean mutable;
        final MapProtocol protocol;

        MapType(boolean mutable, MapProtocol protocol) {
            this.mutable = mutable;
            this.protocol = protocol;
        }
    }

    //    @Param({"16", "64", "512", "1024", "65536"})
//    @Param({"16", "64", "512"})
    @Param({"64", "1024"})
    int size;

    //    @Param({"gradle", "capsule"})
//    @Param({"gradle", "clojure"})
//    @Param({"gradle", "scala", "clojure", "capsule"})
//    @Param({"gradle", "fastutil", "HashMap", "TreeMap", "guava", "capsule", "clojure", "scala"})
    @Param({"gradle", "guava"})
    MapType type;
    MapProtocol protocol;
    SetFixture fixture;
    Object map;

    @Setup(Level.Iteration)
    public void setup() throws CloneNotSupportedException {
        fixture = SetFixture.of(size);
        protocol = type.protocol;
        map = protocol.newInstance();
        for (Object key : fixture.present()) {
            map = protocol.put(map, key, key.toString());
        }
    }

    @Benchmark
    public void constructionOneByOne(Blackhole blackhole) {
        Object set = protocol.newInstance();
        for (Object key : fixture.present()) {
            set = protocol.put(set, key, key.toString());
        }
        blackhole.consume(set);
    }

    @Benchmark
    public void iteration(Blackhole blackhole) {
        for (Object key : protocol.iterable(map)) {
            blackhole.consume(key);
        }
    }

    @Benchmark
    public void putNew(Blackhole blackhole) {
        if (type.mutable) {
            // non-applicable
            throw new UnsupportedOperationException("Operation not supported on mutable collections.");
        }
        Object key = fixture.randomAbsent();
        blackhole.consume(protocol.put(map, key, key.toString()));
    }

    @Benchmark
    public void updateExisting(Blackhole blackhole) {
        if (type.mutable) {
            // non-applicable
            throw new UnsupportedOperationException("Operation not supported on mutable collections.");
        }
        Object key = fixture.randomPresent();
        blackhole.consume(protocol.put(map, key, key.toString() + "*"));
    }

    @Benchmark
    public void removePresent(Blackhole blackhole) {
        if (type.mutable) {
            // non-applicable
            throw new UnsupportedOperationException("Operation not supported on mutable collections.");
        }
        blackhole.consume(protocol.remove(map, fixture.randomPresent()));
    }

    @Benchmark
    public void removeAbsent(Blackhole blackhole) {
        if (type.mutable) {
            // non-applicable
            throw new UnsupportedOperationException("Operation not supported on mutable collections.");
        }
        blackhole.consume(protocol.remove(map, fixture.randomAbsent()));
    }

    @Benchmark
    public void containsKeyPresent(Blackhole blackhole) {
        blackhole.consume(protocol.containsKey(map, fixture.randomPresent()));
    }

    @Benchmark
    public void containsKeyAbsent(Blackhole blackhole) {
        blackhole.consume(protocol.containsKey(map, fixture.randomAbsent()));
    }

    @Benchmark
    public void randomLookup(Blackhole blackhole) {
        blackhole.consume(protocol.get(map, fixture.randomPresent()));
        blackhole.consume(protocol.get(map, fixture.randomAbsent()));
    }

    @Benchmark
    public void containsAll(Blackhole blackhole) {
        for (Object key : fixture.present()) {
            blackhole.consume(protocol.containsKey(map, key));
        }
    }

    @Benchmark
    public void containsNone(Blackhole blackhole) {
        for (Object key : fixture.absent()) {
            blackhole.consume(protocol.containsKey(map, key));
        }
    }

    interface MapProtocol {

        Object newInstance();

        Object put(Object map, Object key, Object val);

        default Object remove(Object map, Object key) {
            throw new UnsupportedOperationException();
        }

        @Nullable Object get(Object map, Object key);

        boolean containsKey(Object map, Object key);

        @SuppressWarnings("unchecked")
        default Iterable<Object> iterable(Object map) {
            return (Iterable<Object>) map;
        }
    }

    @SuppressWarnings("unchecked")
    static class GradleMapProtocol implements MapProtocol {

        @Override
        public Object newInstance() {
            return PersistentMap.of();
        }

        @Override
        public Object put(Object map, Object key, Object val) {
            return ((PersistentMap<Object, Object>) map).assoc(key, val);
        }

        @Override
        public Object remove(Object map, Object key) {
            return ((PersistentMap<Object, Object>) map).dissoc(key);
        }

        @Override
        public boolean containsKey(Object map, Object key) {
            return ((PersistentMap<Object, Object>) map).containsKey(key);
        }

        @Override
        public @Nullable Object get(Object map, Object key) {
            return ((PersistentMap<Object, Object>) map).get(key);
        }
    }

    @SuppressWarnings("unchecked")
    static class GuavaMapProtocol implements MapProtocol {

        @Override
        public Object newInstance() {
            return ImmutableMap.of();
        }

        @Override
        public Object put(Object map, Object key, Object val) {
            ImmutableMap<Object, Object> typed = (ImmutableMap<Object, Object>) map;
            return ImmutableMap.builderWithExpectedSize(typed.size() + 1)
                .putAll(typed)
                .put(key, val)
                .buildKeepingLast();
        }

        @Override
        public boolean containsKey(Object map, Object key) {
            return ((ImmutableMap<Object, Object>) map).containsKey(key);
        }

        @Override
        public Object get(Object map, Object key) {
            return ((ImmutableMap<Object, Object>) map).get(key);
        }
    }

    @SuppressWarnings("unchecked")
    static class HashMapMapProtocol implements MapProtocol {

        @Override
        public Object newInstance() {
            return new HashMap<>();
        }

        @Override
        public Object put(Object map, Object key, Object val) {
            ((HashMap<Object, Object>) map).put(key, val);
            return map;
        }

        @Override
        public boolean containsKey(Object map, Object key) {
            return ((HashMap<Object, Object>) map).containsKey(key);
        }

        @Override
        public Object get(Object map, Object key) {
            return ((HashMap<Object, Object>) map).get(key);
        }
    }

    @SuppressWarnings("unchecked")
    static class TreeMapMapProtocol implements MapProtocol {

        @Override
        public Object newInstance() {
            return new TreeMap<>();
        }

        @Override
        public Object put(Object map, Object key, Object val) {
            ((TreeMap<Object, Object>) map).put(key, val);
            return map;
        }

        @Override
        public boolean containsKey(Object map, Object key) {
            return ((TreeMap<Object, Object>) map).containsKey(key);
        }

        @Override
        public Object get(Object map, Object key) {
            return ((TreeMap<Object, Object>) map).get(key);
        }
    }

    @SuppressWarnings("unchecked")
    static class FastutilMapProtocol implements MapProtocol {

        @Override
        public Object newInstance() {
            return new Object2ObjectOpenHashMap<>();
        }

        @Override
        public Object put(Object map, Object key, Object val) {
            ((Object2ObjectOpenHashMap<Object, Object>) map).put(key, val);
            return map;
        }

        @Override
        public boolean containsKey(Object map, Object key) {
            return ((Object2ObjectOpenHashMap<Object, Object>) map).containsKey(key);
        }

        @Override
        public Object get(Object map, Object key) {
            return ((Object2ObjectOpenHashMap<Object, Object>) map).get(key);
        }
    }

//    @SuppressWarnings("unchecked")
//    static class CapsuleMapProtocol implements MapProtocol {
//
//        @Override
//        public Object newInstance() {
//            return io.usethesource.capsule.Map.Immutable.of();
//        }
//
//        @Override
//        public Object put(Object map, Object key, Object val) {
//            return ((io.usethesource.capsule.Map.Immutable<Object, Object>) map).__put(key, val);
//        }
//
//        @Override
//        public Object remove(Object map, Object key) {
//            return ((io.usethesource.capsule.Map.Immutable<Object, Object>) map).__remove(key);
//        }
//
//        @Override
//        public boolean containsKey(Object map, Object key) {
//            return ((io.usethesource.capsule.Map.Immutable<Object, Object>) map).containsKey(key);
//        }
//
//        @Override
//        public Object get(Object map, Object key) {
//            return ((io.usethesource.capsule.Map.Immutable<Object, Object>) map).get(key);
//        }
//
//        @Override
//        public Iterable<Object> iterable(Object map) {
//            return () -> {
//                java.util.Iterator<?> entryIterator = ((io.usethesource.capsule.Map.Immutable<Object, Object>) map).entryIterator();
//                return (java.util.Iterator<Object>) entryIterator;
//            };
//        }
//    }
//
//    @SuppressWarnings("unchecked")
//    static class ClojureMapProtocol implements MapProtocol {
//
//        @Override
//        public Object newInstance() {
//            return com.github.krukow.clj_ds.Persistents.hashMap();
//        }
//
//        @Override
//        public Object put(Object map, Object key, Object val) {
//            return ((com.github.krukow.clj_ds.PersistentMap<Object, Object>) map).plus(key, val);
//        }
//
//        @Override
//        public Object remove(Object map, Object key) {
//            return ((com.github.krukow.clj_ds.PersistentMap<Object, Object>) map).minus(key);
//        }
//
//        @Override
//        public boolean containsKey(Object map, Object key) {
//            return ((com.github.krukow.clj_ds.PersistentMap<Object, Object>) map).containsKey(key);
//        }
//
//        @Override
//        public Object get(Object map, Object key) {
//            return ((com.github.krukow.clj_ds.PersistentMap<Object, Object>) map).get(key);
//        }
//    }
//
//    @SuppressWarnings("unchecked")
//    static class ScalaMapProtocol implements MapProtocol {
//
//        @Override
//        public Object newInstance() {
//            return scala.collection.immutable.HashMap$.MODULE$.empty();
//        }
//
//        @Override
//        public Iterable<Object> iterable(Object map) {
//            return scala.jdk.CollectionConverters$.MODULE$.IterableHasAsJava((scala.collection.Iterable<Object>) map).asJava();
//        }
//
//        @Override
//        public Object put(Object map, Object key, Object val) {
//            return ((scala.collection.immutable.HashMap<Object, Object>) map).updated(key, val);
//        }
//
//        @Override
//        public Object remove(Object map, Object key) {
//            return ((scala.collection.immutable.HashMap<Object, Object>) map).removed(key);
//        }
//
//        @Override
//        public boolean containsKey(Object map, Object key) {
//            return ((scala.collection.immutable.HashMap<Object, Object>) map).contains(key);
//        }
//
//        @Override
//        public Object get(Object map, Object key) {
//            return ((scala.collection.immutable.HashMap<Object, Object>) map).get(key);
//        }
//    }
//
//    @SuppressWarnings("unchecked")
//    static class PCollectionsMapProtocol implements MapProtocol {
//
//        @Override
//        public Object newInstance() {
//            return org.pcollections.HashTreePMap.empty();
//        }
//
//        @Override
//        public Object put(Object map, Object key, Object val) {
//            return ((org.pcollections.PMap<Object, Object>) map).plus(key, val);
//        }
//
//        @Override
//        public Object remove(Object map, Object key) {
//            return ((org.pcollections.PMap<Object, Object>) map).minus(key);
//        }
//
//        @Override
//        public boolean containsKey(Object map, Object key) {
//            return ((org.pcollections.PMap<Object, Object>) map).containsKey(key);
//        }
//
//        @Override
//        public Object get(Object map, Object key) {
//            return ((org.pcollections.PMap<Object, Object>) map).get(key);
//        }
//
//        @Override
//        public Iterable<Object> iterable(Object map) {
//            return (Iterable<Object>) (Object) ((org.pcollections.PMap<Object, Object>) map).entrySet();
//        }
//    }

}
