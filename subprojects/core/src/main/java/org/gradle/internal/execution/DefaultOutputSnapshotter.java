/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.execution;

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import org.gradle.api.file.FileCollection;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.execution.fingerprint.FileCollectionSnapshotter;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.fingerprint.ContentTracking;
import org.gradle.internal.snapshot.FileSystemSnapshot;

import java.io.File;
import java.io.UncheckedIOException;

public class DefaultOutputSnapshotter implements OutputSnapshotter {
    private final FileCollectionSnapshotter fileCollectionSnapshotter;

    public DefaultOutputSnapshotter(FileCollectionSnapshotter fileCollectionSnapshotter) {
        this.fileCollectionSnapshotter = fileCollectionSnapshotter;
    }

    @Override
    public Result snapshotOutputs(UnitOfWork work, File workspace) {
        ImmutableSortedMap.Builder<String, FileSystemSnapshot> builder = ImmutableSortedMap.naturalOrder();
        ImmutableSortedSet.Builder<String> untrackedProperties = ImmutableSortedSet.naturalOrder();
        work.visitOutputs(workspace, new UnitOfWork.OutputVisitor() {
            @Override
            public void visitOutputProperty(String propertyName, TreeType type, ContentTracking contentTracking, File root, FileCollection contents) {
                if (contentTracking == ContentTracking.TRACKED) {
                    try {
                        builder.put(propertyName, fileCollectionSnapshotter.snapshot(contents));
                    } catch (UncheckedIOException e) {
                        DeprecationLogger.deprecate("Snapshotting output directories which contain unreadable files")
                            .withAdvice("Declare the output property as untracked.")
                            .willBecomeAnErrorInGradle8()
                            // TODO: Document
                            .undocumented()
                            .nagUser();
                        untrackedProperties.add(propertyName);
                    }
                } else {
                    untrackedProperties.add(propertyName);
                }
            }
        });
        return new DefaultResult(builder.build(), untrackedProperties.build());
    }

    private static class DefaultResult implements Result {

        private final ImmutableSortedMap<String, FileSystemSnapshot> outputSnapshots;
        private final ImmutableSortedSet<String> untrackedProperties;

        public DefaultResult(ImmutableSortedMap<String, FileSystemSnapshot> outputSnapshots, ImmutableSortedSet<String> untrackedProperties)  {
            this.outputSnapshots = outputSnapshots;
            this.untrackedProperties = untrackedProperties;
        }

        @Override
        public ImmutableSortedMap<String, FileSystemSnapshot> getOutputSnapshots() {
            return outputSnapshots;
        }

        @Override
        public ImmutableSortedSet<String> getUntrackedProperties() {
            return untrackedProperties;
        }
    }
}
