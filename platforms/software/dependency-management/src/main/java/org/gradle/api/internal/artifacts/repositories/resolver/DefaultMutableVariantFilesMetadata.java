/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories.resolver;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.MutableVariantFilesMetadata;
import org.gradle.api.artifacts.VariantFileMetadata;

import java.util.List;

import static com.google.common.collect.Lists.newArrayList;

public class DefaultMutableVariantFilesMetadata implements MutableVariantFilesMetadata {

    private boolean clearExistingFiles = false;
    private final List<VariantFileMetadata> files = newArrayList();

    @Override
    public void removeAllFiles() {
        clearExistingFiles = true;
        files.clear();
    }

    @Override
    public void addFile(String name) {
        addFile(name, name);
    }

    @Override
    public void addFile(String name, String url) {
        for (VariantFileMetadata file : files) {
            if (file.getName().equals(name)) {
                throw new InvalidUserDataException("Cannot add file " + name + " (url: " + url + ") because it is already defined (url: " + file.getUrl() + ")");
            }
        }
        files.add(new DefaultVariantFileMetadata(name, url));
    }

    public boolean isClearExistingFiles() {
        return clearExistingFiles;
    }

    public List<VariantFileMetadata> getFiles() {
        return files;
    }
}
