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

package org.gradle.caching.internal.controller.service;

import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.BuildCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

public class BaseBuildCacheServiceHandle implements BuildCacheServiceHandle {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpFiringBuildCacheServiceHandle.class);

    protected final BuildCacheService service;

    protected final BuildCacheServiceRole role;
    private final boolean pushEnabled;
    private final boolean logStackTraces;
    private final boolean disableOnError;

    private boolean disabled;

    public BaseBuildCacheServiceHandle(
        BuildCacheService service,
        boolean push,
        BuildCacheServiceRole role,
        boolean logStackTraces,
        boolean disableOnError
    ) {
        this.role = role;
        this.service = service;
        this.pushEnabled = push;
        this.logStackTraces = logStackTraces;
        this.disableOnError = disableOnError;
    }

    @Nullable
    @Override
    public BuildCacheService getService() {
        return service;
    }

    @Override
    public boolean canLoad() {
        return !disabled;
    }

    @Override
    public final void load(BuildCacheKey key, LoadTarget loadTarget) {
        String description = "Load entry " + key.getDisplayName() + " from " + role.getDisplayName() + " build cache";
        LOGGER.debug(description);
        try {
            loadInner(description, key, loadTarget);
        } catch (Exception e) {
            failure("load", "from", key, e);
        }
    }

    protected void loadInner(String description, BuildCacheKey key, LoadTarget loadTarget) {
        service.load(key, loadTarget);
    }

    protected void loadInner(BuildCacheKey key, BuildCacheEntryReader entryReader) {
        service.load(key, entryReader);
    }

    @Override
    public boolean canStore() {
        return pushEnabled && !disabled;
    }

    @Override
    public final void store(BuildCacheKey key, StoreTarget storeTarget) {
        String description = "Store entry " + key.getDisplayName() + " in " + role.getDisplayName() + " build cache";
        LOGGER.debug(description);
        try {
            storeInner(description, key, storeTarget);
        } catch (Exception e) {
            failure("store", "in", key, e);
        }
    }

    protected void storeInner(String description, BuildCacheKey key, StoreTarget storeTarget) {
        service.store(key, storeTarget);
    }

    private void failure(String verb, String preposition, BuildCacheKey key, Throwable e) {
        if (disableOnError) {
            disabled = true;
        }

        String description = "Could not " + verb + " entry " + key.getDisplayName() + " " + preposition + " " + role.getDisplayName() + " build cache";
        if (LOGGER.isWarnEnabled()) {
            if (logStackTraces) {
                LOGGER.warn(description, e);
            } else {
                LOGGER.warn(description + ": " + e.getMessage());
            }
        }
    }

    @Override
    public void close() {
        LOGGER.debug("Closing {} build cache", role.getDisplayName());
        if (disabled) {
            LOGGER.warn("The {} build cache was disabled during the build due to errors.", role.getDisplayName());
        }
        try {
            service.close();
        } catch (Exception e) {
            if (logStackTraces) {
                LOGGER.warn("Error closing {} build cache: ", role.getDisplayName(), e);
            } else {
                LOGGER.warn("Error closing {} build cache: {}", role.getDisplayName(), e.getMessage());
            }
        }
    }
}

