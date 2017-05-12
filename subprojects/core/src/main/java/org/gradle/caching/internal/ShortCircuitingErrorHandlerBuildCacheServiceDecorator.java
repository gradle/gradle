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

import org.gradle.api.GradleException;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.api.logging.configuration.ShowStacktrace;
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
 * A decorator around a {@link org.gradle.caching.BuildCacheService} that passes through the underlying implementation
 * until a number {@link BuildCacheException}s occur.
 * The {@link BuildCacheException}s are counted and then ignored.
 *
 * After that the decorator short-circuits cache requests as no-ops.
 */
public class ShortCircuitingErrorHandlerBuildCacheServiceDecorator extends AbstractRoleAwareBuildCacheServiceDecorator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShortCircuitingErrorHandlerBuildCacheServiceDecorator.class);

    private boolean closed;
    private final int maxErrorCount;
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final AtomicInteger remainingErrorCount;
    private final LoggingConfiguration loggingConfiguration;
    private String disableMessage;

    public ShortCircuitingErrorHandlerBuildCacheServiceDecorator(int maxErrorCount, LoggingConfiguration loggingConfiguration, RoleAwareBuildCacheService delegate) {
        super(delegate);
        this.maxErrorCount = maxErrorCount;
        this.remainingErrorCount = new AtomicInteger(maxErrorCount);
        this.loggingConfiguration = loggingConfiguration;
    }

    @Override
    public boolean load(BuildCacheKey key, BuildCacheEntryReader reader) {
        if (enabled.get()) {
            try {
                LOGGER.debug("Loading entry {} from {} build cache", key, getRole());
                return super.load(key, reader);
            } catch (BuildCacheException e) {
                reportFailure("load", "from", key, e);
                recordFailure();
                // Assume cache didn't have it.
            } catch (RuntimeException e) {
                throw new GradleException("Could not load entry " + key + " from " + getRole() + " build cache", e);
            }
        }
        return false;
    }

    @Override
    public void store(BuildCacheKey key, BuildCacheEntryWriter writer) {
        if (enabled.get()) {
            try {
                LOGGER.debug("Storing entry {} in {} build cache", key, getRole());
                super.store(key, writer);
            } catch (BuildCacheException e) {
                reportFailure("store", "in", key, e);
                recordFailure();
                // Assume its OK to not push anything.
            } catch (Exception e) {
                reportFailure("store", "in", key, e);
                disableBuildCache("a non-recoverable error was encountered.");
            }
        }
    }

    @Override
    public void close() throws IOException {
        LOGGER.debug("Closing {} build cache", getRole());
        if (!closed) {
            if (!enabled.get()) {
                LOGGER.warn("The {} build cache was disabled during the build because {}", getRole(), disableMessage);
            }
            super.close();
        }
        closed = true;
    }

    private void reportFailure(String verb, String preposition, BuildCacheKey key, Exception e) {
        if (!LOGGER.isWarnEnabled()) {
            return;
        }
        if (loggingConfiguration.getShowStacktrace() == ShowStacktrace.INTERNAL_EXCEPTIONS) {
            LOGGER.warn("Could not {} entry {} {} {} build cache: {}", verb, key, preposition, getRole(), e.getMessage());
        } else {
            LOGGER.warn("Could not {} entry {} {} {} build cache", verb, key, preposition, getRole(), e);
        }
    }

    private void recordFailure() {
        if (remainingErrorCount.decrementAndGet() <= 0) {
            disableBuildCache(maxErrorCount + " recoverable errors were encountered.");
        }
    }

    private void disableBuildCache(String message) {
        if (enabled.compareAndSet(true, false)) {
            disableMessage = message;
            LOGGER.warn("The {} build cache is now disabled because {}", getRole(), message);
        }
    }
}
