/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.caching.internal.packaging.impl;

import org.apache.commons.io.FileUtils;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.file.TreeType;

import java.io.File;
import java.io.IOException;

public class DefaultTarPackerFileSystemSupport implements TarPackerFileSystemSupport {
    private final Deleter deleter;

    public DefaultTarPackerFileSystemSupport(Deleter deleter) {
        this.deleter = deleter;
    }

    @Override
    public void ensureFileIsMissing(File entry) throws IOException {
        if (!makeDirectory(entry.getParentFile())) {
            // Make sure tree is removed if it exists already
            deleter.deleteRecursively(entry);
        }
    }

    @Override
    public void ensureDirectoryForTree(TreeType type, File root) throws IOException {
        switch (type) {
            case DIRECTORY:
                deleter.ensureEmptyDirectory(root);
                break;
            case FILE:
                if (!makeDirectory(root.getParentFile())) {
                    if (root.exists()) {
                        deleter.deleteRecursively(root);
                    }
                }
                break;
            default:
                throw new AssertionError();
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean makeDirectory(File target) throws IOException {
        if (target.isDirectory()) {
            return false;
        } else if (target.isFile()) {
            deleter.delete(target);
        }
        FileUtils.forceMkdir(target);
        return true;
    }
}
