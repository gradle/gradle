/*
 * Copyright 2021 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import org.gradle.internal.snapshot.CaseSensitivity;
import org.gradle.internal.snapshot.ChildMap;
import org.gradle.internal.snapshot.ChildMapFactory;
import org.gradle.internal.snapshot.EmptyChildMap;
import org.gradle.internal.snapshot.VfsRelativePath;

import javax.annotation.CheckReturnValue;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * A hierarchy of relative paths with attached values.
 *
 * This is an immutable data structure.
 */
public final class ValuedPathHierarchy<T> {
    private final ImmutableList<T> values;

    private final ChildMap<ValuedPathHierarchy<T>> children;
    private final CaseSensitivity caseSensitivity;

    public ValuedPathHierarchy(ImmutableList<T> values, ChildMap<ValuedPathHierarchy<T>> children, CaseSensitivity caseSensitivity) {
        this.values = values;
        this.children = children;
        this.caseSensitivity = caseSensitivity;
    }

    /**
     * Returns an empty {@link ValuedPathHierarchy} with the same case sensitivity.
     */
    @CheckReturnValue
    public ValuedPathHierarchy<T> empty() {
        return new ValuedPathHierarchy<>(ImmutableList.of(), EmptyChildMap.getInstance(), caseSensitivity);
    }

    /**
     * Visits the values which are attached to ancestors and children of the given location.
     */
    public void visitValuesRelatedTo(VfsRelativePath location, ValueVisitor<T> visitor) {
        values.forEach(value -> visitor.visitAncestor(value, location));
        children.withNode(location, caseSensitivity, new ChildMap.NodeHandler<ValuedPathHierarchy<T>, String>() {
            @Override
            public String handleAsDescendantOfChild(VfsRelativePath pathInChild, ValuedPathHierarchy<T> child) {
                child.visitValuesRelatedTo(pathInChild, visitor);
                return "";
            }

            @Override
            public String handleAsAncestorOfChild(String childPathFromAncestor, ValuedPathHierarchy<T> child) {
                visitor.visitChildren(
                    child.getValues(),
                    () -> childPathFromAncestor.substring(location.length() + 1));
                child.visitAllChildren((nodes, relativePath) ->
                    visitor.visitChildren(nodes, () -> childPathFromAncestor.substring(location.length() + 1) + "/" + relativePath.get()));
                return "";
            }

            @Override
            public String handleExactMatchWithChild(ValuedPathHierarchy<T> child) {
                child.visitAllValues(visitor);
                return "";
            }

            @Override
            public String handleUnrelatedToAnyChild() {
                return "";
            }
        });
    }

    /**
     * Visits all values relative to the root.
     */
    public void visitAllValues(ValueVisitor<T> valueVisitor) {
        getValues().forEach(valueVisitor::visitExact);
        visitAllChildren(valueVisitor::visitChildren);
    }

    public interface ValueVisitor<T> {
        /**
         * The visited value is attached to the given location.
         */
        void visitExact(T value);

        /**
         * The visited value is an ancestor of the visited location
         */
        void visitAncestor(T value, VfsRelativePath pathToVisitedLocation);

        /**
         * The visited value is a child of the visited location.
         *
         * @param relativePathSupplier provides the relative path from the visited location to the path with the attached values.
         */
        void visitChildren(Iterable<T> values, Supplier<String> relativePathSupplier);
    }

    /**
     * Returns a new {@link ValuedPathHierarchy} with the value attached to the location.
     */
    @CheckReturnValue
    public ValuedPathHierarchy<T> recordValue(VfsRelativePath location, T value) {
        if (location.length() == 0) {
            return new ValuedPathHierarchy<>(
                ImmutableList.<T>builderWithExpectedSize(values.size() + 1)
                    .addAll(values)
                    .add(value)
                    .build(), children,
                caseSensitivity
            );
        }
        ChildMap<ValuedPathHierarchy<T>> newChildren = children.store(location, caseSensitivity, new ChildMap.StoreHandler<ValuedPathHierarchy<T>>() {
            @Override
            public ValuedPathHierarchy<T> handleAsDescendantOfChild(VfsRelativePath pathInChild, ValuedPathHierarchy<T> child) {
                return child.recordValue(pathInChild, value);
            }

            @Override
            public ValuedPathHierarchy<T> handleAsAncestorOfChild(String childPath, ValuedPathHierarchy<T> child) {
                ChildMap<ValuedPathHierarchy<T>> singletonChild = ChildMapFactory.childMapFromSorted(ImmutableList.of(new ChildMap.Entry<>(VfsRelativePath.of(childPath).suffixStartingFrom(location.length() + 1).getAsString(), child)));
                return new ValuedPathHierarchy<>(ImmutableList.of(value), singletonChild, caseSensitivity);
            }

            @Override
            public ValuedPathHierarchy<T> mergeWithExisting(ValuedPathHierarchy<T> child) {
                return new ValuedPathHierarchy<>(ImmutableList.<T>builderWithExpectedSize(child.getValues().size() + 1).addAll(child.getValues()).add(value).build(), child.getChildren(), caseSensitivity);
            }

            @Override
            public ValuedPathHierarchy<T> createChild() {
                return new ValuedPathHierarchy<>(ImmutableList.of(value), EmptyChildMap.getInstance(), caseSensitivity);
            }

            @Override
            public ValuedPathHierarchy<T> createNodeFromChildren(ChildMap<ValuedPathHierarchy<T>> children) {
                return new ValuedPathHierarchy<>(ImmutableList.of(), children, caseSensitivity);
            }
        });
        return new ValuedPathHierarchy<>(values, newChildren, caseSensitivity);
    }

    private ImmutableList<T> getValues() {
        return values;
    }

    private void visitAllChildren(BiConsumer<Iterable<T>, Supplier<String>> childConsumer) {
        children.visitChildren((childPath, child) -> {
            childConsumer.accept(
                child.getValues(),
                () -> childPath
            );
            child.visitAllChildren((grandChildren, relativePath) -> childConsumer.accept(grandChildren, () -> childPath + "/" + relativePath));
        });
    }

    private ChildMap<ValuedPathHierarchy<T>> getChildren() {
        return children;
    }
}
