/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import org.gradle.internal.fingerprint.hashing.RegularFileSnapshotContext;
import org.gradle.internal.fingerprint.hashing.ResourceHasher;
import org.gradle.internal.fingerprint.hashing.ZipEntryContext;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.io.IoFunction;
import org.gradle.internal.io.IoSupplier;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Optional;

abstract class FallbackHandlingResourceHasher implements ResourceHasher {
    private final ResourceHasher delegate;

    public FallbackHandlingResourceHasher(ResourceHasher delegate) {
        this.delegate = delegate;
    }

    @Override
    public void appendConfigurationToHasher(Hasher hasher) {
        delegate.appendConfigurationToHasher(hasher);
        hasher.putString(this.getClass().getName());
    }

    @Nullable
    @Override
    public HashCode hash(RegularFileSnapshotContext snapshotContext) throws IOException {
        return Optional.of(snapshotContext)
            .flatMap(path -> tryHashWithFallback(snapshotContext))
            .orElseGet(IoSupplier.wrap(() -> delegate.hash(snapshotContext)));
    }

    @Nullable
    @Override
    public HashCode hash(ZipEntryContext zipEntryContext) throws IOException {
        Optional<ZipEntryContext> safeContext = Optional.of(zipEntryContext)
            .flatMap(ZipEntryContext::withFallbackSafety);

        // We can't just map() here because the delegate can return null, which means we can't
        // distinguish between a context unsafe for fallback and a call to a delegate that
        // returns null.  To avoid calling the delegate twice, we use a conditional instead.
        if (safeContext.isPresent()) {
            // If this is a manifest file and we can fallback safely, attempt to hash the manifest.
            // If we encounter an error, hash with the delegate using the safe fallback.
            return safeContext.flatMap(IoFunction.wrap(this::tryHashWithFallback))
                .orElseGet(IoSupplier.wrap(() -> delegate.hash(safeContext.get())));
        } else {
            // If this is not a manifest file, or we cannot fallback safely, hash with the delegate.
            return delegate.hash(zipEntryContext);
        }
    }

    /**
     * Try to hash the resource, and signal for fallback if it can't be hashed.
     *
     * @return An Optional containing the hash, or an empty Optional if fallback should be triggered
     */
    abstract Optional<HashCode> tryHashWithFallback(RegularFileSnapshotContext snapshotContext);

    /**
     * Try to hash the resource, and signal for fallback if it can't be hashed.
     *
     * @return An Optional containing the hash, or an empty Optional if fallback should be triggered
     */
    abstract Optional<HashCode> tryHashWithFallback(ZipEntryContext zipEntryContext);
}
