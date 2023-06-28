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

package org.gradle.tooling.internal.provider.continuous;

import org.gradle.api.file.ReadOnlyFileTreeElement;
import org.gradle.api.specs.Spec;
import org.gradle.execution.plan.SingleFileTreeElementMatcher;
import org.gradle.execution.plan.ValuedVfsHierarchy;
import org.gradle.execution.plan.ValuedVfsHierarchy.ValueVisitor;
import org.gradle.internal.collect.PersistentList;
import org.gradle.internal.file.Stat;
import org.gradle.internal.snapshot.CaseSensitivity;
import org.gradle.internal.snapshot.VfsRelativePath;

import java.io.File;
import java.util.function.Supplier;

/**
 * Allows recording and querying the input locations of a build.
 */
public class BuildInputHierarchy {
    private volatile ValuedVfsHierarchy<InputDeclaration> root;
    private final SingleFileTreeElementMatcher matcher;

    public BuildInputHierarchy(CaseSensitivity caseSensitivity, Stat stat) {
        this.root = ValuedVfsHierarchy.emptyHierarchy(caseSensitivity);
        this.matcher = new SingleFileTreeElementMatcher(stat);
    }

    /**
     * Returns whether the given location is an input to the build.
     */
    public boolean isInput(String location) {
        InputDeclarationVisitor visitor = new InputDeclarationVisitor();
        root.visitValues(location, visitor);
        return visitor.isInput();
    }

    public boolean isEmpty() {
        return root.isEmpty();
    }

    /**
     * Records that some locations are an input to the build.
     */
    public synchronized void recordInputs(Iterable<String> inputLocations) {
        for (String location : inputLocations) {
            VfsRelativePath relativePath = VfsRelativePath.of(location);
            root = root.recordValue(relativePath, ALL_CHILDREN_ARE_INPUTS);
        }
    }

    /**
     * Records that a fileTreeRoot with a filter is an input to the build.
     *
     * Only children of the fileTreeRoot that match the filter are considered inputs.
     */
    public synchronized void recordFilteredInput(String fileTreeRoot, Spec<ReadOnlyFileTreeElement> filter) {
        VfsRelativePath relativePath = VfsRelativePath.of(fileTreeRoot);
        root = root.recordValue(relativePath, new FilteredInputDeclaration(filter));
    }

    private static class InputDeclarationVisitor implements ValueVisitor<InputDeclaration> {
        private boolean input = false;

        private void foundInput() {
            this.input = true;
        }

        @Override
        public void visitExact(InputDeclaration value) {
            foundInput();
        }

        @Override
        public void visitAncestor(InputDeclaration ancestor, VfsRelativePath pathToVisitedLocation) {
            if (ancestor.contains(pathToVisitedLocation)) {
                foundInput();
            }
        }

        @Override
        public void visitChildren(PersistentList<InputDeclaration> values, Supplier<String> relativePathSupplier) {
            // A parent directory to the input is not an input.
            // As long as nothing within the input location changes we don't need to trigger a build.
            // If we would consider the parents as inputs, then the creation of parent directories for
            // an output file produced by the current build would directly trigger, though actually
            // everything is up-to-date.
        }

        public boolean isInput() {
            return input;
        }
    }

    private interface InputDeclaration {
        boolean contains(VfsRelativePath childPath);
    }

    private static final InputDeclaration ALL_CHILDREN_ARE_INPUTS = childPath -> true;

    private class FilteredInputDeclaration implements InputDeclaration {
        private final Spec<ReadOnlyFileTreeElement> spec;

        public FilteredInputDeclaration(Spec<ReadOnlyFileTreeElement> spec) {
            this.spec = spec;
        }

        @Override
        public boolean contains(VfsRelativePath childPath) {
            return matcher.elementWithRelativePathMatches(spec, new File(childPath.getAbsolutePath()), childPath.getAsString());
        }
    }
}
