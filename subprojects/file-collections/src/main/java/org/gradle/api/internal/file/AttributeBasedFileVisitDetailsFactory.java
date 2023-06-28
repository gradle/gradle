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

package org.gradle.api.internal.file;

import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.RelativePath;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.os.OperatingSystem;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicBoolean;

public class AttributeBasedFileVisitDetailsFactory {
    public static FileVisitDetails getRootFileVisitDetails(
        Path path,
        RelativePath relativePath,
        @Nullable BasicFileAttributes attrs,
        AtomicBoolean stopFlag,
        FileSystem fileSystem
    ) {
        File file = path.toFile();
        if (attrs == null) {
            return new UnauthorizedFileVisitDetails(file, relativePath);
        } else if (attrs.isDirectory() && OperatingSystem.current() == OperatingSystem.WINDOWS) {
            // Workaround for https://github.com/gradle/gradle/issues/11577
            return new DefaultFileVisitDetails(file, relativePath, stopFlag, fileSystem, fileSystem);
        } else {
            return new AttributeBasedFileVisitDetails(file, relativePath, stopFlag, fileSystem, fileSystem, attrs);
        }
    }

    public static FileVisitDetails getRootFileVisitDetails(
        Path path,
        RelativePath relativePath,
        AtomicBoolean stopFlag,
        FileSystem fileSystem
    ) {
        BasicFileAttributes attrs = getAttributes(path);
        return getRootFileVisitDetails(path, relativePath, attrs, stopFlag, fileSystem);
    }

    public static FileVisitDetails getFileVisitDetails(
        Path path,
        RelativePath parentPath,
        @Nullable BasicFileAttributes attrs,
        AtomicBoolean stopFlag,
        FileSystem fileSystem
    ) {
        boolean isDirectory = attrs != null && attrs.isDirectory();
        RelativePath relativePath = parentPath.append(!isDirectory, path.getFileName().toString());
        return getRootFileVisitDetails(path, relativePath, attrs, stopFlag, fileSystem);
    }


    public static FileVisitDetails getFileVisitDetails(
        Path path,
        RelativePath parentPath,
        AtomicBoolean stopFlag,
        FileSystem fileSystem
    ) {
        BasicFileAttributes attrs = getAttributes(path);
        return getFileVisitDetails(path, parentPath, attrs, stopFlag, fileSystem);
    }

    private static @Nullable BasicFileAttributes getAttributes(Path path) {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class);
        } catch (IOException e) {
            try {
                return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            } catch (IOException next) {
                return null;
            }
        }
    }
}
