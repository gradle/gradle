/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.FileTree;
import org.gradle.api.resources.ReadableResource;
import org.gradle.api.resources.ResourceHandler;

public class DefaultArchiveOperations implements ArchiveOperations {

    private final FileOperations fileOperations;

    public DefaultArchiveOperations(FileOperations fileOperations) {
        this.fileOperations = fileOperations;
    }

    @Override
    public ReadableResource gzip(Object path) {
        return resources().gzip(path);
    }

    @Override
    public ReadableResource bzip2(Object path) {
        return resources().bzip2(path);
    }

    private ResourceHandler resources() {
        return fileOperations.getResources();
    }

    @Override
    public FileTree zipTree(Object zipPath) {
        return fileOperations.zipTree(zipPath);
    }

    @Override
    public FileTree tarTree(Object tarPath) {
        return fileOperations.tarTree(tarPath);
    }
}
