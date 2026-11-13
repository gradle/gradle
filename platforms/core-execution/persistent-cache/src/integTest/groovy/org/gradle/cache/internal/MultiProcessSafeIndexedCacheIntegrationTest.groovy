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

package org.gradle.cache.internal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import org.junit.Rule

@Requires(value = TestExecutionPreconditions.NotEmbeddedExecutor, reason = "requires two independent Gradle processes")
class MultiProcessSafeIndexedCacheIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        server.start()
        executer.requireOwnGradleUserHomeDir().withDaemonBaseDir(file("daemon")).requireDaemon()
    }

    def "stale conditional writer cannot overwrite an invalidation from another process"() {
        given:
        def cacheFile = file("shared-cache/entries.bin")
        buildFile << """
            import org.gradle.cache.FileLockManager
            import org.gradle.cache.IndexedCacheParameters
            import org.gradle.cache.internal.DefaultMultiProcessSafeIndexedCache
            import org.gradle.cache.internal.OnDemandFileAccess
            import org.gradle.cache.internal.btree.BTreePersistentIndexedCache

            abstract class CacheOperation extends DefaultTask {
                @Inject
                abstract FileLockManager getFileLockManager()

                @Input
                abstract Property<String> getOperation()

                @Input
                abstract Property<String> getCacheFilePath()

                private def withCache(Closure action) {
                    def target = new File(cacheFilePath.get())
                    target.parentFile.mkdirs()
                    def parameters = IndexedCacheParameters.of("entries", String.class, String.class)
                    def fileAccess = new OnDemandFileAccess(target, "conditional update integration test cache", fileLockManager)
                    def indexedCache = new DefaultMultiProcessSafeIndexedCache<String, String>(
                        { new BTreePersistentIndexedCache<String, String>(
                            target,
                            parameters.getKeySerializer(),
                            parameters.getValueSerializer()
                        ) } as java.util.function.Supplier,
                        fileAccess
                    )
                    try {
                        return action(indexedCache)
                    } finally {
                        indexedCache.finishWork()
                    }
                }

                @TaskAction
                void runOperation() {
                    def projectDir = new File(cacheFilePath.get()).parentFile.parentFile
                    switch (operation.get()) {
                        case "seed":
                            withCache { cache ->
                                cache.put("key", "initial")
                            }
                            break
                        case "staleWriter":
                            def expected = withCache { cache ->
                                cache.getIfPresent("key")
                            }
                            assert expected == "initial"
                            new File(projectDir, "writer.pid").text = ProcessHandle.current().pid().toString()
                            ${server.callFromBuild("writerLoaded")}
                            def stored = withCache { cache ->
                                cache.putIf(
                                    "key",
                                    "stale",
                                    { current -> current == expected } as java.util.function.Predicate<String>
                                )
                            }
                            println "STALE_STORE_RESULT=" + stored
                            break
                        case "invalidate":
                            new File(projectDir, "invalidator.pid").text = ProcessHandle.current().pid().toString()
                            withCache { cache ->
                                cache.remove("key")
                            }
                            break
                        case "assertAbsent":
                            withCache { cache ->
                                assert cache.getIfPresent("key") == null
                            }
                            break
                        default:
                            throw new GradleException("Unknown operation: " + operation.get())
                    }
                }
            }

            tasks.register("seed", CacheOperation) {
                operation.set("seed")
                cacheFilePath.set("${cacheFile.absolutePath}")
            }
            tasks.register("staleWriter", CacheOperation) {
                operation.set("staleWriter")
                cacheFilePath.set("${cacheFile.absolutePath}")
            }
            tasks.register("invalidate", CacheOperation) {
                operation.set("invalidate")
                cacheFilePath.set("${cacheFile.absolutePath}")
            }
            tasks.register("assertAbsent", CacheOperation) {
                operation.set("assertAbsent")
                cacheFilePath.set("${cacheFile.absolutePath}")
            }
        """

        and:
        succeeds("seed")
        def writerLoaded = server.expectAndBlock("writerLoaded")

        when:
        def staleWriter = executer.withTasks("staleWriter").start()
        writerLoaded.waitForAllPendingCalls()
        succeeds("invalidate")
        writerLoaded.releaseAll()
        staleWriter.waitForFinish()

        then:
        staleWriter.standardOutput.contains("STALE_STORE_RESULT=false")
        file("writer.pid").text != file("invalidator.pid").text

        and:
        succeeds("assertAbsent")
    }
}
