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

package org.gradle.caching.internal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class OptimisticBuildCacheIntegrationTest extends AbstractIntegrationSpec {

    def remoteCacheDir

    def setup() {
        requireOwnGradleUserHomeDir("needs isolated transforms/build-cache directories")
        remoteCacheDir = file("remote-cache-dir").createDir()

        createDirs("producer", "consumer")
        settingsFile << """
            include 'producer', 'consumer'
        """

        // Create the artifact file directly
        file("producer/artifact.jar").text = "content"

        file("producer/build.gradle") << """
            def color = Attribute.of('color', String)

            configurations {
                create('default') {
                    attributes.attribute(color, 'blue')
                }
            }

            artifacts {
                'default' file('artifact.jar')
            }
        """

        buildFile << """
            def color = Attribute.of('color', String)
            allprojects {
                dependencies.attributesSchema.attribute(color)
            }
        """
    }

    def "execution wins when remote cache is slow"() {
        given:
        setupRemoteBuildCache(loadDelayMillis: 10000)
        setupTransform(executionDelayMillis: 0)
        setupConsumer()

        when:
        def start = System.currentTimeMillis()
        executer.withBuildCacheEnabled()
        succeeds(':consumer:resolve')
        def elapsed = System.currentTimeMillis() - start

        then:
        // Transform should have executed (not waiting for slow remote cache)
        outputContains("Transforming artifact.jar")
        // Should complete much faster than the 10s remote cache delay
        elapsed < 8000
    }

    def "remote cache wins when execution is slow"() {
        given:
        // First run: populate remote cache with fast transform
        setupRemoteBuildCache(loadDelayMillis: 0)
        setupTransform(executionDelayMillis: 0)
        setupConsumer()

        executer.withBuildCacheEnabled()
        succeeds(':consumer:resolve')
        outputContains("Transforming artifact.jar")

        // Clear immutable workspace and local build cache so the pipeline goes through OptimisticBuildCacheStep
        clearImmutableWorkspacesAndLocalBuildCache()

        // Second run: slow transform, fast remote cache
        setupTransform(executionDelayMillis: 10000)

        when:
        def start = System.currentTimeMillis()
        executer.withBuildCacheEnabled()
        succeeds(':consumer:resolve')
        def elapsed = System.currentTimeMillis() - start

        then:
        // Transform should NOT have completed (interrupted by cache hit)
        outputDoesNotContain("Transforming artifact.jar")
        // Should complete much faster than the 10s transform delay
        elapsed < 8000
    }

    def "local cache hit avoids both remote cache and execution"() {
        given:
        // First run: populate local build cache and remote cache
        setupRemoteBuildCache(loadDelayMillis: 0)
        setupTransform(executionDelayMillis: 0)
        setupConsumer()

        executer.withBuildCacheEnabled()
        succeeds(':consumer:resolve')
        outputContains("Transforming artifact.jar")

        // Clear only the immutable workspace, keep local build cache
        clearImmutableWorkspaces()

        when:
        executer.withBuildCacheEnabled()
        succeeds(':consumer:resolve')

        then:
        // Transform should not have executed (local cache hit)
        outputDoesNotContain("Transforming artifact.jar")
    }

    private void setupRemoteBuildCache(Map options) {
        int loadDelayMillis = options.get('loadDelayMillis', 0)
        settingsFile.text = settingsFile.text.replaceAll(/(?s)\/\/ BEGIN CACHE CONFIG.*\/\/ END CACHE CONFIG/, '')
        settingsFile << """
            // BEGIN CACHE CONFIG
            import org.gradle.caching.*

            class SlowRemoteBuildCache extends AbstractBuildCache {
                String cacheDir
                int loadDelayMillis = 0
            }

            class SlowRemoteBuildCacheServiceFactory implements BuildCacheServiceFactory<SlowRemoteBuildCache> {
                SlowRemoteBuildCacheService createBuildCacheService(SlowRemoteBuildCache configuration, Describer describer) {
                    describer.type("slow-remote")
                    return new SlowRemoteBuildCacheService(new File(configuration.cacheDir), configuration.loadDelayMillis)
                }
            }

            class SlowRemoteBuildCacheService implements BuildCacheService {
                final File dir
                final int loadDelayMillis

                SlowRemoteBuildCacheService(File dir, int loadDelayMillis) {
                    this.dir = dir
                    this.loadDelayMillis = loadDelayMillis
                    dir.mkdirs()
                }

                @Override
                boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
                    File entry = new File(dir, key.hashCode)
                    if (loadDelayMillis > 0) {
                        try { Thread.sleep(loadDelayMillis) } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false }
                    }
                    if (entry.exists()) {
                        entry.withInputStream { reader.readFrom(it) }
                        return true
                    }
                    return false
                }

                @Override
                void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {
                    File entry = new File(dir, key.hashCode)
                    entry.withOutputStream { writer.writeTo(it) }
                }

                @Override
                void close() {}
            }

            buildCache {
                registerBuildCacheService(SlowRemoteBuildCache, SlowRemoteBuildCacheServiceFactory)
                local { enabled = true }
                remote(SlowRemoteBuildCache) {
                    cacheDir = '${remoteCacheDir.absolutePath.replace('\\', '\\\\')}'
                    loadDelayMillis = ${loadDelayMillis}
                    push = true
                    enabled = true
                }
            }
            // END CACHE CONFIG
        """
    }

    private void setupTransform(Map options) {
        int executionDelayMillis = options.get('executionDelayMillis', 0)
        // Write delay config to a file that the transform reads but is NOT a declared input,
        // so it doesn't affect the cache key
        file("consumer/transform-delay.txt").text = "${executionDelayMillis}"

        file("consumer/build.gradle").text = """
            import org.gradle.api.artifacts.transform.*

            def color = Attribute.of('color', String)

            configurations {
                resolver {
                    attributes.attribute(color, 'blue')
                    canBeConsumed = false
                }
            }

            dependencies {
                resolver project(':producer')

                registerTransform(SlowTransform) {
                    from.attribute(color, 'blue')
                    to.attribute(color, 'green')
                }
            }

            @CacheableTransform
            abstract class SlowTransform implements TransformAction<TransformParameters.None> {
                @PathSensitive(PathSensitivity.NAME_ONLY)
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    // Read delay from a file that is NOT a declared input (doesn't affect cache key)
                    def delayFile = new File(input.parentFile.parentFile, "consumer/transform-delay.txt")
                    if (delayFile.exists()) {
                        def delay = delayFile.text.trim() as int
                        if (delay > 0) {
                            Thread.sleep(delay)
                        }
                    }
                    println "Transforming \${input.name}"
                    def output = outputs.file(input.name + ".txt")
                    output.text = input.text + " transformed"
                }
            }

            tasks.register('resolve') {
                def files = configurations.resolver.incoming.artifactView {
                    attributes.attribute(color, 'green')
                }.files
                doLast {
                    files.each { println "resolved: \${it.name}" }
                }
            }
        """
    }

    private void setupConsumer() {
        // Consumer build file is set up by setupTransform
    }

    private void clearImmutableWorkspacesAndLocalBuildCache() {
        // Delete transform workspaces
        clearImmutableWorkspaces()
        // Delete local build cache
        def localCacheDir = new File(executer.gradleUserHomeDir, "caches")
        if (localCacheDir.exists()) {
            localCacheDir.listFiles()?.findAll { it.isDirectory() && it.name.startsWith("build-cache") }?.each { it.deleteDir() }
        }
    }

    private void clearImmutableWorkspaces() {
        def cachesDir = new File(executer.gradleUserHomeDir, "caches")
        if (!cachesDir.exists()) return
        // Find and delete all 'transforms' directories
        def stack = [cachesDir] as ArrayDeque
        while (!stack.isEmpty()) {
            def dir = stack.pop()
            if (!dir.exists() || !dir.isDirectory()) continue
            if (dir.name == "transforms") {
                dir.deleteDir()
                continue
            }
            dir.listFiles()?.findAll { it.isDirectory() }?.each { stack.push(it) }
        }
    }
}
