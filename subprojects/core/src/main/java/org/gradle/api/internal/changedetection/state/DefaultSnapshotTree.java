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

package org.gradle.api.internal.changedetection.state;

import org.gradle.api.internal.changedetection.resources.SnapshottableResource;

import java.io.IOException;
import java.util.List;

public class DefaultSnapshotTree implements SnapshotTree {

    private final SnapshottableResource root;
    private final List<? extends SnapshottableResource> elements;

    public DefaultSnapshotTree(SnapshottableResource root, List<? extends SnapshottableResource> elements) {
        this.root = root;
        this.elements = elements;
    }

    @Override
    public SnapshottableResource getRoot() {
        return root;
    }

    @Override
    public Iterable<? extends SnapshottableResource> getElements() {
        return elements;
    }

    @Override
    public void close() throws IOException {
    }
}
