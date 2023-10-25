/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.internal.nativeintegration.filesystem.FileModeAccessor;
import org.gradle.internal.nativeintegration.filesystem.FileModeMutator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;

import static org.gradle.internal.nativeintegration.filesystem.jdk7.PosixFilePermissionConverter.convertToInt;
import static org.gradle.internal.nativeintegration.filesystem.jdk7.PosixFilePermissionConverter.convertToPermissionsSet;

public class PosixJdk7FilePermissionHandler implements FileModeAccessor, FileModeMutator {

    @Override
    public int getUnixMode(File file, boolean followLinks) throws IOException {
        final PosixFileAttributes posixFileAttributes;
        if (followLinks) {
            posixFileAttributes = Files.readAttributes(file.toPath(), PosixFileAttributes.class);
        } else {
            posixFileAttributes = Files.readAttributes(file.toPath(), PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        }
        return convertToInt(posixFileAttributes.permissions());
    }

    @Override
    public void chmod(File f, int mode) throws IOException {
        PosixFileAttributeView fileAttributeView = Files.getFileAttributeView(f.toPath(), PosixFileAttributeView.class);
        fileAttributeView.setPermissions(convertToPermissionsSet(mode));
    }
}
