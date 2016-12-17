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

import org.gradle.caching.BuildCache;
import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A decorator around a {@link BuildCache} that passes through the underlying implementation
 * until a number of errors occur.
 *
 * After that the decorator short-circuits cache requests as no-ops.
 */
public class ShortCircuitingErrorHandlerBuildCacheDecorator implements BuildCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShortCircuitingErrorHandlerBuildCacheDecorator.class);
    private final BuildCache delegate;
    private final int maxErrorCount;
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final AtomicInteger remainingErrorCount;

    public ShortCircuitingErrorHandlerBuildCacheDecorator(int maxErrorCount, BuildCache delegate) {
        this.delegate = delegate;
        this.maxErrorCount = maxErrorCount;
        this.remainingErrorCount = new AtomicInteger(maxErrorCount);
    }

    @Override
    public boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
        if (enabled.get()) {
            try {
                return delegate.load(key, reader);
            } catch (BuildCacheException e) {
                recordFailure();
                throw e;
            }
        }
        return false;
    }

    @Override
    public void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {
        if (enabled.get()) {
            try {
                delegate.store(key, writer);
            } catch (BuildCacheException e) {
                recordFailure();
                throw e;
            }
        }
    }

    @Override
    public String getDescription() {
        return delegate.getDescription();
    }

    @Override
    public void close() throws IOException {
        if (!enabled.get()) {
            LOGGER.warn("{} was disabled during the build after encountering {} errors.",
                getDescription(), maxErrorCount
            );
        }
        delegate.close();
    }

    private void recordFailure() {
        if (remainingErrorCount.decrementAndGet() <= 0) {
            if (enabled.compareAndSet(true, false)) {
                LOGGER.warn("{} is now disabled because {} errors were encountered", getDescription(), maxErrorCount);
            }
        }
    }
}
