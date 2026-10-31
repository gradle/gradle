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
import java.util.Optional;

/**
 * This class has one responsibility: it converts a given file path into something that looks like
 * a class name using a given set of search roots so that a regular {@link ClassTestSelectionMatcher}
 * can be used to match against the quasi-class name.
 * <p>
 * The file extension is stripped before conversion so that the result looks like a class name
 * rather than including the extension as an extra segment.
 * <p>
 * Examples:
 * <ul>
 *     <li>{@code src/test/definitions/foo.test becomes foo}</li>
 *     <li>{@code src/test/definitions/sub/foo.test becomes sub.foo}</li>
 * </ul>
 * <p>
 * Limitations:
 * <ul>
 *     <li>It's impossible to pick one file or the other if multiple roots have the same structure and file names.</li>
 *     <li>It's also difficult to select files in the root of the directory without selecting other files too. This is similar to how the class matcher deals with default packages.</li>
 *     <li>It's also currently impossible to select a subset of a given file.</li>
 * </ul>
 * <p>
 * Note: This matcher is not designed to select a subset of a given file.
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
        return nameFor(file)
            .map(name -> classTestSelectionMatcher.matchesTest(name, ""))
            .orElse(false);
    }

    /**
     * The name this matcher judges a file by: the file's path relative to the search root that
     * contains it, with the extension stripped and separators turned into dots, so that it looks
     * like a class name.
     * <p>
     * Empty when the file lies under none of the roots, or when its real path cannot be read.
     * Either way there is no name to match the file by, so this matcher cannot judge it at all.
     * That is a different answer from judging it and finding no match, which is what
     * {@link #matchesFile(File)} reports for both cases, and a caller that wants to hand an
     * unjudgeable file to a different matcher has to ask for the name to tell them apart.
     */
    public Optional<String> nameFor(File file) {
        try {
            Path path = file.toPath().toRealPath();
            for (Path root : roots) {
                if (path.startsWith(root)) {
                    String relativePath = TextUtil.normaliseFileSeparators(root.relativize(path).toString());
                    return Optional.of(removeExtension(relativePath).replace("/", "."));
                }
            }
        } catch (IOException e) {
            // A file whose real path cannot be read cannot be placed under a root, so it effectively has no name
        }
        return Optional.empty();
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
