/*
 * Copyright 2015 the original author or authors.
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
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;

public class DefaultDirectoryWalker implements DirectoryWalker {
    private final FileSystem fileSystem;

    public DefaultDirectoryWalker(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    @Override
    public void walkDir(Path rootDir, RelativePath rootPath, FileVisitor visitor, Spec<? super FileTreeElement> spec, AtomicBoolean stopFlag, boolean postfix) {
        try {
            PathVisitor pathVisitor = new PathVisitor(spec, postfix, visitor, stopFlag, rootPath, fileSystem);
            Files.walkFileTree(rootDir, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, pathVisitor);
        } catch (IOException e) {
            throw new GradleException(String.format("Could not list contents of directory '%s'.", rootDir), e);
        }
    }
}
