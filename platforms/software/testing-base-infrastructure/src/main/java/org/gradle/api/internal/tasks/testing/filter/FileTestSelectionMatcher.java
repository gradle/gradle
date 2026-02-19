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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

public class FileTestSelectionMatcher {
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
                    String packagified = "." + relativePath.replaceAll("/", ".");
                    return classTestSelectionMatcher.matchesTest(packagified, "");
                }
            }
            return false;
        } catch (IOException e) {
            return true;
        }
    }
}
