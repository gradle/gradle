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
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotHierarchyVisitor;
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
import java.util.Map;

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

        Path originalOutputDirectory = Files.createTempDirectory("cache-entity-original");
        Path originalOutputTxt = originalOutputDirectory.resolve("output.txt");
        Files.write(originalOutputTxt, Collections.singleton("contents"));

        // TODO Should we switch to using Path instead of File?
        CacheableEntity originalEntity = new ExampleEntity("test-entity", originalOutputDirectory.toFile());

        FileSystemLocationSnapshot outputDirectorySnapshot = fileSystemAccess.read(originalOutputDirectory.toAbsolutePath().toString());
        Map<String, FileSystemSnapshot> outputSnapshots = ImmutableMap.of("output", outputDirectorySnapshot);
        buildCacheController.store(cacheKey, originalEntity, outputSnapshots, Duration.ofSeconds(10));

        Path loadedFromCacheDirectory = Files.createTempDirectory("cache-entity-loaded");
        CacheableEntity loadedEntity = new ExampleEntity("test-entity", loadedFromCacheDirectory.toFile());

        BuildCacheLoadResult loadResult = buildCacheController.load(cacheKey, loadedEntity)
            .orElseThrow(() -> new RuntimeException("Couldn't load from cache"));

        LOGGER.info("Loaded from cache:");
        loadResult.getResultingSnapshots().forEach((name, snapshot) -> {
            LOGGER.info(" - Output property '{}':", name);
            snapshot.accept(new FileSystemSnapshotHierarchyVisitor() {
                @Override
                public SnapshotVisitResult visitEntry(FileSystemLocationSnapshot snapshot) {
                    snapshot.accept(new FileSystemLocationSnapshot.FileSystemLocationSnapshotVisitor() {
                        @Override
                        public void visitDirectory(DirectorySnapshot directorySnapshot) {
                            LOGGER.info("   - {}/", snapshot.getAbsolutePath());
                        }

                        @Override
                        public void visitRegularFile(RegularFileSnapshot fileSnapshot) {
                            LOGGER.info("   - {}", snapshot.getAbsolutePath());
                        }

                        @Override
                        public void visitMissing(MissingFileSnapshot missingSnapshot) {
                            LOGGER.info("   - {} (?)", snapshot.getAbsolutePath());
                        }
                    });
                    return SnapshotVisitResult.CONTINUE;
                }
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
