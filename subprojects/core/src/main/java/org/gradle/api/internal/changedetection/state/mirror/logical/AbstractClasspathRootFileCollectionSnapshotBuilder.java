/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.changedetection.state.mirror.logical;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ListMultimap;
import org.gradle.api.internal.changedetection.state.DirectoryFileSnapshot;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileContentSnapshot;
import org.gradle.api.internal.changedetection.state.FileHashSnapshot;
import org.gradle.api.internal.changedetection.state.IgnoredPathFileSnapshot;
import org.gradle.api.internal.changedetection.state.IndexedNormalizedFileSnapshot;
import org.gradle.api.internal.changedetection.state.JarHasher;
import org.gradle.api.internal.changedetection.state.MissingFileSnapshot;
import org.gradle.api.internal.changedetection.state.NormalizedFileSnapshot;
import org.gradle.api.internal.changedetection.state.RegularFileSnapshot;
import org.gradle.api.internal.changedetection.state.ResourceHasher;
import org.gradle.api.internal.changedetection.state.ResourceSnapshotterCacheService;
import org.gradle.caching.internal.DefaultBuildCacheHasher;
import org.gradle.internal.Factory;
import org.gradle.internal.FileUtils;
import org.gradle.internal.hash.HashCode;

import javax.annotation.Nullable;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;

public abstract class AbstractClasspathRootFileCollectionSnapshotBuilder extends RootFileCollectionSnapshotBuilder {
    private final ResourceSnapshotterCacheService cacheService;
    private final ResourceHasher classpathResourceHasher;
    private final JarHasher jarHasher;
    private final HashCode jarHasherConfigurationHash;

    public AbstractClasspathRootFileCollectionSnapshotBuilder(ResourceHasher classpathResourceHasher, ResourceSnapshotterCacheService cacheService) {
        this.cacheService = cacheService;
        this.classpathResourceHasher = classpathResourceHasher;
        this.jarHasher = new JarHasher(classpathResourceHasher);
        DefaultBuildCacheHasher hasher = new DefaultBuildCacheHasher();
        jarHasher.appendConfigurationToHasher(hasher);
        this.jarHasherConfigurationHash = hasher.hash();
    }

    @Override
    protected FileCollectionSnapshot build(ListMultimap<String, LogicalSnapshot> roots) {
        return new ClasspathSnapshot(new ClasspathSnapshotFactory(roots));
    }

    @Nullable
    @Override
    public FileContentSnapshot snapshotFileContents(String path, Deque<String> relativePath, FileContentSnapshot contentSnapshot) {
        HashCode hashCode = classpathResourceHasher.hash(path, relativePath, contentSnapshot);
        return hashCode == null ? null : new FileHashSnapshot(hashCode);
    }

    @Override
    public void visitFileSnapshot(RegularFileSnapshot file) {
        FileContentSnapshot newContentSnapshot = snapshotRootFile(file);
        if (newContentSnapshot != null) {
            addRoot(file.getPath(), file.getName(), newContentSnapshot);
        }
    }

    @Nullable
    private FileContentSnapshot snapshotRootFile(RegularFileSnapshot file) {
        if (FileUtils.hasExtensionIgnoresCase(file.getName(), ".jar")) {
            return snapshotJarContents(file.getPath(), file.getRelativePath(), file.getContent());
        }
        return snapshotNonJarContents(file.getContent());
    }

    @Nullable
    protected abstract FileContentSnapshot snapshotNonJarContents(FileContentSnapshot contentSnapshot);

    @Nullable
    private FileContentSnapshot snapshotJarContents(String path, Iterable<String> relativePath, FileContentSnapshot contentSnapshot) {
        HashCode hash = cacheService.hashFile(path, relativePath, contentSnapshot, jarHasher, jarHasherConfigurationHash);
        return hash == null ? null : new FileHashSnapshot(hash);
    }

    @Override
    public void visitDirectorySnapshot(DirectoryFileSnapshot directory) {
    }

    @Override
    public void visitMissingFileSnapshot(MissingFileSnapshot missingFile) {
    }

    private class ClasspathSnapshotFactory implements Factory<Map<String, NormalizedFileSnapshot>> {
        private final ListMultimap<String, LogicalSnapshot> roots;

        public ClasspathSnapshotFactory(ListMultimap<String, LogicalSnapshot> roots) {
            this.roots = roots;
        }

        @Nullable
        @Override
        public Map<String, NormalizedFileSnapshot> create() {
            final ImmutableMap.Builder<String, NormalizedFileSnapshot> builder = ImmutableMap.builder();
            final HashSet<String> processedEntries = new HashSet<String>();
            for (Map.Entry<String, LogicalSnapshot> entry : roots.entries()) {
                final String basePath = entry.getKey();
                final int rootIndex = basePath.length() + 1;
                final ImmutableSortedMap.Builder<String, NormalizedFileSnapshot> rootBuilder = ImmutableSortedMap.naturalOrder();
                entry.getValue().accept(new HierarchicalSnapshotVisitor() {
                    private boolean root = true;

                    @Override
                    public void preVisitDirectory(String path, String name) {
                        root = false;
                    }

                    @Override
                    public void visit(String path, String name, FileContentSnapshot content) {
                        if (processedEntries.add(path)) {
                            NormalizedFileSnapshot normalizedFileSnapshot = isRoot() ? new IgnoredPathFileSnapshot(content) : new IndexedNormalizedFileSnapshot(path, getIndex(name), content);
                            rootBuilder.put(
                                path,
                                normalizedFileSnapshot);
                        }
                    }

                    private int getIndex(String name) {
                        return isRoot() ? basePath.length() - name.length() : rootIndex;
                    }

                    private boolean isRoot() {
                        return root;
                    }

                    @Override
                    public void postVisitDirectory() {
                    }
                });
                builder.putAll(rootBuilder.build());
            }
            return builder.build();
        }
    }
}
