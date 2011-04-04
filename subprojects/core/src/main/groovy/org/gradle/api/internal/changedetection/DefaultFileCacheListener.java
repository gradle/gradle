/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.changedetection;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.collections.DefaultFileCollectionResolveContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DefaultFileCacheListener implements FileCacheListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFileCacheListener.class);

    public void cacheable(FileCollection files) {
        List<FileCollection> collections = new DefaultFileCollectionResolveContext().add(files).resolveAsFileCollections();
        for (FileCollection collection : collections) {
            LOGGER.debug("Can cache files for {}", collection);
        }
    }

    public void invalidate(FileCollection files) {
        List<FileCollection> collections = new DefaultFileCollectionResolveContext().add(files).resolveAsFileCollections();
        for (FileCollection collection : collections) {
            LOGGER.debug("Invalidate cached files for {}", collection);
        }
    }

    public void invalidateAll() {
        LOGGER.debug("Invalidate all cached files");
    }
}
