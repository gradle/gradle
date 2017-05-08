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

import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A more compact / aesthetically please representation of effectively a UUID.
 */
public class UniqueId {

    private static final BaseEncoding ENCODING = BaseEncoding.base32().lowerCase().omitPadding();
    private static final Pattern PATTERN = Pattern.compile("[a-z][2-7]{26}");

    private final String value;

    private UniqueId(String value) {
        this.value = value;
    }

    public String asString() {
        return value;
    }

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
            throw new IllegalArgumentException("Value must be 26 characters long");
        }
    }

    public static UniqueId generate() {
        return from(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value;
    }
}
