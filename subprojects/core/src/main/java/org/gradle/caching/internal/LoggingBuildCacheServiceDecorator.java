/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.caching.internal;

import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Logs <code>load()</code>, <code>store()</code> and <code>close()</code> methods and exceptions.
 */
public class LoggingBuildCacheServiceDecorator extends AbstractRoleAwareBuildCacheServiceDecorator {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingBuildCacheServiceDecorator.class);

    public LoggingBuildCacheServiceDecorator(RoleAwareBuildCacheService delegate) {
        super(delegate);
    }

    @Override
    public boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
        try {
            LOGGER.debug("Loading entry {} from {} build cache", key, getRole());
            return super.load(key, reader);
        } catch (BuildCacheException e) {
            LOGGER.warn("Could not load entry {} from {} build cache", key, getRole(), e);
            throw e;
        }
    }

    @Override
    public void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {
        try {
            LOGGER.debug("Storing entry {} in {} build cache", key, getRole());
            super.store(key, writer);
        } catch (BuildCacheException e) {
            LOGGER.warn("Could not store entry {} in {} build cache", key, getRole(), e);
            throw e;
        }
    }

    @Override
    public void close() throws IOException {
        LOGGER.debug("Closing {} build cache", getRole());
        super.close();
    }
}
