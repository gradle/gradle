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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.specs.Spec;
import org.gradle.execution.plan.ValuedVfsHierarchy.ValueVisitor;
import org.gradle.internal.collect.PersistentList;
import org.gradle.internal.file.Stat;
import org.gradle.internal.snapshot.CaseSensitivity;
import org.gradle.internal.snapshot.VfsRelativePath;

import javax.annotation.concurrent.ThreadSafe;
import java.io.File;
import java.util.function.BiFunction;
import java.util.function.Supplier;


@ThreadSafe
public class ExecutionNodeAccessHierarchy {
    private volatile ValuedVfsHierarchy<NodeAccess> root;
    private final SingleFileTreeElementMatcher matcher;

    public ExecutionNodeAccessHierarchy(CaseSensitivity caseSensitivity, Stat stat) {
        this.root = ValuedVfsHierarchy.emptyHierarchy(caseSensitivity);
        this.matcher = new SingleFileTreeElementMatcher(stat);
    }

    /**
     * Returns all nodes which access the location.
     *
     * That includes node which access ancestors or children of the location.
     */
    public ImmutableSet<Node> getNodesAccessing(String location) {
        return visitValues(location, new CollectingNodeAccessVisitor());
    }

    /**
     * Visits all nodes which access the location.
     *
     * That includes node which access ancestors or children of the location.
     */
    public <T> T visitNodesAccessing(String location, T initialValue, BiFunction<T, ? super Node, T> visitor) {
        return visitValues(location, new AbstractNodeAccessVisitor<T>() {
            T currentValue = initialValue;

            @Override
            void visit(NodeAccess value) {
                currentValue = visitor.apply(currentValue, value.getNode());
            }

            @Override
            T getResult() {
                return currentValue;
            }
        });
    }

    /**
     * Returns all nodes which access the location, taking into account the filter.
     *
     * That includes nodes which access ancestors or children of the location.
     * Nodes accessing children of the location are only included if the children match the filter.
     */
    public ImmutableSet<Node> getNodesAccessing(String location, Spec<FileTreeElement> filter) {
        return visitValues(location, new CollectingNodeAccessVisitor() {
            @Override
            boolean acceptChildren(Supplier<String> relativePathSupplier) {
                String relativePathFromLocation = relativePathSupplier.get();
                return matcher.elementWithRelativePathMatches(filter, new File(location, relativePathFromLocation), relativePathFromLocation);
            }
        });
    }

    /**
     * Records that a node accesses the given locations.
     */
    public synchronized void recordNodeAccessingLocations(Node node, Iterable<String> accessedLocations) {
        for (String location : accessedLocations) {
            VfsRelativePath relativePath = VfsRelativePath.of(location);
            root = root.recordValue(relativePath, new DefaultNodeAccess(node));
        }
    }

    /**
     * Records that a node accesses the fileTreeRoot with a filter.
     *
     * The node only accesses children of the fileTreeRoot if they match the filter.
     * This is taken into account when using {@link #getNodesAccessing(String)} and {@link #getNodesAccessing(String, Spec)}.
     */
    public synchronized void recordNodeAccessingFileTree(Node node, String fileTreeRoot, Spec<FileTreeElement> filter) {
        VfsRelativePath relativePath = VfsRelativePath.of(fileTreeRoot);
        root = root.recordValue(relativePath, new FilteredNodeAccess(node, filter));
    }

    /**
     * Removes all recorded nodes.
     */
    public synchronized void clear() {
        root = root.empty();
    }

    private <T> T visitValues(String location, AbstractNodeAccessVisitor<T> visitor) {
        root.visitValues(location, visitor);
        return visitor.getResult();
    }

    private abstract static class AbstractNodeAccessVisitor<T> implements ValueVisitor<NodeAccess> {
        @Override
        public void visitExact(NodeAccess value) {
            visit(value);
        }

        @Override
        public void visitAncestor(NodeAccess value, VfsRelativePath pathToVisitedLocation) {
            if (value.accessesChild(pathToVisitedLocation)) {
                visit(value);
            }
        }

        @Override
        public void visitChildren(PersistentList<NodeAccess> values, Supplier<String> relativePathSupplier) {
            if (acceptChildren(relativePathSupplier)) {
                values.forEach(this::visit);
            }
        }

        @SuppressWarnings("UnusedVariable")
        boolean acceptChildren(Supplier<String> relativePathSupplier) {
            return true;
        }

        abstract void visit(NodeAccess value);

        abstract T getResult();
    }

    private static class CollectingNodeAccessVisitor extends AbstractNodeAccessVisitor<ImmutableSet<Node>> {
        private final ImmutableSet.Builder<Node> builder = ImmutableSet.builder();

        @Override
        void visit(NodeAccess value) {
            builder.add(value.getNode());
        }

        @Override
        ImmutableSet<Node> getResult() {
            return builder.build();
        }
    }

    private interface NodeAccess {
        Node getNode();

        boolean accessesChild(VfsRelativePath childPath);
    }

    private static class DefaultNodeAccess implements NodeAccess {

        private final Node node;

        public DefaultNodeAccess(Node node) {
            this.node = node;
        }

        @Override
        public Node getNode() {
            return node;
        }

        @Override
        public boolean accessesChild(VfsRelativePath childPath) {
            return true;
        }
    }

    private class FilteredNodeAccess implements NodeAccess {
        private final Node node;
        private final Spec<FileTreeElement> spec;

        public FilteredNodeAccess(Node node, Spec<FileTreeElement> spec) {
            this.node = node;
            this.spec = spec;
        }

        @Override
        public Node getNode() {
            return node;
        }

        @Override
        public boolean accessesChild(VfsRelativePath childPath) {
            return matcher.elementWithRelativePathMatches(spec, new File(childPath.getAbsolutePath()), childPath.getAsString());
        }
    }
}
