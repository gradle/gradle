/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.tooling.internal.consumer;

import org.gradle.tooling.Failure;
import org.gradle.tooling.internal.protocol.InternalFailure;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Retains consumer failures by the identity of the protocol failures from which they were converted.
 *
 * <p>The cache is safe to use when model queries run in parallel. Its owner determines the scope in which failure
 * identity is preserved.</p>
 */
@NullMarked
public final class ClientFailureCache {
    private final ConcurrentMap<IdentityKey, Failure> failures = new ConcurrentHashMap<>();

    @Nullable
    public Failure get(InternalFailure original) {
        return failures.get(new IdentityKey(original));
    }

    public Failure intern(InternalFailure original, Failure converted) {
        Failure previous = failures.putIfAbsent(new IdentityKey(original), converted);
        return previous != null ? previous : converted;
    }

    private static final class IdentityKey {
        private final InternalFailure failure;
        private final int hashCode;

        private IdentityKey(InternalFailure failure) {
            this.failure = failure;
            this.hashCode = System.identityHashCode(failure);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof IdentityKey && failure == ((IdentityKey) other).failure;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
