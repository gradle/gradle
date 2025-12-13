/*
 * Copyright 2017 the original author or authors.
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

import com.google.common.base.Preconditions;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.RelativePath;
import org.gradle.api.specs.Spec;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public class ReproducibleDirectoryWalker implements DirectoryWalker {
    private final static Comparator<PathWithAttributes> FILES_FIRST = Comparator
        .comparing(PathWithAttributes::isDirectory)
        .thenComparing(p -> p.path.toString());

    private final FileSystem fileSystem;

    public ReproducibleDirectoryWalker(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    @Override
    public void walkDir(Path rootDir, RelativePath rootPath, FileVisitor visitor, Spec<? super FileTreeElement> spec, AtomicBoolean stopFlag, boolean postfix) {
        PathVisitor pathVisitor = new PathVisitor(spec, postfix, visitor, stopFlag, rootPath, fileSystem);
        visit(toPathWithAttributes(rootDir), pathVisitor);
    }

    private static FileVisitResult visit(PathWithAttributes pathWithAttributes, PathVisitor pathVisitor) {
        if (pathWithAttributes.exception != null) {
            return pathVisitor.visitFileFailed(pathWithAttributes.path, pathWithAttributes.exception);
        }

        Path path = pathWithAttributes.path;
        BasicFileAttributes attrs = Preconditions.checkNotNull(pathWithAttributes.attributes);
        if (attrs.isDirectory()) {
            FileVisitResult fvr = pathVisitor.preVisitDirectory(path, attrs);
            if (fvr == FileVisitResult.SKIP_SUBTREE || fvr == FileVisitResult.TERMINATE) {
                return fvr;
            }
            IOException exception = null;
            try (Stream<Path> fileStream = Files.list(path)) {
                Iterable<PathWithAttributes> files = () -> fileStream
                    .map(ReproducibleDirectoryWalker::toPathWithAttributes)
                    .sorted(FILES_FIRST)
                    .iterator();
                for (PathWithAttributes child : files) {
                    FileVisitResult childResult = visit(child, pathVisitor);
                    if (childResult == FileVisitResult.TERMINATE) {
                        return childResult;
                    }
                }
            } catch (IOException e) {
                exception = e;
            }
            return pathVisitor.postVisitDirectory(path, exception);
        } else {
            return pathVisitor.visitFile(path, attrs);
        }
    }

    private static PathWithAttributes toPathWithAttributes(Path path) {
        try {
            // This is the same logic as used in java.nio.file.FileTreeWalker:
            // Attempts to get attributes of a file. If it fails it might mean this is a link
            // and a link target might not exist so try to get attributes of a link
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            return new PathWithAttributes(path, attributes, null);
        } catch (IOException ignored) {
            try {
                BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                return new PathWithAttributes(path, attributes, null);
            } catch (IOException e) {
                return new PathWithAttributes(path, null, e);
            }
        }
    }

    private static class PathWithAttributes {
        private final Path path;
        @Nullable
        private final BasicFileAttributes attributes;
        @Nullable
        private final IOException exception;

        public PathWithAttributes(Path path, @Nullable BasicFileAttributes attrs, @Nullable IOException exception) {
            this.path = path;
            this.attributes = attrs;
            this.exception = exception;
        }

        public boolean isDirectory() {
            return attributes != null && attributes.isDirectory();
        }
    }
}
