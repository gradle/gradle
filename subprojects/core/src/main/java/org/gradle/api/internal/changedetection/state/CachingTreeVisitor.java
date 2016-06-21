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
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.UncheckedException;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

// Visits a FileTreeInternal for snapshotting, caches some directory scans
public class CachingTreeVisitor {
    public static final boolean CACHING_TREE_VISITOR_FEATURE_ENABLED = Boolean.valueOf(System.getProperty("org.gradle.tree_visitor_cache.enabled", "false"));
    private final static Logger LOG = Logging.getLogger(CachingTreeVisitor.class);
    public static final int VISITED_TREES_CACHE_MAX_SIZE = 500;
    private final Cache<String, VisitedTreeCacheEntry> cachedTrees;
    private final AtomicLong nextId = new AtomicLong(System.currentTimeMillis());
    private volatile HashSet<String> cacheableFilePaths;

    public CachingTreeVisitor() {
        HeapProportionalCacheSizer cacheSizer = new HeapProportionalCacheSizer();
        cachedTrees = CacheBuilder.newBuilder().maximumSize(cacheSizer.scaleCacheSize(VISITED_TREES_CACHE_MAX_SIZE, 10)).build();
    }

    public VisitedTree visitTreeForSnapshotting(FileTreeInternal fileTree, boolean allowReuse) {
        String treePath = null;
        PatternSet treePattern = null;
        if (CACHING_TREE_VISITOR_FEATURE_ENABLED && isDirectoryFileTree(fileTree)) {
            DirectoryFileTree directoryFileTree = DirectoryFileTree.class.cast(((FileTreeAdapter) fileTree).getTree());
            treePath = directoryFileTree.getDir().getAbsolutePath();
            treePattern = directoryFileTree.getPatternSet();
            if (isCacheablePath(treePath)) {
                VisitedTreeCacheEntry cacheEntry = findOrCreateCacheEntry(treePath);
                cacheEntry.lock();
                try {
                    VisitedTree cachedTree = null;
                    if (cacheEntry != null) {
                        if (allowReuse) {
                            cachedTree = cacheEntry.get(treePattern);
                        } else {
                            cacheEntry.clear();
                        }
                    }
                    if (cachedTree != null) {
                        recordCacheHit(directoryFileTree);
                        return cachedTree;
                    } else {
                        recordCacheMiss(directoryFileTree, allowReuse);
                        cachedTree = doVisitTree(treePath, treePattern, fileTree, true);
                        cacheEntry.put(treePattern, cachedTree);
                        return cachedTree;
                    }
                } finally {
                    cacheEntry.unlock();
                }
            }
        }
        return doVisitTree(treePath, treePattern, fileTree, false);
    }

    private VisitedTreeCacheEntry findOrCreateCacheEntry(String treePath) {
        VisitedTreeCacheEntry cacheEntry;
        try {
            cacheEntry = cachedTrees.get(treePath, new Callable<VisitedTreeCacheEntry>() {
                @Override
                public VisitedTreeCacheEntry call() {
                    return new VisitedTreeCacheEntry(nextId);
                }
            });
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        return cacheEntry;
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
        return new DefaultVisitedTree(null, null, listBuilder.build(), false, nextId, missingFiles);
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

    private boolean isCacheablePath(String absolutePath) {
        return cacheableFilePaths != null && cacheableFilePaths.contains(absolutePath);
    }

    private boolean isDirectoryFileTree(FileTreeInternal fileTree) {
        return fileTree instanceof FileTreeAdapter && ((FileTreeAdapter) fileTree).getTree() instanceof DirectoryFileTree;
    }

    private VisitedTree doVisitTree(String absolutePath, PatternSet patternSet, FileTreeInternal fileTree, boolean shareable) {
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
        return new DefaultVisitedTree(absolutePath, patternSet, fileTreeElements.build(), shareable, nextId.incrementAndGet(), null);
    }

    public void clearCache() {
        cachedTrees.invalidateAll();
    }

    public void updateCacheableFilePaths(Collection<String> cacheableFilePaths) {
        this.cacheableFilePaths = cacheableFilePaths != null ? new HashSet<String>(cacheableFilePaths) : null;
    }

    public void invalidateFilePaths(Iterable<String> filePaths) {
        cachedTrees.invalidateAll(filePaths);
    }

    private static class VisitedTreeCacheEntry {
        private final ReentrantLock lock = new ReentrantLock();
        private final AtomicLong nextId;
        Map<PatternSet, VisitedTree> treesPerPattern;
        VisitedTree noPatternTree;

        public VisitedTreeCacheEntry(AtomicLong nextId) {
            this.nextId = nextId;
        }

        public VisitedTree get(PatternSet patternSet) {
            if (patternSet == null || patternSet.isEmpty()) {
                return noPatternTree;
            } else if (treesPerPattern != null) {
                VisitedTree cachedTree = treesPerPattern.get(patternSet);
                if (cachedTree == null && noPatternTree != null) {
                    cachedTree = filterTree(noPatternTree, patternSet, nextId);
                }
                return cachedTree;
            } else {
                return null;
            }
        }

        private VisitedTree filterTree(VisitedTree noPatternTree, PatternSet patternSet, AtomicLong nextId) {
            final List<FileTreeElement> filteredEntries = noPatternTree.filter(patternSet);
            if (filteredEntries.size() != noPatternTree.getEntries().size()) {
                return new DefaultVisitedTree(noPatternTree.getAbsolutePath(), patternSet, filteredEntries, true, nextId.incrementAndGet(), null);
            } else {
                return noPatternTree;
            }
        }

        public void put(PatternSet patternSet, VisitedTree visitedTree) {
            if (patternSet == null || patternSet.isEmpty()) {
                noPatternTree = visitedTree;
            } else {
                if (treesPerPattern == null) {
                    treesPerPattern = new HashMap<PatternSet, VisitedTree>();
                }
                treesPerPattern.put(patternSet, visitedTree);
            }
        }

        public void clear() {
            noPatternTree = null;
            treesPerPattern = null;
        }

        public void lock() {
            lock.lock();
        }

        public void unlock() {
            lock.unlock();
        }
    }
}
