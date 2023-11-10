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

import org.apache.commons.io.FileUtils;
import org.gradle.internal.file.Chmod;
import org.gradle.util.internal.GFileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;


public abstract class CopyableFileTreeElement extends AbstractFileTreeElement {

    protected CopyableFileTreeElement(Chmod chmod) {
        super(chmod);
    }

    protected void copyPreservingPermissions(File target) {
        validateTimeStamps();
        try {
            if (isSymbolicLink()) {
                copySymlinkTo(target);
            } else if (isDirectory()) {
                GFileUtils.mkdirs(target);
                adaptPermissions(target);
            } else {
                GFileUtils.mkdirs(target.getParentFile());
                copyFile(target);
                adaptPermissions(target);
            }
        } catch (Exception e) {
            throw new CopyFileElementException(String.format("Could not copy %s to '%s'.", getDisplayName(), target), e);
        }
    }

    private void copyFile(File target) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(target)) {
            copyTo(outputStream);
        }
    }

    private void copySymlinkTo(File target) {
        Path symlinkTarget = new File(getSymbolicLinkDetails().getTarget().replace("/", File.separator)).toPath();
        try {
            Path path = target.toPath();
            if (target.exists()) {
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    FileUtils.deleteDirectory(target);
                } else {
                    Files.delete(path);
                }
            }
            Files.createSymbolicLink(path, symlinkTarget);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void adaptPermissions(File target) {
        int specMode = this.getPermissions().toUnixNumeric();
        getChmod().chmod(target, specMode);
    }
}
