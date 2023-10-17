/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.GradleException;
import org.gradle.api.file.RelativePath;
import org.gradle.api.file.SymbolicLinkDetails;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public class DefaultSymbolicLinkDetails implements SymbolicLinkDetails {
    private final Path target;
    private final Path absoluteTarget;
    private final boolean isRelative;

    public DefaultSymbolicLinkDetails(Path path, RelativePath relativePath) {
        try {
            target = Files.readSymbolicLink(path);
        } catch (IOException e) {
            throw new GradleException(String.format("Couldn't read symbolic link '%s'.", path), e);
        }
        if (target.isAbsolute()) {
            absoluteTarget = target;
            isRelative = false;
        } else {
            Path resolvedTarget = path.resolveSibling(target);
            boolean isRelative;
            Path absoluteTarget;
            try {
                absoluteTarget = resolvedTarget.toRealPath();
                isRelative = isRelativeToRoot(path, relativePath, resolvedTarget, absoluteTarget);
            } catch (IOException e) {
                absoluteTarget = resolvedTarget;
                isRelative = false;
            }
            this.absoluteTarget = absoluteTarget;
            this.isRelative = isRelative;
        }
    }

    /**
     * Comparing by string to mitigate unicode normalization.
     * Path should begin with the absolute path to the root spec and should not contain parts at the same level or upper at any point.
     **/
    private boolean isRelativeToRoot(Path path, RelativePath relativePath, Path resolvedTarget, Path absoluteTarget) {
        int rootAbsoluteLength = path.getNameCount() - relativePath.getSegments().length;
        if (absoluteTarget.getNameCount() < rootAbsoluteLength || absoluteTarget.getRoot() != path.getRoot()) {
            return false;
        }
        int current = 0;
        for (; current < rootAbsoluteLength; current++) {
            String absolutePart = absoluteTarget.getName(current).toString();
            if (!path.getName(current).toString().equals(absolutePart)) {
                return false;
            }
            if (!resolvedTarget.getName(current).toString().equals(absolutePart)) {
                return false;
            }
        }

        int absolutePathIndex = current;
        int rootIndex = current;
        int absoluteLength = absoluteTarget.getNameCount();
        for (; current < resolvedTarget.getNameCount(); current++) {
            String part = resolvedTarget.getName(current).toString();
            if (part.equals("..")) { // consistent with UnixPath and normalize
                absolutePathIndex--;
            } else if (part.equals(".")) {
                continue;
            } else {
                if (absolutePathIndex >= absoluteLength || !absoluteTarget.getName(absolutePathIndex).toString().equals(part)) {
                    Path currentPath = resolvedTarget.getRoot().resolve(resolvedTarget.subpath(0, current + 1));
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(currentPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                        if (attrs.isSymbolicLink()) {
                            return false;
                        }
                    } catch (IOException ioe) {
                        return false;
                    }
                }
                absolutePathIndex++;
            }
            if (absolutePathIndex < rootIndex) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isRelative() {
        return isRelative;
    }

    @Override
    public String getTarget() {
        return target.toString();
    }

    @Override
    public boolean targetExists() {
        return Files.exists(absoluteTarget);
    }
}
