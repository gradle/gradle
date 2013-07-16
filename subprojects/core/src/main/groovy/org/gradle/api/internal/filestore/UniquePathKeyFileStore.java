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

package org.gradle.api.internal.filestore;

import org.gradle.api.Action;
import org.gradle.internal.resource.local.LocallyAvailableResource;
import org.gradle.util.GFileUtils;

import java.io.File;

/**
 * Assumes that files do not need to be replaced in the filestore.
 *
 * Can be used as an optimisation if path contains a checksum of the file, as there is no point to perform the replace in that circumstance.
 */
public class UniquePathKeyFileStore extends PathKeyFileStore {

    public UniquePathKeyFileStore(File baseDir) {
        super(baseDir);
    }

    @Override
    public LocallyAvailableResource move(String path, File source) {
        LocallyAvailableResource entry = super.move(path, source);
        if (source.exists()) {
            GFileUtils.deleteQuietly(source);
        }
        return entry;
    }

    @Override
    protected LocallyAvailableResource doAdd(File destination, String failureDescription, Action<File> action) {
        if (destination.exists()) {
            return entryAt(destination);
        }
        return super.doAdd(destination, failureDescription, action);
    }
}
