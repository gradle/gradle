/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.changedetection.resources;

import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.changedetection.state.SnapshottableResourceTree;
import org.gradle.internal.IoActions;

import java.io.IOException;

public abstract class AbstractSnapshotter implements ResourceSnapshotter {
    @Override
    public void snapshot(Snapshottable snapshottable, SnapshotCollector collector) {
        if (snapshottable instanceof SnapshottableResourceTree) {
            SnapshottableResourceTree tree = (SnapshottableResourceTree) snapshottable;
            try {
                snapshotTree(tree, collector);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                IoActions.closeQuietly(tree);
            }
        }
        if (snapshottable instanceof SnapshottableResource) {
            snapshotResource((SnapshottableResource) snapshottable, collector);
        }
        if (!(snapshottable instanceof SnapshottableResource || snapshottable instanceof SnapshottableResourceTree)) {
            throw new IllegalArgumentException("Can only snapshot resources or tree but not " + snapshottable);
        }
    }

    protected abstract void snapshotTree(SnapshottableResourceTree snapshottable, SnapshotCollector collector) throws IOException;

    protected abstract void snapshotResource(SnapshottableResource resource, SnapshotCollector collector);
}
