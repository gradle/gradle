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

package org.gradle.caching.example;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;
import com.google.inject.Inject;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.CacheableEntity;
import org.gradle.caching.internal.SimpleBuildCacheKey;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.caching.internal.controller.service.BuildCacheLoadResult;
import org.gradle.caching.local.internal.DirectoryBuildCache;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.DirectorySnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.MissingFileSnapshot;
import org.gradle.internal.snapshot.RegularFileSnapshot;
import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.snapshot.SnapshotVisitResult;
import org.gradle.internal.vfs.FileSystemAccess;
import org.gradle.internal.vfs.impl.AbstractVirtualFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;

public class ExampleBuildCacheClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryBuildCache.class);

    private final BuildCacheController buildCacheController;
    private final FileSystemAccess fileSystemAccess;

    public static void main(String[] args) throws IOException {
        Guice.createInjector(new BuildCacheClientModule("build-1"))
            .getInstance(ExampleBuildCacheClient.class)
            .useBuildCache();
        System.exit(0);
    }

    @Inject
    public ExampleBuildCacheClient(BuildCacheController buildCacheController, FileSystemAccess fileSystemAccess) {
        this.buildCacheController = buildCacheController;
        this.fileSystemAccess = fileSystemAccess;
    }

    private void useBuildCache() throws IOException {
        BuildCacheKey cacheKey = new SimpleBuildCacheKey(HashCode.fromString("b9800f9130db9efa58f6ec8c744f1cc7"));
        String identity = "test-entity";
        Path targetOutputDirectory = Files.createTempDirectory("target-output");
        ExampleEntity targetEntity = new ExampleEntity(identity, targetOutputDirectory.toFile());

        // Try to load a non-existent entity
        buildCacheController.load(cacheKey, targetEntity)
            .ifPresent(__ -> {
                throw new RuntimeException("Should have been a miss");
            });

        // Produce some example output (simulate executing cacheable work in a temporary sandbox)
        Path sandboxOutputDirectory = Files.createTempDirectory("sandbox-output");
        Path sandboxOutputTxt = sandboxOutputDirectory.resolve("output.txt");
        Files.write(sandboxOutputTxt, Collections.singleton("contents"));

        // Capture the snapshot of the produced output
        FileSystemLocationSnapshot producedOutputSnapshot = fileSystemAccess.read(sandboxOutputDirectory.toAbsolutePath().toString());

        // Store the entity in the cache
        ExampleEntity sandboxEntity = new ExampleEntity("test-entity", sandboxOutputDirectory.toFile());
        buildCacheController.store(
            cacheKey,
            sandboxEntity,
            ImmutableMap.of("output", producedOutputSnapshot),
            Duration.ofSeconds(10));

        // Load the entity from the cache
        BuildCacheLoadResult loadResult = buildCacheController.load(cacheKey, targetEntity)
            .orElseThrow(() -> new RuntimeException("Should have been a hit"));

        // Show what we did
        printLoadedResult(loadResult);
    }

    private static void printLoadedResult(BuildCacheLoadResult loadResult) {
        LOGGER.info("Loaded from cache:");
        loadResult.getResultingSnapshots().forEach((name, snapshot) -> {
            LOGGER.info(" - Output property '{}':", name);
            snapshot.accept(locationSnapshot -> {
                locationSnapshot.accept(new FileSystemLocationSnapshot.FileSystemLocationSnapshotVisitor() {
                    @Override
                    public void visitDirectory(DirectorySnapshot directorySnapshot) {
                        LOGGER.info("   - {}/", locationSnapshot.getAbsolutePath());
                    }

                    @Override
                    public void visitRegularFile(RegularFileSnapshot fileSnapshot) {
                        LOGGER.info("   - {}", locationSnapshot.getAbsolutePath());
                    }

                    @Override
                    public void visitMissing(MissingFileSnapshot missingSnapshot) {
                        LOGGER.info("   - {} (?)", locationSnapshot.getAbsolutePath());
                    }
                });
                return SnapshotVisitResult.CONTINUE;
            });
        });
    }

    private static class ExampleEntity implements CacheableEntity {
        private final String identity;
        private final File outputDirectory;

        public ExampleEntity(String identity, File outputDirectory) {
            this.identity = identity;
            this.outputDirectory = outputDirectory;
        }

        @Override
        public String getIdentity() {
            return identity;
        }

        @Override
        public Class<?> getType() {
            return getClass();
        }

        @Override
        public String getDisplayName() {
            return identity;
        }

        @Override
        public void visitOutputTrees(CacheableTreeVisitor visitor) {
            visitor.visitOutputTree("output", TreeType.DIRECTORY, outputDirectory);
        }
    }

    // TODO Make AbstractVirtualFileSystem into DefaultVirtualFileSystem, and wrap it with the
    //      watching/non-watching implementations so DefaultVirtualFileSystem can be reused here
    static class CustomVirtualFileSystem extends AbstractVirtualFileSystem {
        protected CustomVirtualFileSystem(SnapshotHierarchy root) {
            super(root);
        }

        @Override
        protected SnapshotHierarchy updateNotifyingListeners(UpdateFunction updateFunction) {
            return updateFunction.update(SnapshotHierarchy.NodeDiffListener.NOOP);
        }
    }

    static {
        // Workaround to make sure the dependency checker doesn't complain about slf4j-simple being unused
        Class<?> ignored = org.slf4j.impl.SimpleLoggerFactory.class;
    }
}
