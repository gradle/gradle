/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.internal.file.collections;

import org.gradle.api.GradleException;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.DefaultFileVisitDetails;
import org.gradle.api.internal.file.FileSystemSubset;
import org.gradle.api.internal.file.pattern.PatternStep;
import org.gradle.api.internal.file.pattern.PatternStepFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.nativeintegration.services.FileSystems;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Directory walker that supports a single Ant-style include pattern
 * and an optional exclude spec. Efficient in the sense that it will only
 * exhaustively scan a directory hierarchy if, and from the point where,
 * a '**' pattern is encountered.
 */
public class SingleIncludePatternFileTree implements MinimalFileTree {
    private final File baseDir;
    private final String includePattern;
    private final List<String> patternSegments;
    private final Spec<FileTreeElement> excludeSpec;
    private final FileSystem fileSystem = FileSystems.getDefault();

    public SingleIncludePatternFileTree(File baseDir, String includePattern) {
        this(baseDir, includePattern, Specs.<FileTreeElement>satisfyNone());
    }

    public SingleIncludePatternFileTree(File baseDir, String includePattern, Spec<FileTreeElement> excludeSpec) {
        this.baseDir = baseDir;
        if (includePattern.endsWith("/") || includePattern.endsWith("\\")) {
            includePattern += "**";
        }
        this.includePattern = includePattern;
        this.patternSegments = Arrays.asList(includePattern.split("[/\\\\]"));
        this.excludeSpec = excludeSpec;
    }

    @Override
    public void visit(FileVisitor visitor) {
        doVisit(visitor, baseDir, new LinkedList<String>(), 0, new AtomicBoolean());
    }

    private void doVisit(FileVisitor visitor, File file, LinkedList<String> relativePath, int segmentIndex, AtomicBoolean stopFlag) {
        if (stopFlag.get()) {
            return;
        }

        String segment = patternSegments.get(segmentIndex);

        if (segment.contains("**")) {
            PatternSet patternSet = new PatternSet();
            patternSet.include(includePattern);
            patternSet.exclude(excludeSpec);
            DirectoryFileTree fileTree = new DirectoryFileTree(baseDir, patternSet, fileSystem);
            fileTree.visitFrom(visitor, file, new RelativePath(file.isFile(), relativePath.toArray(new String[relativePath.size()])));
        } else if (segment.contains("*") || segment.contains("?")) {
            PatternStep step = PatternStepFactory.getStep(segment, false);
            File[] children = file.listFiles();
            if (children == null) {
                if (!file.canRead()) {
                    throw new GradleException(String.format("Could not list contents of directory '%s' as it is not readable.", file));
                }
                // else, might be a link which points to nothing, or has been removed while we're visiting, or ...
                throw new GradleException(String.format("Could not list contents of '%s'.", file));
            }
            for (File child : children) {
                if (stopFlag.get()) {
                    break;
                }
                String childName = child.getName();
                if (step.matches(childName)) {
                    relativePath.addLast(childName);
                    doVisitDirOrFile(visitor, child, relativePath, segmentIndex + 1, stopFlag);
                    relativePath.removeLast();
                }
            }
        } else {
            relativePath.addLast(segment);
            doVisitDirOrFile(visitor, new File(file, segment), relativePath, segmentIndex + 1, stopFlag);
            relativePath.removeLast();
        }
    }

    private void doVisitDirOrFile(FileVisitor visitor, File file, LinkedList<String> relativePath, int segmentIndex, AtomicBoolean stopFlag) {
        if (file.isFile()) {
            if (segmentIndex == patternSegments.size()) {
                RelativePath path = new RelativePath(true, relativePath.toArray(new String[relativePath.size()]));
                FileVisitDetails details = new DefaultFileVisitDetails(file, path, stopFlag, fileSystem, fileSystem);
                if (!excludeSpec.isSatisfiedBy(details)) {
                    visitor.visitFile(details);
                }
            }
        } else if (file.isDirectory()) {
            RelativePath path = new RelativePath(false, relativePath.toArray(new String[relativePath.size()]));
            FileVisitDetails details = new DefaultFileVisitDetails(file, path, stopFlag, fileSystem, fileSystem);
            if (!excludeSpec.isSatisfiedBy(details)) {
                visitor.visitDir(details);
            }
            if (segmentIndex < patternSegments.size()) {
                doVisit(visitor, file, relativePath, segmentIndex, stopFlag);
            }
        }
    }

    @Override
    public String getDisplayName() {
        return "directory '" + baseDir + "' include '" + includePattern + "'";
    }

    @Override
    public void registerWatchPoints(FileSystemSubset.Builder builder) {
        builder.add(baseDir, new PatternSet().include(includePattern).exclude(excludeSpec));
    }

    @Override
    public void visitTreeOrBackingFile(FileVisitor visitor) {
        visit(visitor);
    }
}
