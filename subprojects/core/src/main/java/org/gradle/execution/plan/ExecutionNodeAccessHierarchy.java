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
import org.gradle.api.file.RelativePath;
import org.gradle.api.specs.Spec;
import org.gradle.execution.plan.ValuedVfsHierarchy.ValueVisitor;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.collect.PersistentList;
import org.gradle.internal.file.Stat;
import org.gradle.internal.snapshot.CaseSensitivity;
import org.gradle.internal.snapshot.EmptyChildMap;
import org.gradle.internal.snapshot.VfsRelativePath;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.function.Supplier;

public class ExecutionNodeAccessHierarchy {
    private volatile ValuedVfsHierarchy<NodeAccess> root;
    private final Stat stat;

    public ExecutionNodeAccessHierarchy(CaseSensitivity caseSensitivity, Stat stat) {
        this.root = new ValuedVfsHierarchy<>(PersistentList.of(), EmptyChildMap.getInstance(), caseSensitivity);
        this.stat = stat;
    }

    /**
     * Returns all nodes which access the location.
     *
     * That includes node which access ancestors or children of the location.
     */
    public ImmutableSet<Node> getNodesAccessing(String location) {
        return visitValues(location, new AbstractNodeAccessVisitor() {
            @Override
            public void visitChildren(PersistentList<NodeAccess> values, Supplier<String> relativePathSupplier) {
                values.forEach(this::addNode);
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
        return visitValues(location, new AbstractNodeAccessVisitor() {
            @Override
            public void visitChildren(PersistentList<NodeAccess> values, Supplier<String> relativePathSupplier) {
                String relativePathFromLocation = relativePathSupplier.get();
                if (relativePathMatchesSpec(filter, new File(location, relativePathFromLocation), relativePathFromLocation)) {
                    values.forEach(this::addNode);
                }
            }
        });
    }

    private boolean relativePathMatchesSpec(Spec<FileTreeElement> filter, File element, String relativePathString) {
        // A better solution for output files would be to record the type of the output file and then using this type here instead of looking at the disk.
        // Though that is more involved and as soon as the file has been produced, the right file type will be detected here as well.
        boolean elementIsFile = !element.isDirectory();
        RelativePath relativePath = RelativePath.parse(elementIsFile, relativePathString);
        if (!filter.isSatisfiedBy(new ReadOnlyFileTreeElement(element, relativePath, stat))) {
            return false;
        }
        // All parent paths need to match the spec as well, since this is how we implement the file system walking for file tree.
        RelativePath parentRelativePath = relativePath.getParent();
        File parentFile = element.getParentFile();
        while (parentRelativePath != null && parentRelativePath != RelativePath.EMPTY_ROOT) {
            if (!filter.isSatisfiedBy(new ReadOnlyFileTreeElement(parentFile, parentRelativePath, stat))) {
                return false;
            }
            parentRelativePath = parentRelativePath.getParent();
            parentFile = parentFile.getParentFile();
        }
        return true;
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

    private ImmutableSet<Node> visitValues(String location, AbstractNodeAccessVisitor visitor) {
        VfsRelativePath relativePath = VfsRelativePath.of(location);
        if (relativePath.length() == 0) {
            root.visitAllValues(visitor);
        } else {
            root.visitValuesRelatedTo(relativePath, visitor);
        }
        return visitor.getResult();
    }

    private abstract static class AbstractNodeAccessVisitor implements ValueVisitor<NodeAccess> {

        private final ImmutableSet.Builder<Node> builder = ImmutableSet.builder();

        public void addNode(NodeAccess value) {
            builder.add(value.getNode());
        }

        @Override
        public void visitExact(NodeAccess value) {
            addNode(value);
        }

        @Override
        public void visitAncestor(NodeAccess value, VfsRelativePath pathToVisitedLocation) {
            if (value.accessesChild(pathToVisitedLocation)) {
                addNode(value);
            }
        }

        public ImmutableSet<Node> getResult() {
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
            return relativePathMatchesSpec(spec, new File(childPath.getAbsolutePath()), childPath.getAsString());
        }
    }

    private static class ReadOnlyFileTreeElement implements FileTreeElement {
        private final File file;
        private final RelativePath relativePath;
        private final Stat stat;

        public ReadOnlyFileTreeElement(File file, RelativePath relativePath, Stat stat) {
            this.file = file;
            this.relativePath = relativePath;
            this.stat = stat;
        }

        @Override
        public File getFile() {
            return file;
        }

        @Override
        public boolean isDirectory() {
            return !relativePath.isFile();
        }

        @Override
        public long getLastModified() {
            return file.lastModified();
        }

        @Override
        public long getSize() {
            return file.length();
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
            return relativePath.getPathString();
        }

        @Override
        public RelativePath getRelativePath() {
            return relativePath;
        }

        @Override
        public int getMode() {
            return stat.getUnixMode(file);
        }
    }
}
