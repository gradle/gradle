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
import com.google.common.collect.ImmutableSortedSet;
import org.gradle.caching.BuildCacheKey;
import org.gradle.internal.execution.caching.CachingDisabledReason;
import org.gradle.internal.execution.caching.CachingDisabledReasonCategory;
import org.gradle.internal.execution.caching.CachingInputs;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.caching.CachingStateBuilder;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

import javax.annotation.Nullable;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import java.util.Map;
import java.util.Optional;

import static org.gradle.internal.execution.caching.CachingDisabledReasonCategory.NON_CACHEABLE_ADDITIONAL_IMPLEMENTATION;
import static org.gradle.internal.execution.caching.CachingDisabledReasonCategory.NON_CACHEABLE_IMPLEMENTATION;

public class DefaultCachingStateBuilder implements CachingStateBuilder {
    private ImplementationSnapshot implementation;
    private ImmutableList<ImplementationSnapshot> additionalImplementations = ImmutableList.of();
    private final ImmutableSortedMap.Builder<String, HashCode> inputValueFingerprintsBuilder = ImmutableSortedMap.naturalOrder();
    private ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFileFingerprints = ImmutableSortedMap.of();
    private final ImmutableSortedMap.Builder<String, String> nonCacheableInputPropertiesBuilder = ImmutableSortedMap.naturalOrder();
    private ImmutableSortedSet<String> outputProperties = ImmutableSortedSet.of();
    private final ImmutableList.Builder<CachingDisabledReason> noCachingReasonsBuilder = ImmutableList.builder();

    @Override
    public final void withImplementation(ImplementationSnapshot implementation) {
        this.implementation = implementation;
        processImplementation(implementation);
    }

    @OverridingMethodsMustInvokeSuper
    protected void processImplementation(ImplementationSnapshot implementation) {
        if (implementation.isUnknown()) {
            noCachingReasonsBuilder.add(new CachingDisabledReason(
                NON_CACHEABLE_IMPLEMENTATION,
                "Implementation type " + implementation.getUnknownReason()
            ));
        }
    }

    @Override
    public final void withAdditionalImplementations(Iterable<ImplementationSnapshot> additionalImplementations) {
        this.additionalImplementations = ImmutableList.copyOf(additionalImplementations);
        for (ImplementationSnapshot additionalImplementation : additionalImplementations) {
            processAdditionalImplementation(additionalImplementation);
        }
    }

    @OverridingMethodsMustInvokeSuper
    protected void processAdditionalImplementation(ImplementationSnapshot additionalImplementation) {
        if (additionalImplementation.isUnknown()) {
            noCachingReasonsBuilder.add(new CachingDisabledReason(
                NON_CACHEABLE_ADDITIONAL_IMPLEMENTATION,
                "Additional implementation type " + additionalImplementation.getUnknownReason()
            ));
        }
    }

    @Override
    public final void withInputValueFingerprints(Map<String, ValueSnapshot> fingerprints) {
        fingerprints.forEach((propertyName, fingerprint) -> {
            Hasher hasher = Hashing.newHasher();
            fingerprint.appendToHasher(hasher);
            if (hasher.isValid()) {
                HashCode hash = hasher.hash();
                recordInputValueFingerprint(propertyName, hash);
            } else {
                markInputValuePropertyNotCacheable(propertyName, hasher.getInvalidReason());
            }
        });
    }

    @OverridingMethodsMustInvokeSuper
    protected void recordInputValueFingerprint(String propertyName, HashCode fingerprint) {
        inputValueFingerprintsBuilder.put(propertyName, fingerprint);
    }

    @OverridingMethodsMustInvokeSuper
    protected void markInputValuePropertyNotCacheable(String propertyName, String nonCacheableReason) {
        nonCacheableInputPropertiesBuilder.put(propertyName, nonCacheableReason);
    }

    @Override
    @OverridingMethodsMustInvokeSuper
    public void withInputFilePropertyFingerprints(Map<String, CurrentFileCollectionFingerprint> fingerprints) {
        this.inputFileFingerprints = ImmutableSortedMap.copyOf(fingerprints);
    }

    @Override
    @OverridingMethodsMustInvokeSuper
    public void withOutputPropertyNames(Iterable<String> propertyNames) {
        this.outputProperties = ImmutableSortedSet.copyOf(propertyNames);
    }

