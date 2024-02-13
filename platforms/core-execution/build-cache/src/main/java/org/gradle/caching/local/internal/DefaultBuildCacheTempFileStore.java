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

package org.gradle.caching.local.internal;

import org.apache.commons.io.FileUtils;
import org.gradle.internal.hash.HashCode;

import java.io.File;
import java.util.function.Consumer;

public class DefaultBuildCacheTempFileStore implements BuildCacheTempFileStore {

    private final TemporaryFileFactory temporaryFileFactory;

    public DefaultBuildCacheTempFileStore(TemporaryFileFactory temporaryFileFactory) {
        this.temporaryFileFactory = temporaryFileFactory;
    }

    @Override
    public void withTempFile(HashCode key, Consumer<? super File> action) {
        String hashCode = key.toString();
        File tempFile = null;
        try {
            tempFile = temporaryFileFactory.createTemporaryFile(hashCode + "-", PARTIAL_FILE_SUFFIX);
            action.accept(tempFile);
        } finally {
            FileUtils.deleteQuietly(tempFile);
        }
    }
}
