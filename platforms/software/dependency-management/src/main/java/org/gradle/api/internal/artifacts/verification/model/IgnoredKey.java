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
package org.gradle.api.internal.artifacts.verification.model;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

public class IgnoredKey implements Comparable<IgnoredKey> {
    private final String keyId;
    private final String reason;

    public IgnoredKey(String keyId, @Nullable String reason) {
        this.keyId = keyId.toUpperCase(Locale.ROOT);
        this.reason = reason;
    }

    public String getKeyId() {
        return keyId;
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

        IgnoredKey that = (IgnoredKey) o;

        return keyId.equals(that.keyId);
    }

    @Override
    public int hashCode() {
        return keyId.hashCode();
    }

    @Override
    public int compareTo(IgnoredKey o) {
        return keyId.compareTo(o.keyId);
    }
}
