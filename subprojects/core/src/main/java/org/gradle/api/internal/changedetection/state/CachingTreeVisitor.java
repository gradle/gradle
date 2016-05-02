/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.cache.HeapProportionalCacheSizer;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.FileTreeAdapter;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

// Visits a FileTreeInternal for snapshotting, caches some directory scans
public class CachingTreeVisitor {
    private final static Logger LOG = Logging.getLogger(CachingTreeVisitor.class);
    public static final int VISITED_TREES_CACHE_MAX_SIZE = 500;
    private final Cache<String, VisitedTree> cachedTrees;
    private final AtomicLong nextId = new AtomicLong(System.currentTimeMillis());

    public CachingTreeVisitor() {
        HeapProportionalCacheSizer cacheSizer = new HeapProportionalCacheSizer();
        cachedTrees = CacheBuilder.newBuilder().maximumSize(cacheSizer.scaleCacheSize(VISITED_TREES_CACHE_MAX_SIZE, 10)).build();
    }

    public VisitedTree visitTreeForSnapshotting(FileTreeInternal fileTree, boolean allowReuse) {
        if (isDirectoryFileTree(fileTree)) {
            DirectoryFileTree directoryFileTree = DirectoryFileTree.class.cast(((FileTreeAdapter) fileTree).getTree());
            if (isEligibleForCaching(directoryFileTree)) {
                final String absolutePath = directoryFileTree.getDir().getAbsolutePath();
                VisitedTree cachedTree = allowReuse ? cachedTrees.getIfPresent(absolutePath) : null;
                if (cachedTree != null) {
                    recordCacheHit(directoryFileTree);
                    return cachedTree;
                } else {
                    recordCacheMiss(directoryFileTree, allowReuse);
                    cachedTree = doVisitTree(fileTree, true);
                    cachedTrees.put(absolutePath, cachedTree);
                    return cachedTree;
                }
            }
        }
        return doVisitTree(fileTree, false);
    }

    public VisitedTree createJoinedTree(List<VisitedTree> trees, Collection<File> missingFiles) {
        return createJoinedTree(nextId.incrementAndGet(), trees, missingFiles);
    }

    public static VisitedTree createJoinedTree(long nextId, List<VisitedTree> trees, Collection<File> missingFiles) {
        if (missingFiles.isEmpty()) {
            if (trees.size() == 0) {
                return null;
            }
            if (trees.size() == 1) {
                return trees.get(0);
            }
        }
        ImmutableList.Builder<FileTreeElement> listBuilder = ImmutableList.builder();
        for (VisitedTree tree : trees) {
            listBuilder.addAll(tree.getEntries());
        }
        return new DefaultVisitedTree(listBuilder.build(), false, nextId, missingFiles);
    }

    protected void recordCacheHit(DirectoryFileTree directoryFileTree) {
        // method added also for interception with bytebuddy in integtest
        LOG.debug("Cache hit {}", directoryFileTree);
    }

    protected void recordCacheMiss(DirectoryFileTree directoryFileTree, boolean allowReuse) {
        // method added also for interception with bytebuddy in integtest
        if (allowReuse) {
            LOG.debug("Cache miss {}", directoryFileTree);
        } else {
            LOG.debug("Visiting {}", directoryFileTree);
        }
    }

    private boolean isEligibleForCaching(DirectoryFileTree directoryFileTree) {
        return directoryFileTree.getPatterns().isEmpty();
    }

    private boolean isDirectoryFileTree(FileTreeInternal fileTree) {
        return fileTree instanceof FileTreeAdapter && ((FileTreeAdapter) fileTree).getTree() instanceof DirectoryFileTree;
    }

    private VisitedTree doVisitTree(FileTreeInternal fileTree, boolean shareable) {
        final ImmutableList.Builder<FileTreeElement> fileTreeElements = ImmutableList.builder();
        fileTree.visitTreeOrBackingFile(new FileVisitor() {
            @Override
            public void visitDir(FileVisitDetails dirDetails) {
                fileTreeElements.add(dirDetails);
            }

            @Override
            public void visitFile(FileVisitDetails fileDetails) {
                fileTreeElements.add(fileDetails);
            }
        });
        return new DefaultVisitedTree(fileTreeElements.build(), shareable, nextId.incrementAndGet(), null);
    }

    public void clearCache() {
        cachedTrees.invalidateAll();
    }

}
