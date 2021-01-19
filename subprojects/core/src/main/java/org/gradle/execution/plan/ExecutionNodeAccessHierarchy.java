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
import org.gradle.execution.plan.ValuedPathHierarchy.ValueVisitor;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.file.Stat;
import org.gradle.internal.snapshot.CaseSensitivity;
import org.gradle.internal.snapshot.EmptyChildMap;
import org.gradle.internal.snapshot.VfsRelativePath;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.function.Supplier;

public class ExecutionNodeAccessHierarchy {
    private volatile ValuedPathHierarchy<RelatedNode> root;
    private final Stat stat;

    public ExecutionNodeAccessHierarchy(CaseSensitivity caseSensitivity, Stat stat) {
        this.root = new ValuedPathHierarchy<>(ImmutableList.of(), EmptyChildMap.getInstance(), caseSensitivity);
        this.stat = stat;
    }

    public ImmutableSet<Node> getNodesAccessing(String location) {
        return getNodesAccessing(location, null);
    }

    public ImmutableSet<Node> getNodesAccessing(String location, @Nullable Spec<FileTreeElement> filter) {
        VfsRelativePath relativePath = VfsRelativePath.of(location);
        ImmutableSet.Builder<Node> builder = ImmutableSet.builder();
        ValueVisitor<RelatedNode> nodeVisitor = filter == null
            ? new ValueVisitor<RelatedNode>() {
            @Override
            public void visitExact(RelatedNode value) {
                builder.add(value.getNode());
            }

            @Override
            public void visitAncestor(RelatedNode value, VfsRelativePath pathToVisitedLocation) {
                if (value.relatedToLocation(pathToVisitedLocation)) {
                    builder.add(value.getNode());
                }
            }

            @Override
            public void visitChildren(Iterable<RelatedNode> values, Supplier<String> relativePathSupplier) {
                values.forEach(node -> builder.add(node.getNode()));
            }
        } : new ValueVisitor<RelatedNode>() {
            @Override
            public void visitExact(RelatedNode value) {
                builder.add(value.getNode());
            }

            @Override
            public void visitAncestor(RelatedNode value, VfsRelativePath pathToVisitedLocation) {
                if (value.relatedToLocation(pathToVisitedLocation)) {
                    builder.add(value.getNode());
                }
            }

            @Override
            public void visitChildren(Iterable<RelatedNode> values, Supplier<String> relativePathSupplier) {
                String relativePathFromLocation = relativePathSupplier.get();
                if (filter.isSatisfiedBy(new LocationFileTreeElement(new File(location + "/" + relativePathFromLocation).getAbsolutePath(), relativePathFromLocation, stat))) {
                    values.forEach(node -> builder.add(node.getNode()));
                }
            }
        };
        if (relativePath.length() == 0) {
            root.visitValues(nodeVisitor);
        } else {
            root.visitValues(relativePath, nodeVisitor);
        }
        return builder.build();
    }

    public synchronized void recordNodeAccessingLocations(Node node, Iterable<String> accessedLocations) {
        for (String location : accessedLocations) {
            VfsRelativePath relativePath = VfsRelativePath.of(location);
            root = root.recordValue(relativePath, new DefaultRelatedNode(node));
        }
    }

    public synchronized void recordNodeAccessingFileTree(Node node, String fileTreeRoot, Spec<FileTreeElement> spec) {
        VfsRelativePath relativePath = VfsRelativePath.of(fileTreeRoot);
        root = root.recordValue(relativePath, new FilteredRelatedNode(node, spec));
    }

    public synchronized void clear() {
        root = root.empty();
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
