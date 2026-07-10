/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.nativeintegration.filesystem.jdk7;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Represents the Symlink facilities available on JDK 7 or better on Windows.
 * <p>
 * This subclass is used so that we don't accidentally start creating Symlinks
 * on Windows where it is often not safe to assume that a normal user will
 * have permission to use them.
 */
public class WindowsJdk7Symlink extends Jdk7Symlink {

    /**
     * Determine if the file indicated by {@code suspectPath} is some form of link
     * (symbolic link or junction).
     *
     * @param suspectPath the path to check
     * @return {@code true} if the path is a link, {@code false} otherwise
     */
    private static boolean modifiesRealPath(Path suspectPath) {
        Path parent = suspectPath.getParent();
        if (parent == null) {
            return false;
        }
        try {
            // Remove any symbolic links or junctions from the parent path
            // This prevents false positives when an ancestor is a link
            Path realParent = parent.toRealPath();
            Path suspectWithRealParent = realParent.resolve(suspectPath.getFileName());
            // If the suspect is a link, converting it to a real path will change the path
            Path realSuspect = suspectPath.toRealPath();
            return !suspectWithRealParent.equals(realSuspect);
        } catch (IOException ignored) {
            // Ignore IOException like Files.isSymbolicLink does
            return false;
        }
    }

    public WindowsJdk7Symlink() {
        super(false);
    }

    @Override
    public void symlink(File link, File target) throws IOException {
        throw new IOException("Creation of symlinks is not supported on this platform.");
    }

    @Override
    public boolean isSymlink(File suspect) {
        try {
            Path suspectPath = suspect.toPath();
            BasicFileAttributes attrs = Files.readAttributes(suspectPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attrs.isOther()) {
                // For cross-version compatibility, do a full check for junctions using path resolution logic
                return modifiesRealPath(suspectPath);
            }
            return super.isSymlink(suspect);
        } catch (IOException e) {
            return super.isSymlink(suspect);
        }
    }
}
