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

import com.google.common.collect.ImmutableList;
import org.gradle.internal.execution.caching.CachingDisabledReason;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.caching.CachingStateFactory;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.slf4j.Logger;

public class DefaultCachingStateFactory implements CachingStateFactory {
    private final Logger logger;

    public DefaultCachingStateFactory(Logger logger) {
        this.logger = logger;
    }

    @Override
    public final CachingState createCachingState(BeforeExecutionState beforeExecutionState, ImmutableList<CachingDisabledReason> cachingDisabledReasons) {
        final Hasher cacheKeyHasher = Hashing.newHasher();

        logger.warn("Appending implementation to build cache key: {}",
            beforeExecutionState.getImplementation());
        beforeExecutionState.getImplementation().appendToHasher(cacheKeyHasher);

        beforeExecutionState.getAdditionalImplementations().forEach(additionalImplementation -> {
            logger.warn("Appending additional implementation to build cache key: {}",
                additionalImplementation);
            additionalImplementation.appendToHasher(cacheKeyHasher);
        });

        beforeExecutionState.getInputProperties().forEach((propertyName, valueSnapshot) -> {
            if (logger.isWarnEnabled()) {
                Hasher valueHasher = Hashing.newHasher();
                valueSnapshot.appendToHasher(valueHasher);
                logger.warn("Appending input value fingerprint for '{}' to build cache key: {}",
                    propertyName, valueHasher.hash());
            }
            cacheKeyHasher.putString(propertyName);
            valueSnapshot.appendToHasher(cacheKeyHasher);
        });

        beforeExecutionState.getInputFileProperties().forEach((propertyName, fingerprint) -> {
            logger.warn("Appending input file fingerprints for '{}' to build cache key: {} - {}",
                propertyName, fingerprint.getHash(), fingerprint);
            cacheKeyHasher.putString(propertyName);
            cacheKeyHasher.putHash(fingerprint.getHash());
        });

        beforeExecutionState.getOutputFileLocationSnapshots().keySet().forEach(propertyName -> {
            logger.warn("Appending output property name to build cache key: {}", propertyName);
            cacheKeyHasher.putString(propertyName);
        });

        if (cachingDisabledReasons.isEmpty()) {
            return CachingState.enabled(new DefaultBuildCacheKey(cacheKeyHasher.hash()), beforeExecutionState);
        } else {
            cachingDisabledReasons.forEach(reason ->
                logger.warn("Non-cacheable because {} [{}]", reason.getMessage(), reason.getCategory()));
            return CachingState.disabled(cachingDisabledReasons, new DefaultBuildCacheKey(cacheKeyHasher.hash()), beforeExecutionState);
        }
    }
}
