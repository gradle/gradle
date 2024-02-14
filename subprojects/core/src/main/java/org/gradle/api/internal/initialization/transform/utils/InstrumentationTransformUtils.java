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
}
