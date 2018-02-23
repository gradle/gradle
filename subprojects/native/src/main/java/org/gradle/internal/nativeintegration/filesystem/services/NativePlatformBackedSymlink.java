/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.nativeintegration.filesystem.services;

import net.rubygrapefruit.platform.FileInfo;
import net.rubygrapefruit.platform.PosixFiles;
import org.gradle.internal.nativeintegration.filesystem.Symlink;

import java.io.File;
import java.io.IOException;

class NativePlatformBackedSymlink implements Symlink {
    private final PosixFiles posixFiles;

    public NativePlatformBackedSymlink(PosixFiles posixFiles) {
        this.posixFiles = posixFiles;
    }

    @Override
    public boolean isSymlinkSupported() {
        return true;
    }

    @Override
    public void symlink(File link, File target) throws IOException {
        link.getParentFile().mkdirs();
        posixFiles.symlink(link, target.getPath());
    }

    @Override
    public boolean isSymlink(File suspect) {
        return posixFiles.stat(suspect).getType() == FileInfo.Type.Symlink;
    }
}
