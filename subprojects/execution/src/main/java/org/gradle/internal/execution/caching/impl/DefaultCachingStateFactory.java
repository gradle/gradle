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
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.caching.BuildCacheKey;
import org.gradle.internal.execution.caching.CachingDisabledReason;
import org.gradle.internal.execution.caching.CachingInputs;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.caching.CachingStateFactory;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Optional;

public class DefaultCachingStateFactory implements CachingStateFactory {
    private final Logger logger;

    public DefaultCachingStateFactory(Logger logger) {
        this.logger = logger;
    }

    @Override
    public final CachingState createCachingState(BeforeExecutionState beforeExecutionState, ImmutableList<CachingDisabledReason> cachingDisabledReasons) {
        ImmutableSortedMap<String, HashCode> inputValueFingerprints = collectInputValueFingerprints(beforeExecutionState.getInputProperties());

        Hasher hasher = Hashing.newHasher();

        logger.warn("Appending implementation to build cache key: {}",
            beforeExecutionState.getImplementation());
        beforeExecutionState.getImplementation().appendToHasher(hasher);

        beforeExecutionState.getAdditionalImplementations().forEach(additionalImplementation -> {
            logger.warn("Appending additional implementation to build cache key: {}",
                additionalImplementation);
            additionalImplementation.appendToHasher(hasher);
        });

        inputValueFingerprints.forEach((propertyName, fingerprint) -> {
            logger.warn("Appending input value fingerprint for '{}' to build cache key: {}",
                propertyName, fingerprint);
            hasher.putString(propertyName);
            hasher.putHash(fingerprint);
        });
        beforeExecutionState.getInputFileProperties().forEach((propertyName, fingerprint) -> {
            logger.warn("Appending input file fingerprints for '{}' to build cache key: {} - {}",
                propertyName, fingerprint.getHash(), fingerprint);
            hasher.putString(propertyName);
            hasher.putHash(fingerprint.getHash());
        });
        beforeExecutionState.getOutputFileLocationSnapshots().keySet().forEach(propertyName -> {
            logger.warn("Appending output property name to build cache key: {}", propertyName);
            hasher.putString(propertyName);
        });

        CachingInputs inputs = new DefaultCachingInputs(
            beforeExecutionState,
            inputValueFingerprints
        );

        if (cachingDisabledReasons.isEmpty()) {
            return new CachedState(hasher.hash(), inputs);
        } else {
            cachingDisabledReasons.forEach(reason ->
                logger.warn("Non-cacheable because {} [{}]", reason.getMessage(), reason.getCategory()));
            return new NonCachedState(hasher.hash(), cachingDisabledReasons, inputs);
        }
    }

    private static ImmutableSortedMap<String, HashCode> collectInputValueFingerprints(ImmutableSortedMap<String, ValueSnapshot> inputProperties) {
        ImmutableSortedMap.Builder<String, HashCode> inputPropertyHashesBuilder = ImmutableSortedMap.naturalOrder();
        inputProperties.forEach((propertyName, valueSnapshot) -> {
            Hasher hasher = Hashing.newHasher();
            valueSnapshot.appendToHasher(hasher);
            HashCode hash = hasher.hash();
            inputPropertyHashesBuilder.put(propertyName, hash);
        });
        return inputPropertyHashesBuilder.build();
    }

    private static class CachedState implements CachingState {
        private final BuildCacheKey key;
        private final CachingInputs inputs;

        public CachedState(HashCode key, CachingInputs inputs) {
            this.key = new DefaultBuildCacheKey(key);
            this.inputs = inputs;
        }

        @Override
        public Optional<BuildCacheKey> getKey() {
            return Optional.of(key);
        }

        @Override
        public ImmutableList<CachingDisabledReason> getDisabledReasons() {
            return ImmutableList.of();
        }

        @Override
        public Optional<CachingInputs> getInputs() {
            return Optional.of(inputs);
        }
    }

    private static class NonCachedState implements CachingState {
        private final BuildCacheKey key;
        private final ImmutableList<CachingDisabledReason> disabledReasons;
        private final CachingInputs inputs;

        public NonCachedState(@Nullable HashCode key, Iterable<CachingDisabledReason> disabledReasons, CachingInputs inputs) {
            this.key = key == null
                ? null
                : new DefaultBuildCacheKey(key);
            this.disabledReasons = ImmutableList.copyOf(disabledReasons);
            this.inputs = inputs;
        }

        @Override
        public Optional<BuildCacheKey> getKey() {
            return Optional.ofNullable(key);
        }

        @Override
        public ImmutableList<CachingDisabledReason> getDisabledReasons() {
            return disabledReasons;
        }

        @Override
        public Optional<CachingInputs> getInputs() {
            return Optional.of(inputs);
        }
    }

    private static class DefaultBuildCacheKey implements BuildCacheKey {
        private final HashCode hashCode;

        public DefaultBuildCacheKey(HashCode hashCode) {
            this.hashCode = hashCode;
        }

        @Override
        public String getHashCode() {
            return hashCode.toString();
        }

        @Override
        public byte[] toByteArray() {
            return hashCode.toByteArray();
        }

        @Override
        public String getDisplayName() {
            return getHashCode();
        }

        @Override
        public String toString() {
            return getHashCode();
        }
    }

    private static class DefaultCachingInputs implements CachingInputs {
        private final BeforeExecutionState beforeExecutionState;
        private final ImmutableSortedMap<String, HashCode> inputValueFingerprints;

        public DefaultCachingInputs(
            BeforeExecutionState beforeExecutionState,
            ImmutableSortedMap<String, HashCode> inputValueFingerprints
        ) {
            this.beforeExecutionState = beforeExecutionState;
            this.inputValueFingerprints = inputValueFingerprints;
        }

        @Override
        public BeforeExecutionState getBeforeExecutionState() {
            return beforeExecutionState;
        }

        @Override
        public ImmutableSortedMap<String, HashCode> getInputValueFingerprints() {
            return inputValueFingerprints;
        }
    }
}
