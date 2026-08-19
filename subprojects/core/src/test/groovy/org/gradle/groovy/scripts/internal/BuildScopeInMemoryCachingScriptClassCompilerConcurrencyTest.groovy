/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.groovy.scripts.internal

import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory
import org.gradle.groovy.scripts.ScriptSource
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

@Timeout(120)
class BuildScopeInMemoryCachingScriptClassCompilerConcurrencyTest extends Specification {

    private static final int THREADS = 8
    private static final int ROUNDS = 100
    private static final int ROUND_TIMEOUT_SECONDS = 10

    // Class names sharing a String.hashCode(), so all keys land in one bucket. An unsynchronized
    // HashMap only fails observably once a bucket is treeified, which needs 8 colliding entries;
    // with well-spread keys the same race corrupts the map silently instead of throwing.
    private static final List<String> COLLIDING_CLASS_NAMES = (0..<128).collect { int combination ->
        (0..<7).collect { int bit -> (combination >> bit) & 1 ? "BB" : "Aa" }.join()
    }

    private final ClassLoader classLoader = new ClassLoader() {}
    private final Map<String, CompiledScript<?, ?>> compiledScripts =
        COLLIDING_CLASS_NAMES.collectEntries { [it, [:] as CompiledScript] }

    // Daemon threads: a corrupted map can spin forever inside get(), ignoring interruption, and
    // would otherwise keep the test JVM alive after the test has failed.
    private final ExecutorService executorService = Executors.newFixedThreadPool(THREADS, { Runnable runnable ->
        def thread = new Thread(runnable, "script-class-cache-race")
        thread.daemon = true
        thread
    } as ThreadFactory)

    def cleanup() {
        executorService.shutdownNow()
    }

    def "compiles concurrently without corrupting the build scoped cache"() {
        given:
        def failures = new ConcurrentLinkedQueue<Throwable>()

        when:
        int stuckRound = -1

        // A fresh compiler per round: the race is on populating the map, not on reading a warm one.
        for (int round = 0; round < ROUNDS && stuckRound < 0; round++) {
            def compiler = newCompiler()

            def results = executorService.invokeAll((0..<THREADS).collect {
                { ->
                    try {
                        COLLIDING_CLASS_NAMES.each { String className ->
                            def compiled = compile(compiler, className)

                            if (!compiled.is(compiledScripts[className])) {
                                throw new IllegalStateException("Cache returned the wrong script for $className")
                            }
                        }
                    } catch (Throwable failure) {
                        failures.add(failure)
                    }

                    null
                } as Callable
            }, ROUND_TIMEOUT_SECONDS, TimeUnit.SECONDS)

            // Stuck threads are unrecoverable, so every later round would starve the pool.
            if (results.any { it.cancelled }) {
                stuckRound = round
            }
        }

        then:
        if (stuckRound >= 0) {
            throw new AssertionError(
                "Concurrent compile() left a thread spinning inside the cache in round $stuckRound: " +
                    "the map is corrupted and lookups no longer terminate", failures.peek()
            )
        }

        if (!failures.empty) {
            throw new AssertionError(
                "Concurrent compile() corrupted the cache: ${failures.size()} of ${THREADS * ROUNDS} tasks failed",
                failures.peek()
            )
        }
    }

    private ScriptClassCompiler newCompiler() {
        new BuildScopeInMemoryCachingScriptClassCompiler(delegateCache(), null)
    }

    private CompiledScript<?, ?> compile(ScriptClassCompiler compiler, String className) {
        compiler.compile(
            [getClassName: { className }] as ScriptSource,
            Script,
            new Object(),
            [getExportClassLoader: { classLoader }] as ClassLoaderScope,
            // Constant id: keeps every key's hash equal, leaving the class name as the only difference.
            [getId: { "test" }] as CompileOperation
        )
    }

    // Not a Spock mock: those synchronize internally, serializing the calls this test needs to overlap.
    private CrossBuildInMemoryCachingScriptClassCache delegateCache() {
        new CrossBuildInMemoryCachingScriptClassCache([newCache: { null }] as CrossBuildInMemoryCacheFactory) {
            @Override
            CompiledScript getOrCompile(
                Object target,
                ScriptSource source,
                ClassLoaderScope targetScope,
                CompileOperation operation,
                Class scriptBaseClass,
                ScriptClassCompiler delegate
            ) {
                compiledScripts[source.className]
            }
        }
    }
}
