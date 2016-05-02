/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.DefaultFileCollectionResolveContext;
import org.gradle.internal.serialize.SerializerRegistry;

import java.io.File;
import java.util.Collection;
import java.util.List;

public class DefaultFileCollectionSnapshotter extends AbstractFileCollectionSnapshotter {
    private final CachingTreeVisitor treeVisitor;
    private final TreeSnapshotRepository treeSnapshotRepository;

    public DefaultFileCollectionSnapshotter(FileSnapshotter snapshotter, TaskArtifactStateCacheAccess cacheAccess, StringInterner stringInterner, FileResolver fileResolver, CachingTreeVisitor treeVisitor, TreeSnapshotRepository treeSnapshotRepository) {
        super(snapshotter, cacheAccess, stringInterner, fileResolver);
        this.treeVisitor = treeVisitor;
        this.treeSnapshotRepository = treeSnapshotRepository;
    }

    public void registerSerializers(SerializerRegistry registry) {
        registry.register(FileCollectionSnapshotImpl.class, new DefaultFileSnapshotterSerializer(stringInterner, treeSnapshotRepository));
    }

    @Override
    VisitedTree createJoinedTree(List<VisitedTree> nonShareableTrees, Collection<File> missingFiles) {
        return treeVisitor.createJoinedTree(nonShareableTrees, missingFiles);
    }

    @Override
    protected void visitFiles(FileCollection input, final List<VisitedTree> visitedTrees, final List<File> missingFiles, boolean allowReuse) {
        DefaultFileCollectionResolveContext context = new DefaultFileCollectionResolveContext(fileResolver);
        context.add(input);
        List<FileTreeInternal> fileTrees = context.resolveAsFileTrees();

        for (FileTreeInternal fileTree : fileTrees) {
            visitedTrees.add(treeVisitor.visitTreeForSnapshotting(fileTree, allowReuse));
        }
    }


}
