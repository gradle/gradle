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

package org.gradle.internal.execution.caching.impl;

import org.gradle.internal.execution.caching.CachingDisabledReason;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class LoggingCachingStateBuilder extends DefaultCachingStateBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingCachingStateBuilder.class);

    @Override
    protected void processImplementation(ImplementationSnapshot implementation) {
        LOGGER.warn("Appending implementation to build cache key: {}", implementation);
        super.processImplementation(implementation);
    }

    @Override
    protected void processAdditionalImplementation(ImplementationSnapshot additionalImplementation) {
        LOGGER.warn("Appending additional implementation to build cache key: {}", additionalImplementation);
        super.processAdditionalImplementation(additionalImplementation);
    }

    @Override
    protected void recordInputValueFingerprint(String propertyName, HashCode fingerprint) {
        LOGGER.warn("Appending input value fingerprint for '{}' to build cache key: {}", propertyName, fingerprint);
        super.recordInputValueFingerprint(propertyName, fingerprint);
    }

    @Override
    protected void markInputValuePropertyNotCacheable(String propertyName, String nonCacheableReason) {
        LOGGER.warn("Non-cacheable input value property '{}' {}.", propertyName, nonCacheableReason);
        super.markInputValuePropertyNotCacheable(propertyName, nonCacheableReason);
    }

    @Override
    public void withInputFilePropertyFingerprints(Map<String, CurrentFileCollectionFingerprint> fingerprints) {
        fingerprints.forEach((propertyName, fingerprint) -> {
            LOGGER.warn("Appending input file fingerprints for '{}' to build cache key: {} - {}", propertyName, fingerprint.getHash(), fingerprint);
        });
        super.withInputFilePropertyFingerprints(fingerprints);
    }

    @Override
    public void withOutputPropertyNames(Iterable<String> propertyNames) {
        propertyNames.forEach(propertyName -> {
            LOGGER.warn("Appending output property name to build cache key: {}", propertyName);
        });
        super.withOutputPropertyNames(propertyNames);
    }

    @Override
    public void markNotCacheable(CachingDisabledReason reason) {
        LOGGER.warn("Non-cacheable because {} [{}]", reason.getMessage(), reason.getCategory());
        super.markNotCacheable(reason);
    }
}