    @Override
    @OverridingMethodsMustInvokeSuper
    public void markNotCacheable(CachingDisabledReason reason) {
        noCachingReasonsBuilder.add(reason);
    }

    @Override
    public final CachingState build() {
        ImmutableSortedMap<String, HashCode> inputValueFingerprints = inputValueFingerprintsBuilder.build();

        Hasher hasher = Hashing.newHasher();
        implementation.appendToHasher(hasher);
        additionalImplementations.forEach(additionalImplementation -> {
            additionalImplementation.appendToHasher(hasher);
        });

        inputValueFingerprints.forEach((propertyName, fingerprint) -> {
            hasher.putString(propertyName);
            hasher.putHash(fingerprint);
        });

        inputFileFingerprints.forEach((propertyName, fingerprint) -> {
            hasher.putString(propertyName);
            hasher.putHash(fingerprint.getHash());
        });

        outputProperties.forEach(propertyName -> hasher.putString(propertyName));

        ImmutableSortedMap<String, String> nonCacheableInputPropertiesMap = nonCacheableInputPropertiesBuilder.build();
        if (!nonCacheableInputPropertiesMap.isEmpty()) {
            StringBuilder builder = new StringBuilder("Non-cacheable inputs: ");
            boolean first = true;
            for (Map.Entry<String, String> entry : nonCacheableInputPropertiesMap.entrySet()) {
                if (!first) {
                    builder.append(", ");
                }
                first = false;
                builder
                    .append("property '")
                    .append(entry.getKey())
                    .append("' ")
                    .append(entry.getValue());
            }
            String message = builder.toString();
            noCachingReasonsBuilder.add(new CachingDisabledReason(
                CachingDisabledReasonCategory.NON_CACHEABLE_INPUTS,
                message
            ));
            hasher.markAsInvalid(message);
        }
        ImmutableSortedSet<String> nonCacheableInputProperties = nonCacheableInputPropertiesMap.keySet();

        CachingInputs inputs = new DefaultCachingInputs(
            implementation,
            additionalImplementations,
            inputValueFingerprints,
            inputFileFingerprints,
            outputProperties,
            nonCacheableInputProperties
        );

        ImmutableList<CachingDisabledReason> cachingDisabledReasons = noCachingReasonsBuilder.build();

        if (cachingDisabledReasons.isEmpty()) {
            return new CachedState(hasher.hash(), inputs);
        } else {
            HashCode key = hasher.isValid()
                ? hasher.hash()
                : null;
            return new NonCachedState(key, cachingDisabledReasons, inputs);
        }
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
        ImplementationSnapshot implementation;
        ImmutableList<ImplementationSnapshot> additionalImplementations;
        ImmutableSortedMap<String, HashCode> inputValueFingerprints;
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFileFingerprints;
        ImmutableSortedSet<String> outputProperties;
        ImmutableSortedSet<String> nonCacheableInputProperties;

        public DefaultCachingInputs(
            ImplementationSnapshot implementation,
            ImmutableList<ImplementationSnapshot> additionalImplementations,
            ImmutableSortedMap<String, HashCode> inputValueFingerprints,
            ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFileFingerprints,
            ImmutableSortedSet<String> outputProperties,
            ImmutableSortedSet<String> nonCacheableInputProperties
        ) {
            this.implementation = implementation;
            this.additionalImplementations = additionalImplementations;
            this.inputValueFingerprints = inputValueFingerprints;
            this.inputFileFingerprints = inputFileFingerprints;
            this.outputProperties = outputProperties;
            this.nonCacheableInputProperties = nonCacheableInputProperties;
        }

        @Override
        public ImplementationSnapshot getImplementation() {
            return implementation;
        }

        @Override
        public ImmutableList<ImplementationSnapshot> getAdditionalImplementations() {
            return additionalImplementations;
        }

        @Override
        public ImmutableSortedMap<String, HashCode> getInputValueFingerprints() {
            return inputValueFingerprints;
        }

        @Override
        public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getInputFileFingerprints() {
            return inputFileFingerprints;
        }

        @Override
        public ImmutableSortedSet<String> getOutputProperties() {
            return outputProperties;
        }

        @Override
        public ImmutableSortedSet<String> getNonCacheableInputProperties() {
            return nonCacheableInputProperties;
        }
    }
}
