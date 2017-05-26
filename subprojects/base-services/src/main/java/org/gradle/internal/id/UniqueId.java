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

package org.gradle.internal.id;

import com.google.common.io.BaseEncoding;
import org.gradle.internal.Factory;

import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A more compact / aesthetically pleasing representation of effectively a UUID.
 * <p>
 * Values of this type are serialized and deserialized (as strings) across Gradle versions.
 * The string representation cannot change.
 *
 * @since 4.0
 */
public final class UniqueId {

    private static final BaseEncoding ENCODING = BaseEncoding.base32().lowerCase().omitPadding();
    private static final Pattern PATTERN = Pattern.compile("[a-z2-7]{26}");

    private static final Factory<UniqueId> FACTORY = new Factory<UniqueId>() {
        @Override
        public UniqueId create() {
            return generate();
        }
    };

    private final String value;

    public static UniqueId from(UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();

        byte[] bytes = new byte[16];
        ByteBuffer.wrap(bytes).putLong(msb).putLong(lsb);
        String value = ENCODING.encode(bytes);

        return new UniqueId(value);
    }

    public static UniqueId from(String string) {
        if (PATTERN.matcher(string).matches()) {
            return new UniqueId(string);
        } else {
            throw new IllegalArgumentException("Invalid unique ID: " + string);
        }
    }

    public static UniqueId generate() {
        return from(UUID.randomUUID());
    }

    public static Factory<UniqueId> factory() {
        return FACTORY;
    }

    private UniqueId(String value) {
        this.value = value;
    }

    public String asString() {
        return value;
    }

    @Override
    public String toString() {
        return asString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        UniqueId uniqueId = (UniqueId) o;

        return value.equals(uniqueId.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

}
