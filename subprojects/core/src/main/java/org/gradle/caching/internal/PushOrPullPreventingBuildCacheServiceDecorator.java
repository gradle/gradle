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

package org.gradle.caching.internal;

import org.gradle.StartParameter;
import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.BuildCacheService;
import org.gradle.caching.configuration.BuildCache;
import org.gradle.util.SingleMessageLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class PushOrPullPreventingBuildCacheServiceDecorator implements BuildCacheService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PushOrPullPreventingBuildCacheServiceDecorator.class);

    private final BuildCacheService delegate;
    private final boolean pushDisabled;
    private final boolean pullDisabled;

    public PushOrPullPreventingBuildCacheServiceDecorator(StartParameter startParameter, BuildCache buildCacheConfiguration, BuildCacheService delegate) {
        this.delegate = delegate;

        // TODO: Drop these system properties
        this.pushDisabled = !buildCacheConfiguration.isPush()
            || isDisabled(startParameter, "org.gradle.cache.tasks.push");
        this.pullDisabled = isDisabled(startParameter, "org.gradle.cache.tasks.pull");

        emitUsageMessage();
    }

    @Override
    public boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
        if (pullDisabled) {
            LOGGER.debug("Not loading cache entry with key {} because pulling from cache is disabled for the build", key);
            return false;
        }
        return delegate.load(key, reader);
    }

    @Override
    public void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {
        if (pushDisabled) {
            LOGGER.debug("Not storing cache entry with key {} because pushing to cache is disabled for the build", key);
        } else {
            delegate.store(key, writer);
        }
    }

    @Override
    public String getDescription() {
        return delegate.getDescription();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    private void emitUsageMessage() {
        if (pushDisabled) {
            if (pullDisabled) {
                LOGGER.warn("Neither pushing nor pulling from cache is enabled");
            } else {
                SingleMessageLogger.incubatingFeatureUsed("Retrieving task output from " + getDescription());
            }
        } else if (pullDisabled) {
            SingleMessageLogger.incubatingFeatureUsed("Pushing task output to " + getDescription());
        } else {
            SingleMessageLogger.incubatingFeatureUsed("Using " + getDescription());
        }
    }

    private static boolean isDisabled(StartParameter startParameter, String property) {
        String value = startParameter.getSystemPropertiesArgs().get(property);
        if (value == null) {
            value = System.getProperty(property);
        }
        if (value == null) {
            return false;
        }
        value = value.toLowerCase().trim();
        return value.equals("false");
    }
}
