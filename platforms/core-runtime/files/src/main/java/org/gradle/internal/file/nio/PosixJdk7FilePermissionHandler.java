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

package org.gradle.internal.file.nio;

import org.gradle.internal.file.FileModeAccessor;
import org.gradle.internal.file.FileModeMutator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;

import static org.gradle.internal.file.nio.PosixFilePermissionConverter.convertToInt;
import static org.gradle.internal.file.nio.PosixFilePermissionConverter.convertToPermissionsSet;

@SuppressWarnings("Since15")
public class PosixJdk7FilePermissionHandler implements FileModeAccessor, FileModeMutator {

    @Override
    public int getUnixMode(File file) throws IOException {
        final PosixFileAttributes posixFileAttributes = Files.readAttributes(file.toPath(), PosixFileAttributes.class);
        return convertToInt(posixFileAttributes.permissions());
    }

    @Override
    public void chmod(File f, int mode) throws IOException {
        PosixFileAttributeView fileAttributeView = Files.getFileAttributeView(f.toPath(), PosixFileAttributeView.class);
        fileAttributeView.setPermissions(convertToPermissionsSet(mode));
    }
}
