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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.RelativePath;
import org.gradle.api.specs.Spec;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.file.Stat;
import org.gradle.internal.snapshot.CaseSensitivity;
import org.gradle.internal.snapshot.ChildMap;
import org.gradle.internal.snapshot.ChildMapFactory;
import org.gradle.internal.snapshot.EmptyChildMap;
import org.gradle.internal.snapshot.VfsRelativePath;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class ExecutionNodeAccessHierarchy {
    private volatile RelatedLocation<RelatedNode> root;
    private final CaseSensitivity caseSensitivity;
    private final Stat stat;

    public ExecutionNodeAccessHierarchy(CaseSensitivity caseSensitivity, Stat stat) {
        this.root = new RelatedLocation<>(EmptyChildMap.getInstance(), ImmutableList.of(), caseSensitivity);
        this.caseSensitivity = caseSensitivity;
        this.stat = stat;
    }

    public ImmutableSet<Node> getNodesAccessing(String location) {
        return getNodesAccessing(location, null);
    }

    public ImmutableSet<Node> getNodesAccessing(String location, @Nullable Spec<FileTreeElement> filter) {
        VfsRelativePath relativePath = VfsRelativePath.of(location);
        ImmutableSet.Builder<Node> builder = ImmutableSet.builder();
        NodeVisitor<RelatedNode> nodeVisitor = filter == null
            ? new NodeVisitor<RelatedNode>() {
            @Override
            public void visitExact(RelatedNode node) {
                builder.add(node.getNode());
            }

            @Override
            public void visitAncestor(RelatedNode node, VfsRelativePath pathToAccessedLocation) {
                if (node.relatedToLocation(pathToAccessedLocation)) {
                    builder.add(node.getNode());
                }
            }

            @Override
            public void visitChildren(Iterable<RelatedNode> nodes, Supplier<String> relativePathSupplier) {
                nodes.forEach(node -> builder.add(node.getNode()));
            }
        } : new NodeVisitor<RelatedNode>() {
            @Override
            public void visitExact(RelatedNode node) {
                builder.add(node.getNode());
            }

            @Override
            public void visitAncestor(RelatedNode node, VfsRelativePath pathToAccessedLocation) {
                if (node.relatedToLocation(pathToAccessedLocation)) {
                    builder.add(node.getNode());
                }
            }

            @Override
            public void visitChildren(Iterable<RelatedNode> nodes, Supplier<String> relativePathSupplier) {
                String relativePathFromLocation = relativePathSupplier.get();
                if (filter.isSatisfiedBy(new LocationFileTreeElement(new File(location + "/" + relativePathFromLocation).getAbsolutePath(), relativePathFromLocation, stat))) {
                    nodes.forEach(node -> builder.add(node.getNode()));
                }
            }
        };
        if (relativePath.length() == 0) {
            root.visitNodes(nodeVisitor);
        } else {
            root.visitNodes(relativePath, nodeVisitor);
        }
        return builder.build();
    }

    public synchronized void recordNodeAccessingLocations(Node node, Iterable<String> accessedLocations) {
        for (String location : accessedLocations) {
            VfsRelativePath relativePath = VfsRelativePath.of(location);
            root = root.recordRelatedToNode(relativePath, new DefaultRelatedNode(node));
        }
    }

    public synchronized void recordNodeAccessingFileTree(Node node, String fileTreeRoot, Spec<FileTreeElement> spec) {
        VfsRelativePath relativePath = VfsRelativePath.of(fileTreeRoot);
        root = root.recordRelatedToNode(relativePath, new FilteredRelatedNode(node, spec));
    }

    public synchronized void clear() {
        root = new RelatedLocation<>(EmptyChildMap.getInstance(), ImmutableList.of(), caseSensitivity);
    }

    private interface NodeVisitor<T> {
        void visitExact(T node);
        void visitAncestor(T node, VfsRelativePath pathToAccessedLocation);
        void visitChildren(Iterable<T> nodes, Supplier<String> relativePathSupplier);
    }

    private static final class RelatedLocation<T> {
        private final ImmutableList<T> relatedNodes;
        private final ChildMap<RelatedLocation<T>> children;
        private final CaseSensitivity caseSensitivity;

        private RelatedLocation(ChildMap<RelatedLocation<T>> children, ImmutableList<T> relatedNodes, CaseSensitivity caseSensitivity) {
            this.children = children;
            this.relatedNodes = relatedNodes;
            this.caseSensitivity = caseSensitivity;
        }

        public ImmutableList<T> getNodes() {
            return relatedNodes;
        }

        public void visitNodes(VfsRelativePath relatedToLocation, NodeVisitor<T> visitor) {
            relatedNodes.forEach(node -> visitor.visitAncestor(node, relatedToLocation));
            children.withNode(relatedToLocation, caseSensitivity, new ChildMap.NodeHandler<RelatedLocation<T>, String>() {
                @Override
                public String handleAsDescendantOfChild(VfsRelativePath pathInChild, RelatedLocation<T> child) {
                    child.visitNodes(pathInChild, visitor);
                    return "";
                }

                @Override
                public String handleAsAncestorOfChild(String childPathFromAncestor, RelatedLocation<T> child) {
                    visitor.visitChildren(
                        child.getNodes(),
                        () -> childPathFromAncestor.substring(relatedToLocation.length() + 1));
                    child.visitAllChildren((nodes, relativePath) ->
                        visitor.visitChildren(nodes, () -> childPathFromAncestor.substring(relatedToLocation.length() + 1) + "/" + relativePath.get()));
                    return "";
                }

                @Override
                public String handleExactMatchWithChild(RelatedLocation<T> child) {
                    child.visitNodes(visitor);
                    return "";
                }

                @Override
                public String handleUnrelatedToAnyChild() {
                    return "";
                }
            });
        }

        public void visitNodes(NodeVisitor<T> nodeVisitor) {
            getNodes().forEach(nodeVisitor::visitExact);
            visitAllChildren(nodeVisitor::visitChildren);
        }

        public void visitAllChildren(BiConsumer<Iterable<T>, Supplier<String>> childConsumer) {
            children.visitChildren((childPath, child) -> {
                childConsumer.accept(
                    child.getNodes(),
                    () -> childPath
                );
                child.visitAllChildren((grandChildren, relativePath) -> childConsumer.accept(grandChildren, () -> childPath + "/" + relativePath));
            });
        }

        public RelatedLocation<T> recordRelatedToNode(VfsRelativePath locationRelatedToNode, T node) {
            if (locationRelatedToNode.length() == 0) {
                return new RelatedLocation<>(
                    children,
                    ImmutableList.<T>builderWithExpectedSize(relatedNodes.size() + 1)
                        .addAll(relatedNodes)
                        .add(node)
                        .build(),
                    caseSensitivity
                );
            }
            ChildMap<RelatedLocation<T>> newChildren = children.store(locationRelatedToNode, caseSensitivity, new ChildMap.StoreHandler<RelatedLocation<T>>() {
                @Override
                public RelatedLocation<T> handleAsDescendantOfChild(VfsRelativePath pathInChild, RelatedLocation<T> child) {
                    return child.recordRelatedToNode(pathInChild, node);
                }

                @Override
                public RelatedLocation<T> handleAsAncestorOfChild(String childPath, RelatedLocation<T> child) {
                    ChildMap<RelatedLocation<T>> singletonChild = ChildMapFactory.childMapFromSorted(ImmutableList.of(new ChildMap.Entry<>(VfsRelativePath.of(childPath).suffixStartingFrom(locationRelatedToNode.length() + 1).getAsString(), child)));
                    return new RelatedLocation<>(singletonChild, ImmutableList.of(node), caseSensitivity);
                }

                @Override
                public RelatedLocation<T> mergeWithExisting(RelatedLocation<T> child) {
                    return new RelatedLocation<>(child.getChildren(), ImmutableList.<T>builderWithExpectedSize(child.getNodes().size() + 1).addAll(child.getNodes()).add(node).build(), caseSensitivity);
                }

                @Override
                public RelatedLocation<T> createChild() {
                    return new RelatedLocation<>(EmptyChildMap.getInstance(), ImmutableList.of(node), caseSensitivity);
                }

                @Override
                public RelatedLocation<T> createNodeFromChildren(ChildMap<RelatedLocation<T>> children) {
                    return new RelatedLocation<>(children, ImmutableList.of(), caseSensitivity);
                }
            });
            return new RelatedLocation<>(newChildren, relatedNodes, caseSensitivity);
        }

        public ChildMap<RelatedLocation<T>> getChildren() {
            return children;
        }
    }

    private interface RelatedNode {
        Node getNode();
        boolean relatedToLocation(VfsRelativePath relativePath);
    }

    private static class DefaultRelatedNode implements RelatedNode {

        private final Node node;

        public DefaultRelatedNode(Node node) {
            this.node = node;
        }

        @Override
        public Node getNode() {
            return node;
        }

        @Override
        public boolean relatedToLocation(VfsRelativePath relativePath) {
            return true;
        }
    }

    private class FilteredRelatedNode implements RelatedNode {
        private final Node node;
        private final Spec<FileTreeElement> spec;

        public FilteredRelatedNode(Node node, Spec<FileTreeElement> spec) {
            this.node = node;
            this.spec = spec;
        }

        @Override
        public Node getNode() {
            return node;
        }

        @Override
        public boolean relatedToLocation(VfsRelativePath relativePath) {
            return spec.isSatisfiedBy(new LocationFileTreeElement(relativePath.getAbsolutePath(), relativePath.getAsString(), stat));
        }
    }

    private static class LocationFileTreeElement implements FileTreeElement {
        private final File file;
        private final boolean isFile;
        private final String relativePath;
        private final Stat stat;

        public LocationFileTreeElement(String absolutePath, String relativePath, Stat stat) {
            this.file = new File(absolutePath);
            this.isFile = file.isFile();
            this.relativePath = relativePath;
            this.stat = stat;
        }

        @Override
        public File getFile() {
            return file;
        }

        @Override
        public boolean isDirectory() {
            return !isFile;
        }

        @Override
        public long getLastModified() {
            return getFile().lastModified();
        }

        @Override
        public long getSize() {
            return getFile().length();
        }

        @Override
        public InputStream open() {
            try {
                return Files.newInputStream(file.toPath());
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        @Override
        public void copyTo(OutputStream output) {
            throw new UnsupportedOperationException("Copy to not supported for filters");
        }

        @Override
        public boolean copyTo(File target) {
            throw new UnsupportedOperationException("Copy to not supported for filters");
        }

        @Override
        public String getName() {
            return file.getName();
        }

        @Override
        public String getPath() {
            return getRelativePath().getPathString();
        }

        @Override
        public RelativePath getRelativePath() {
            return RelativePath.parse(isFile, relativePath);
        }

        @Override
        public int getMode() {
            return stat.getUnixMode(file);
        }
    }
}
