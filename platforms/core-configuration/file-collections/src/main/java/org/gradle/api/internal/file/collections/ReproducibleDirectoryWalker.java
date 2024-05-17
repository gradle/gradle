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

import org.gradle.api.GradleException;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.RelativePath;
import org.gradle.api.specs.Spec;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;

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
    private final FileSystem fileSystem;

    public ReproducibleDirectoryWalker(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    @Override
    public void walkDir(Path rootDir, RelativePath rootPath, FileVisitor visitor, Spec<? super FileTreeElement> spec, AtomicBoolean stopFlag, boolean postfix) {
        try {
            PathVisitor pathVisitor = new PathVisitor(spec, postfix, visitor, stopFlag, rootPath, fileSystem);
            visit(rootDir, pathVisitor);
        } catch (IOException e) {
            throw new GradleException(String.format("Could not list contents of directory '%s'.", rootDir), e);
        }
    }

    private static FileVisitResult visit(Path path, PathVisitor pathVisitor) throws IOException {
        BasicFileAttributes attrs;
        try {
            attrs = Files.readAttributes(path, BasicFileAttributes.class);
        } catch (IOException e) {
            try {
                attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            } catch (IOException next) {
                return pathVisitor.visitFileFailed(path, next);
            }
        }

        if (attrs.isDirectory()) {
            FileVisitResult fvr = pathVisitor.preVisitDirectory(path, attrs);
            if (fvr == FileVisitResult.SKIP_SUBTREE || fvr == FileVisitResult.TERMINATE) {
                return fvr;
            }
            IOException exception = null;
            try (Stream<Path> fileStream = Files.list(path)) {
                Iterable<Path> files = () -> fileStream.sorted(FILES_FIRST).iterator();
                for (Path child : files) {
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

    private final static Comparator<Path> FILES_FIRST = Comparator.<Path, Boolean>comparing(x -> x.toFile().isDirectory()).thenComparing(Path::toString);
}
