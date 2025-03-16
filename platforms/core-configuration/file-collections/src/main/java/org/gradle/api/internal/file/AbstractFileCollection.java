/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.file;

import com.google.common.collect.ImmutableSet;
import groovy.lang.Closure;
import org.gradle.api.file.DirectoryTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.FileBackedDirectoryFileTree;
import org.gradle.api.internal.file.collections.FileSystemMirroringFileTree;
import org.gradle.api.internal.provider.BuildableBackedProvider;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.api.tasks.util.internal.PatternSets;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.MutableBoolean;
import org.gradle.internal.deprecation.DocumentedFailure;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.util.internal.GUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class AbstractFileCollection implements FileCollectionInternal {
    protected final TaskDependencyFactory taskDependencyFactory;
    protected final Factory<PatternSet> patternSetFactory;

    protected AbstractFileCollection(TaskDependencyFactory taskDependencyFactory, Factory<PatternSet> patternSetFactory) {
        this.taskDependencyFactory = taskDependencyFactory;
        this.patternSetFactory = patternSetFactory;
    }

    @SuppressWarnings("deprecation")
    protected AbstractFileCollection(TaskDependencyFactory taskDependencyFactory) {
        this(taskDependencyFactory, PatternSets.getNonCachingPatternSetFactory());
    }

    public AbstractFileCollection() {
        this(DefaultTaskDependencyFactory.withNoAssociatedProject());
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    /**
     * This is final - override {@link #appendContents(TreeFormatter)}  instead to add type specific content.
     */
    @Override
    public final TreeFormatter describeContents(TreeFormatter formatter) {
        formatter.node("collection type: ").appendType(getClass()).append(" (id: ").append(String.valueOf(System.identityHashCode(this))).append(")");
        formatter.startChildren();
        appendContents(formatter);
        formatter.endChildren();
        return formatter;
    }

    protected void appendContents(TreeFormatter formatter) {
    }

    // This is final - override {@link TaskDependencyContainer#visitDependencies} to provide the dependencies instead.
    @Override
    public final TaskDependency getBuildDependencies() {
        assertCanCarryBuildDependencies();
        DefaultTaskDependency result = taskDependencyFactory.visitingDependencies(context -> context.add(AbstractFileCollection.this));
        result.setToStringProvider(() -> "Dependencies of " + getDisplayName());
        return result;
    }

    protected void assertCanCarryBuildDependencies() {
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        // Assume no dependencies
    }

    @Override
    public FileCollectionInternal replace(FileCollectionInternal original, Supplier<FileCollectionInternal> supplier) {
        if (original == this) {
            return supplier.get();
        }
        return this;
    }

    @Override
    public Set<File> getFiles() {
        // Use a JVM type here, rather than a Guava type, as some plugins serialize this return value and cannot deserialize the result
        Set<File> files = new LinkedHashSet<>();
        visitContents(new FileCollectionStructureVisitor() {
            @Override
            public void visitCollection(Source source, Iterable<File> contents) {
                for (File content : contents) {
                    files.add(content);
                }
            }

            private void addTreeContents(FileTreeInternal fileTree) {
                // TODO - add some convenient way to visit the files of the tree without collecting them into a set
                files.addAll(fileTree.getFiles());
            }

            @Override
            public void visitFileTree(File root, PatternSet patterns, FileTreeInternal fileTree) {
                addTreeContents(fileTree);
            }

            @Override
            public void visitFileTreeBackedByFile(File file, FileTreeInternal fileTree, FileSystemMirroringFileTree sourceTree) {
                addTreeContents(fileTree);
            }
        });
        return files;
    }

    @Override
    public File getSingleFile() throws IllegalStateException {
        Iterator<File> iterator = iterator();
        if (!iterator.hasNext()) {
            throw new IllegalStateException(String.format("Expected %s to contain exactly one file, however, it contains no files.", getDisplayName()));
        }
        File singleFile = iterator.next();
        if (iterator.hasNext()) {
            throw new IllegalStateException(String.format("Expected %s to contain exactly one file, however, it contains more than one file.", getDisplayName()));
        }
        return singleFile;
    }

    @Override
    public Iterator<File> iterator() {
        return getFiles().iterator();
    }

    @Override
    public String getAsPath() {
        showGetAsPathDeprecationWarning();
        return GUtil.asPath(this);
    }

    private void showGetAsPathDeprecationWarning() {
        List<String> filesAsPaths = this.getFiles().stream()
            .map(File::getPath)
            .filter(path -> path.contains(File.pathSeparator))
            .collect(Collectors.toList());
        if (!filesAsPaths.isEmpty()) {
            String displayedFilePaths = filesAsPaths.stream().map(path -> "'" + path + "'").collect(Collectors.joining(","));
            throw DocumentedFailure.builder()
                .withSummary(String.format(
                    "Converting files to a classpath string when their paths contain the path separator '%s' is not supported. " +
                        "The path separator is not a valid element of a file path.", File.pathSeparator))
                .withContext(String.format("Problematic paths in '%s' are: %s.",
                    getDisplayName(),
                    displayedFilePaths
                ))
                .withAdvice("Add the individual files to the file collection instead.")
                .withUpgradeGuideSection(7, "file_collection_to_classpath")
                .build();
        }
    }

    @Override
    public boolean contains(File file) {
        return getFiles().contains(file);
    }

    @Override
    public FileCollection plus(FileCollection collection) {
        return new UnionFileCollection(taskDependencyFactory, this, (FileCollectionInternal) collection);
    }

    @Override
    public Provider<Set<FileSystemLocation>> getElements() {
        return new BuildableBackedProvider<>(
            this,
            Cast.uncheckedCast(Set.class),
            new FileCollectionElementsFactory(this)
        );
    }

    private static class FileCollectionElementsFactory implements Factory<Set<FileSystemLocation>> {

        private final FileCollection fileCollection;

        private FileCollectionElementsFactory(FileCollection fileCollection) {
            this.fileCollection = fileCollection;
        }

        @Override
        public Set<FileSystemLocation> create() {
            // TODO - visit the contents of this collection instead.
            // This is just a super simple implementation for now
            Set<File> files = fileCollection.getFiles();
            ImmutableSet.Builder<FileSystemLocation> builder = ImmutableSet.builderWithExpectedSize(files.size());
            for (File file : files) {
                builder.add(new DefaultFileSystemLocation(file));
            }
            return builder.build();
        }
    }

    @Override
    public FileCollection minus(final FileCollection collection) {
        return new SubtractingFileCollection(this, collection);
    }

    @Override
    public void addToAntBuilder(Object builder, String nodeName, AntType type) {
        if (type == AntType.ResourceCollection) {
            addAsResourceCollection(builder, nodeName);
        } else if (type == AntType.FileSet) {
            addAsFileSet(builder, nodeName);
        } else {
            addAsMatchingTask(builder, nodeName);
        }
    }

    protected void addAsMatchingTask(Object builder, String nodeName) {
        new AntFileCollectionMatchingTaskBuilder(getAsFileTrees()).addToAntBuilder(builder, nodeName);
    }

    protected void addAsFileSet(Object builder, String nodeName) {
        new AntFileSetBuilder(getAsFileTrees()).addToAntBuilder(builder, nodeName);
    }

    protected void addAsResourceCollection(Object builder, String nodeName) {
        new AntFileCollectionBuilder(this).addToAntBuilder(builder, nodeName);
    }

    /**
     * Returns this collection as a set of {@link DirectoryFileTree} instance. These are used to map to Ant types.
     */
    protected Collection<DirectoryTree> getAsFileTrees() {
        List<DirectoryTree> fileTrees = new ArrayList<>();
        visitStructure(new FileCollectionStructureVisitor() {
            @Override
            public void visitCollection(Source source, Iterable<File> contents) {
                for (File file : contents) {
                    if (file.isFile()) {
                        fileTrees.add(new FileBackedDirectoryFileTree(file));
                    }
                }
            }

            @Override
            public void visitFileTree(File root, PatternSet patterns, FileTreeInternal fileTree) {
                if (root.isFile()) {
                    fileTrees.add(new FileBackedDirectoryFileTree(root));
                } else if (root.isDirectory()) {
                    fileTrees.add(new DirectoryTree() {
                        @Override
                        public File getDir() {
                            return root;
                        }

                        @Override
                        public PatternSet getPatterns() {
                            return patterns;
                        }
                    });
                }
            }

            @Override
            public void visitFileTreeBackedByFile(File file, FileTreeInternal fileTree, FileSystemMirroringFileTree sourceTree) {
                // Visit the contents of the tree to generate the tree
                if (visitAll(sourceTree)) {
                    fileTrees.add(sourceTree.getMirror());
                }
            }
        });
        return fileTrees;
    }

    /**
     * Visits all the files of the given tree.
     */
    protected static boolean visitAll(FileSystemMirroringFileTree tree) {
        final MutableBoolean hasContent = new MutableBoolean();
        tree.visit(new FileVisitor() {
            @Override
            public void visitDir(FileVisitDetails dirDetails) {
                dirDetails.getFile();
                hasContent.set(true);
            }

            @Override
            public void visitFile(FileVisitDetails fileDetails) {
                fileDetails.getFile();
                hasContent.set(true);
            }
        });
        return hasContent.get();
    }

    @Override
    public Object addToAntBuilder(Object node, String childNodeName) {
        addToAntBuilder(node, childNodeName, AntType.ResourceCollection);
        return this;
    }

    @Override
    public boolean isEmpty() {
        return getFiles().isEmpty();
    }

    @Override
    public FileTreeInternal getAsFileTree() {
        return new FileCollectionBackedFileTree(taskDependencyFactory, patternSetFactory, this);
    }

    @Override
    public FileCollection filter(Closure filterClosure) {
        return filter(Specs.convertClosureToSpec(filterClosure));
    }

    @Override
    public FileCollectionInternal filter(final Spec<? super File> filterSpec) {
        return new FilteredFileCollection(this, filterSpec);
    }

    /**
     * This is final - override {@link #visitContents(FileCollectionStructureVisitor)} instead to provide the contents.
     */
    @Override
    public final void visitStructure(FileCollectionStructureVisitor visitor) {
        if (visitor.startVisit(OTHER, this)) {
            visitContents(visitor);
        }
    }

    protected void visitContents(FileCollectionStructureVisitor visitor) {
        visitor.visitCollection(OTHER, this);
    }
}
