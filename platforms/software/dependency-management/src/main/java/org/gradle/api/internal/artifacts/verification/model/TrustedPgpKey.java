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
package org.gradle.api.internal.artifacts.verification.model;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Internal representation of an artifact-specific trusted PGP key (the {@code <pgp>} element),
 * aimed at <i>verification</i>. In addition to the key id, a trusted key may also provide an
 * {@code origin} (documentation of where the key was found or verified, for example a URL) and
 * a {@code reason} explaining why the key is trusted.
 *
 * <p>Two trusted keys are considered equal when they share the same key id, so that the
 * {@code origin} and {@code reason} attributes are purely informational and don't affect
 * de-duplication.
 */
public class TrustedPgpKey implements Comparable<TrustedPgpKey> {
    private final String keyId;
    private final @Nullable String origin;
    private final @Nullable String reason;

    public TrustedPgpKey(String keyId, @Nullable String origin, @Nullable String reason) {
        this.keyId = keyId.toUpperCase(Locale.ROOT);
        this.origin = origin;
        this.reason = reason;
    }

    public String getKeyId() {
        return keyId;
    }

    @Nullable
    public String getOrigin() {
        return origin;
    }

    @Nullable
    public String getReason() {
        return reason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        TrustedPgpKey that = (TrustedPgpKey) o;

        return keyId.equals(that.keyId);
    }

    @Override
    public int hashCode() {
        return keyId.hashCode();
    }

    @Override
    public int compareTo(TrustedPgpKey o) {
        return keyId.compareTo(o.keyId);
    }
}
