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
            absoluteTarget = path.getParent().resolve(target).normalize();
            isRelative = isRelativeToRoot(path, absoluteTarget, relativePath);
        }
    }

    //TODO: cover with tests better
    private boolean isRelativeToRoot(Path path, Path absoluteTarget, RelativePath relativePath) { //TOOD: optimize
        // "/q/a/root/b/c/d" and "root/b/c/d"
        // "/q/target/some" vs "/q/a/root/er/target/some"
        Path rootAbsolutePath = path;
        for (String segment : relativePath.getSegments()) {
            rootAbsolutePath = rootAbsolutePath.getParent();
        }
        rootAbsolutePath = rootAbsolutePath.getParent();
        return absoluteTarget.startsWith(rootAbsolutePath);
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
        return Files.exists(absoluteTarget, LinkOption.NOFOLLOW_LINKS);
    }
}
