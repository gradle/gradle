/*
 * Copyright 2014 the original author or authors.
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

package perf;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.commons.collections.map.AbstractReferenceMap;
import org.apache.commons.collections.map.ReferenceMap;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@State(Scope.Benchmark)
public class ClassGeneratorBench {

    ReferenceMap map = new ReferenceMap(AbstractReferenceMap.WEAK, AbstractReferenceMap.WEAK);

    LoadingCache<Class<?>, WeakReference<Class<?>>> cache = CacheBuilder.newBuilder()
            .weakKeys()
//            .concurrencyLevel(1)
            .build(new CacheLoader<Class<?>, WeakReference<Class<?>>>() {
                @Override
                public WeakReference<Class<?>> load(Class<?> key) throws Exception {
                    return new WeakReference<Class<?>>(key);
                }
            });

    Lock lock = new ReentrantLock();

    private static class Class1 {

    }

    private static class Class2 {
    }

    private static class Class3 {
    }

    private static class Class4 {
    }

    private static class Class5 {
    }

    private static class Class6 {
    }

    private static class Class7 {
    }

    private static class Class8 {
    }

    private static class Class9 {
    }

    private static class Class10 {
    }

    Class[] classes = new Class[]{
            Class1.class,
            Class2.class,
            Class3.class,
            Class4.class,
            Class5.class,
            Class6.class,
            Class7.class,
            Class8.class,
            Class9.class,
            Class10.class
    };

    int iter = 100;

    @Benchmark
    public void viaMap(Blackhole blackhole) {
        for (Class aClass : classes) {
            for (int i = 0; i < iter; i++) {
                lock.lock();
                try {
                    Object o = map.get(aClass);
                    if (o == null) {
                        map.put(aClass, aClass);
                        o = map.get(aClass);
                    }
                    blackhole.consume(o);
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    @Benchmark
    public void viaCache(Blackhole blackhole) throws ExecutionException {
        for (Class aClass : classes) {
            for (int i = 0; i < iter; i++) {
                Object value = cache.get(aClass).get();
                blackhole.consume(value);
            }
        }
    }

}

