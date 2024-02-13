/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.initialization.transform.utils;

import com.google.common.base.Preconditions;
import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.vfs.FileSystemAccess;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class InstrumentationTransformUtils {

    public static File findFirstWithSuffix(File directory, String suffix) {
        try (Stream<Path> entries = Files.list(directory.toPath())) {
            return entries
                .filter(p -> p.toFile().getName().endsWith(suffix))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No file found in " + directory + " with extension: " + suffix))
                .toFile();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static boolean createNewFile(File file) {
        try {
            return file.createNewFile();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String hash(FileSystemAccess fileSystemAccess, File file) {
        Hasher hasher = Hashing.newHasher();
        FileSystemLocationSnapshot snapshot = fileSystemAccess.read(file.getAbsolutePath());
        Preconditions.checkArgument(snapshot.getType() != FileType.Missing, "File does not exist: " + file.getAbsolutePath());
        hasher.putHash(fileSystemAccess.read(file.getAbsolutePath()).getHash());
        hasher.putString(file.getName());
        return hasher.hash().toString();
    }
}
