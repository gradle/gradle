/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.filter;

import org.gradle.util.internal.TextUtil;
import org.jspecify.annotations.NullMarked;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

/**
 * This class has one responsibility.
 *
 * It converts a given file path into something that looks like a class name using a given set of search roots
 * and then uses a regular {@link ClassTestSelectionMatcher} to match against the quasi-class name.
 *
 * The file extension is stripped before conversion so that the result looks like a class name
 * rather than including the extension as an extra segment.
 *
 * Examples:
 * src/test/definitions/foo.test becomes foo
 * src/test/definitions/sub/foo.test becomes sub.foo
 *
 * Limitations:
 * This means it's impossible to pick one file or the other if multiple roots have the same structure and file names.
 * It's also difficult to select files in the root of the directory without selecting other files too. This is similar to how the class matcher deals with default packages.
 * It's also currently impossible to select a subset of a given file.
 */
@NullMarked
class FileTestSelectionMatcher {
    private final ClassTestSelectionMatcher classTestSelectionMatcher;
    private final Collection<Path> roots;

    FileTestSelectionMatcher(ClassTestSelectionMatcher classTestSelectionMatcher, Collection<Path> roots) {
        this.classTestSelectionMatcher = classTestSelectionMatcher;
        this.roots = roots;
    }

    public boolean matchesFile(File file) {
        try {
            Path path = file.toPath().toRealPath();
            for (Path root : roots) {
                if (path.startsWith(root)) {
                    String relativePath = TextUtil.normaliseFileSeparators(root.relativize(path).toString());
                    String withoutExtension = removeExtension(relativePath);
                    String packagified = withoutExtension.replaceAll("/", ".");
                    return classTestSelectionMatcher.matchesTest(packagified, "");
                }
            }
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    private static String removeExtension(String relativePath) {
        int lastSlash = relativePath.lastIndexOf('/');
        int lastDot = relativePath.lastIndexOf('.');
        if (lastDot > lastSlash) {
            return relativePath.substring(0, lastDot);
        }
        return relativePath;
    }
}
