/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.filewatch.jdk7;

import org.gradle.api.file.DirectoryTree;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * On Windows, the Java WatchService supports watching a full sub tree.
 * On other OSs, you can only watch a single directory at a time.
 *
 * This class handles the differences in registering paths to watch.
 */
class ExtendedDirTreeWatchRegistry extends DirTreeWatchRegistry {
    private final FileTreeWatchStrategy subTreeWatchStrategy;

    ExtendedDirTreeWatchRegistry(FileTreeWatchStrategy subTreeWatchStrategy) {
        super(subTreeWatchStrategy);
        this.subTreeWatchStrategy = subTreeWatchStrategy;
    }

    @Override
    public synchronized void register(String sourceKey, Iterable<DirectoryTree> trees) throws IOException {
        for(DirectoryTree originalTree : trees) {
            DirectoryTree tree = new HashableDirectoryTree(originalTree);
            markLive(sourceKey, tree);
            Path treePath = dirToPath(tree.getDir());
            Set<DirectoryTree> watchedTrees = pathToDirectoryTrees.get(treePath);
            if(watchedTrees == null) {
                watchedTrees = new HashSet<DirectoryTree>();
                pathToDirectoryTrees.put(treePath, watchedTrees);
            }
            if(!watchedTrees.contains(tree)) {
                watchedTrees.add(tree);
                if(watchedTrees.size()==1) {
                    subTreeWatchStrategy.watchFileTree(treePath);
                }
            }
        }
    }

    @Override
    protected boolean registerSubDir(DirectoryTree tree, Path path) throws IOException {
        // no need to register subdir while watching changes
        return false;
    }
}
