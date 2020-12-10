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
import org.gradle.internal.snapshot.CaseSensitivity;
import org.gradle.internal.snapshot.ChildMap;
import org.gradle.internal.snapshot.ChildMapFactory;
import org.gradle.internal.snapshot.EmptyChildMap;
import org.gradle.internal.snapshot.VfsRelativePath;

import java.util.function.Consumer;

public class RelatedLocations {
    private volatile RelatedLocation root;
    private final CaseSensitivity caseSensitivity;

    public RelatedLocations(CaseSensitivity caseSensitivity) {
        this.caseSensitivity = caseSensitivity;
        this.root = new RelatedLocation(EmptyChildMap.getInstance(), ImmutableList.of(), caseSensitivity);
    }

    public ImmutableSet<Node> getNodesRelatedTo(String location) {
        VfsRelativePath relativePath = VfsRelativePath.of(location);
        ImmutableSet.Builder<Node> builder = ImmutableSet.builder();
        if (relativePath.length() == 0) {
            root.visitNodes(builder::add);
        } else {
            root.visitNodes(relativePath, builder::add);
        }
        return builder.build();
    }

    public synchronized void recordRelatedToNode(Node node, Iterable<String> locationsRelatedToNode) {
        for (String location : locationsRelatedToNode) {
            VfsRelativePath relativePath = VfsRelativePath.of(location);
            root = root.recordRelatedToNode(relativePath, node);
        }
    }

    public synchronized void clear() {
        root = new RelatedLocation(EmptyChildMap.getInstance(), ImmutableList.of(), caseSensitivity);
    }

    private static final class RelatedLocation {
        private final ChildMap<RelatedLocation> children;
        private final ImmutableList<Node> relatedNodes;
        private final CaseSensitivity caseSensitivity;

        private RelatedLocation(ChildMap<RelatedLocation> children, ImmutableList<Node> relatedNodes, CaseSensitivity caseSensitivity) {
            this.children = children;
            this.relatedNodes = relatedNodes;
            this.caseSensitivity = caseSensitivity;
        }

        public ImmutableList<Node> getNodes() {
            return relatedNodes;
        }

        public void visitNodes(VfsRelativePath relatedToLocation, Consumer<Node> nodeConsumer) {
            relatedNodes.forEach(nodeConsumer);
            children.withNode(relatedToLocation, caseSensitivity, new ChildMap.NodeHandler<RelatedLocation, String>() {
                @Override
                public String handleAsDescendantOfChild(VfsRelativePath pathInChild, RelatedLocation child) {
                    child.visitNodes(pathInChild, nodeConsumer);
                    return "";
                }

                @Override
                public String handleAsAncestorOfChild(String childPath, RelatedLocation child) {
                    child.visitNodes(nodeConsumer);
                    return "";
                }

                @Override
                public String handleExactMatchWithChild(RelatedLocation child) {
                    child.visitNodes(nodeConsumer);
                    return "";
                }

                @Override
                public String handleUnrelatedToAnyChild() {
                    return "";
                }
            });
        }

        public void visitNodes(Consumer<Node> nodeConsumer) {
            getNodes().forEach(nodeConsumer);
            children.visitChildren((__, child) -> child.visitNodes(nodeConsumer));
        }

        public RelatedLocation recordRelatedToNode(VfsRelativePath locationRelatedToNode, Node node) {
            if (locationRelatedToNode.length() == 0) {
                return new RelatedLocation(
                    children,
                    ImmutableList.<Node>builderWithExpectedSize(relatedNodes.size() + 1)
                        .addAll(relatedNodes)
                        .add(node)
                        .build(),
                    caseSensitivity
                );
            }
            ChildMap<RelatedLocation> newChildren = children.store(locationRelatedToNode, caseSensitivity, new ChildMap.StoreHandler<RelatedLocation>() {
                @Override
                public RelatedLocation handleAsDescendantOfChild(VfsRelativePath pathInChild, RelatedLocation child) {
                    return child.recordRelatedToNode(pathInChild, node);
                }

                @Override
                public RelatedLocation handleAsAncestorOfChild(String childPath, RelatedLocation child) {
                    ChildMap<RelatedLocation> singletonChild = ChildMapFactory.childMapFromSorted(ImmutableList.of(new ChildMap.Entry<>(VfsRelativePath.of(childPath).suffixStartingFrom(locationRelatedToNode.length() + 1).getAsString(), child)));
                    return new RelatedLocation(singletonChild, ImmutableList.of(node), caseSensitivity);
                }

                @Override
                public RelatedLocation mergeWithExisting(RelatedLocation child) {
                    return new RelatedLocation(child.getChildren(), ImmutableList.<Node>builderWithExpectedSize(child.getNodes().size() + 1).addAll(child.getNodes()).add(node).build(), caseSensitivity);
                }

                @Override
                public RelatedLocation createChild() {
                    return new RelatedLocation(EmptyChildMap.getInstance(), ImmutableList.of(node), caseSensitivity);
                }

                @Override
                public RelatedLocation createNodeFromChildren(ChildMap<RelatedLocation> children) {
                    return new RelatedLocation(children, ImmutableList.of(), caseSensitivity);
                }
            });
            return new RelatedLocation(newChildren, ImmutableList.of(), caseSensitivity);
        }

        public ChildMap<RelatedLocation> getChildren() {
            return children;
        }
    }
}
