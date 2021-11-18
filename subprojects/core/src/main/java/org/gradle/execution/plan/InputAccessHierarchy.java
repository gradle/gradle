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

package org.gradle.execution.plan;

import org.gradle.api.file.FileTreeElement;
import org.gradle.api.specs.Spec;
import org.gradle.execution.plan.ValuedVfsHierarchy.ValueVisitor;
import org.gradle.internal.collect.PersistentList;
import org.gradle.internal.file.Stat;
import org.gradle.internal.snapshot.CaseSensitivity;
import org.gradle.internal.snapshot.EmptyChildMap;
import org.gradle.internal.snapshot.VfsRelativePath;

import java.io.File;
import java.util.function.Supplier;

public class InputAccessHierarchy {
    private volatile ValuedVfsHierarchy<ChildAccess> root;
    private final Stat stat;

    public InputAccessHierarchy(CaseSensitivity caseSensitivity, Stat stat) {
        this.root = new ValuedVfsHierarchy<>(PersistentList.of(), EmptyChildMap.getInstance(), caseSensitivity);
        this.stat = stat;
    }

    /**
     * Returns all nodes which access the location.
     *
     * That includes node which access ancestors or children of the location.
     */
    public boolean isInput(String location) {
        AccessVisitor visitor = new AccessVisitor();
        VfsRelativePath relativePath = VfsRelativePath.of(location);
        if (relativePath.length() == 0) {
            root.visitAllValues(visitor);
        } else {
            root.visitValuesRelatedTo(relativePath, visitor);
        }
        return visitor.isInput();
    }

    public boolean isEmpty() {
        return root.isEmpty();
    }

    /**
     * Records that a node accesses the given locations.
     */
    public synchronized void recordInputs(Iterable<String> accessedLocations) {
        for (String location : accessedLocations) {
            VfsRelativePath relativePath = VfsRelativePath.of(location);
            root = root.recordValue(relativePath, ALL_CHILDREN);
        }
    }

    /**
     * Records that a node accesses the fileTreeRoot with a filter.
     *
     * The node only accesses children of the fileTreeRoot if they match the filter.
     */
    public synchronized void recordFilteredInput(String fileTreeRoot, Spec<FileTreeElement> filter) {
        VfsRelativePath relativePath = VfsRelativePath.of(fileTreeRoot);
        root = root.recordValue(relativePath, new FilteredChildAccess(filter));
    }

    /**
     * Removes all recorded nodes.
     */
    public synchronized void clear() {
        root = root.empty();
    }

    private static class AccessVisitor implements ValueVisitor<ChildAccess> {
        private boolean input = false;

        private void foundInput() {
            this.input = true;
        }

        @Override
        public void visitExact(ChildAccess value) {
            foundInput();
        }

        @Override
        public void visitAncestor(ChildAccess value, VfsRelativePath pathToVisitedLocation) {
            if (value.accessesChild(pathToVisitedLocation)) {
                foundInput();
            }
        }

        @Override
        public void visitChildren(PersistentList<ChildAccess> values, Supplier<String> relativePathSupplier) {
            this.input = true;
        }

        public boolean isInput() {
            return input;
        }
    }

    private interface ChildAccess {
        boolean accessesChild(VfsRelativePath childPath);
    }

    private static final ChildAccess ALL_CHILDREN = childPath -> true;

    private class FilteredChildAccess implements ChildAccess {
        private final Spec<FileTreeElement> spec;

        public FilteredChildAccess(Spec<FileTreeElement> spec) {
            this.spec = spec;
        }

        @Override
        public boolean accessesChild(VfsRelativePath childPath) {
            return ReadOnlyFileTreeElement.relativePathMatchesSpec(spec, new File(childPath.getAbsolutePath()), childPath.getAsString(), stat);
        }
    }
}
