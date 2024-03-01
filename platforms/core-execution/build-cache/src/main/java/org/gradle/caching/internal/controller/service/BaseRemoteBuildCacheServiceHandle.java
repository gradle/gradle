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
import java.io.File;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

public class BaseRemoteBuildCacheServiceHandle implements RemoteBuildCacheServiceHandle {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpFiringRemoteBuildCacheServiceHandle.class);

    protected enum Operation {
        LOAD("Load", "from"),
        STORE("Store", "in");

        private final String verb;
        private final String capitalizedVerb;
        private final String preposition;

        Operation(String capitalizedVerb, String preposition) {
            this.capitalizedVerb = capitalizedVerb;
            this.verb = capitalizedVerb.toLowerCase(Locale.ROOT);
            this.preposition = preposition;
        }

        public String describe(BuildCacheKey key, BuildCacheServiceRole role) {
            return capitalizedVerb + " entry " + key.getHashCode() + " " + preposition + " " + role.getDisplayName() + " build cache";
        }

        public String describeFailure(BuildCacheKey key, BuildCacheServiceRole role) {
            return "Could not " + verb + " entry " + key.getHashCode() + " " + preposition + " " + role.getDisplayName() + " build cache";
        }
    }

    protected final BuildCacheService service;

    protected final BuildCacheServiceRole role;
    private final boolean pushEnabled;
    private final boolean logStackTraces;
    private final boolean disableOnError;

    private boolean disabled;

    public BaseRemoteBuildCacheServiceHandle(
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
    public final Optional<BuildCacheLoadResult> maybeLoad(BuildCacheKey key, File loadTargetFile, Function<File, BuildCacheLoadResult> unpackFunction) {
        if (!canLoad()) {
            return Optional.empty();
        }
        String description = Operation.LOAD.describe(key, role);
        LOGGER.debug(description);
        LoadTarget loadTarget = new LoadTarget(loadTargetFile);
        try {
            loadInner(description, key, loadTarget);
        } catch (Exception e) {
            failure(Operation.LOAD, key, e);
        }
        return maybeUnpack(loadTarget, unpackFunction);
    }

    protected void loadInner(String description, BuildCacheKey key, LoadTarget loadTarget) {
        service.load(key, loadTarget);
    }

    protected void loadInner(BuildCacheKey key, BuildCacheEntryReader entryReader) {
        service.load(key, entryReader);
    }

    private static Optional<BuildCacheLoadResult> maybeUnpack(LoadTarget loadTarget, Function<File, BuildCacheLoadResult> unpackFunction) {
        if (loadTarget.isLoaded()) {
            return Optional.ofNullable(unpackFunction.apply(loadTarget.getFile()));
        }
        return Optional.empty();
    }

    @Override
    public boolean canStore() {
        return pushEnabled && !disabled;
    }

    @Override
    public final boolean maybeStore(BuildCacheKey key, File file) {
        if (!canStore()) {
            return false;
        }
        String description = Operation.STORE.describe(key, role);
        LOGGER.debug(description);
        try {
            storeInner(description, key, new StoreTarget(file));
            return true;
        } catch (Exception e) {
            failure(Operation.STORE, key, e);
            return false;
        }
    }

    protected void storeInner(String description, BuildCacheKey key, StoreTarget storeTarget) {
        service.store(key, storeTarget);
    }

    private void failure(Operation operation, BuildCacheKey key, Throwable failure) {
        if (disableOnError) {
            disabled = true;
            onCacheDisabledDueToFailure(key, operation, failure);
        }

        String description = operation.describeFailure(key, role);
        if (LOGGER.isWarnEnabled()) {
            if (logStackTraces) {
                LOGGER.warn(description, failure);
            } else {
                LOGGER.warn(description + ": " + failure.getMessage());
            }
        }
    }

    protected void onCacheDisabledDueToFailure(BuildCacheKey key, Operation operation, Throwable failure) {
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

