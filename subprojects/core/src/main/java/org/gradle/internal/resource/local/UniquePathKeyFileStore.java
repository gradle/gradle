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

package org.gradle.internal.resource.local;

import org.apache.commons.io.FileUtils;
import org.gradle.api.Action;
import org.gradle.internal.hash.ChecksumService;
import org.jspecify.annotations.NullMarked;

import java.io.File;

/**
 * Assumes that files do not need to be replaced in the filestore.
 *
 * Can be used as an optimisation if path contains a checksum of the file, as there is no point to perform the replace in that circumstance.
 */
@NullMarked
public class UniquePathKeyFileStore extends DefaultPathKeyFileStore {

    public UniquePathKeyFileStore(ChecksumService checksumService, File baseDir) {
        super(checksumService, baseDir);
    }

    @Override
    public LocallyAvailableResource move(String path, File source) {
        LocallyAvailableResource entry = super.move(path, source);
        if (source.exists()) {
            FileUtils.deleteQuietly(source);
        }
        return entry;
    }

    @Override
    protected void doAdd(File destination, Action<File> action) {
        if (!destination.exists()) {
            super.doAdd(destination, action);
        }
    }
}
