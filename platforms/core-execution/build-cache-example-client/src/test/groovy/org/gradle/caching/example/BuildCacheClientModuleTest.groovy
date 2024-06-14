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

package org.gradle.caching.example

import com.google.common.collect.ImmutableMap
import com.google.inject.Guice
import org.gradle.caching.BuildCacheKey
import org.gradle.caching.internal.CacheableEntity
import org.gradle.caching.internal.SimpleBuildCacheKey
import org.gradle.caching.internal.controller.BuildCacheController
import org.gradle.caching.internal.controller.service.BuildCacheLoadResult
import org.gradle.internal.file.TreeType
import org.gradle.internal.hash.HashCode
import org.gradle.internal.snapshot.DirectorySnapshot
import org.gradle.internal.snapshot.FileSystemLocationSnapshot
import org.gradle.internal.snapshot.MissingFileSnapshot
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.internal.snapshot.SnapshotVisitResult
import org.gradle.internal.vfs.FileSystemAccess
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

class BuildCacheClientModuleTest extends Specification {
    def injector = Guice.createInjector(new BuildCacheClientModule("build-1"))
    def buildCacheController = injector.getInstance(BuildCacheController)
    def fileSystemAccess = injector.getInstance(FileSystemAccess)

    def "can use build cache to store and load things"() {
        BuildCacheKey cacheKey = new SimpleBuildCacheKey(HashCode.fromString("b9800f9130db9efa58f6ec8c744f1cc7"))
        String identity = "test-entity"
        Path targetOutputDirectory = Files.createTempDirectory("target-output")
        ExampleEntity targetEntity = new ExampleEntity(identity, targetOutputDirectory.toFile())

        // Try to load a non-existent entity
        buildCacheController.load(cacheKey, targetEntity)
            .ifPresent(__ -> {
                throw new RuntimeException("Should have been a miss")
            })

        // Produce some example output (simulate executing cacheable work in a temporary sandbox)
        Path sandboxOutputDirectory = Files.createTempDirectory("sandbox-output")
        Path sandboxOutputTxt = sandboxOutputDirectory.resolve("output.txt")
        Files.write(sandboxOutputTxt, Collections.singleton("contents"))

        // Capture the snapshot of the produced output
        FileSystemLocationSnapshot producedOutputSnapshot = fileSystemAccess.read(sandboxOutputDirectory.toAbsolutePath().toString())

        // Store the entity in the cache
        def sandboxEntity = new ExampleEntity("test-entity", sandboxOutputDirectory.toFile())
        buildCacheController.store(
            cacheKey,
            sandboxEntity,
            ImmutableMap.of("output", producedOutputSnapshot),
            Duration.ofSeconds(10))

        // Load the entity from the cache
        def loadResult = buildCacheController.load(cacheKey, targetEntity)
            .orElseThrow(() -> new RuntimeException("Should have been a hit"))

        printLoadedResult(loadResult)

        expect:
        true
    }

    private static void printLoadedResult(BuildCacheLoadResult loadResult) {
        println("Loaded from cache:")
        loadResult.getResultingSnapshots().forEach((name, snapshot) -> {
            println(" - Output property '${name}':")
            snapshot.accept(locationSnapshot -> {
                locationSnapshot.accept(new FileSystemLocationSnapshot.FileSystemLocationSnapshotVisitor() {
                    @Override
                    void visitDirectory(DirectorySnapshot directorySnapshot) {
                        println("   - ${locationSnapshot.getAbsolutePath()}/")
                    }

                    @Override
                    void visitRegularFile(RegularFileSnapshot fileSnapshot) {
                        println("   - ${locationSnapshot.getAbsolutePath()}")
                    }

                    @Override
                    void visitMissing(MissingFileSnapshot missingSnapshot) {
                        println("   - ${locationSnapshot.getAbsolutePath()} (?)")
                    }
                })
                return SnapshotVisitResult.CONTINUE
            })
        })
    }

    static class ExampleEntity implements CacheableEntity {
        final String identity
        final File outputDirectory

        ExampleEntity(String identity, File outputDirectory) {
            this.identity = identity
            this.outputDirectory = outputDirectory
        }

        @Override
        Class<?> getType() {
            return getClass()
        }

        @Override
        String getDisplayName() {
            return identity
        }

        @Override
        void visitOutputTrees(CacheableTreeVisitor visitor) {
            visitor.visitOutputTree("output", TreeType.DIRECTORY, outputDirectory)
        }
    }
}
