/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.publish.internal;

import org.gradle.api.InvalidUserDataException;

public abstract class PublicationFieldValidator<T extends PublicationFieldValidator> {
    private final Class<T> type;
    protected final String publicationName;
    protected final String name;
    protected final String value;

    public PublicationFieldValidator(Class<T> type, String publicationName, String name, String value) {
        this.type = type;
        this.publicationName = publicationName;
        this.name = name;
        this.value = value;
    }

    public T notNull() {
        if (value == null) {
            String message = String.format("%s cannot be null.", name);
            throw failure(message);
        }
        return type.cast(this);
    }

    public T notEmpty() {
        notNull();
        if (value.length() == 0) {
            throw failure(String.format("%s cannot be empty.", name));
        }
        return type.cast(this);
    }

    public T validInFileName() {
        if (value == null || value.length() == 0) {
            return type.cast(this);
        }
        // Iterate over unicode characters
        int offset = 0;
        while (offset < value.length()) {
            final int unicodeChar = value.codePointAt(offset);
            if (Character.isISOControl(unicodeChar)) {
                throw failure(String.format("%s cannot contain ISO control character '\\u%04x'.", name, unicodeChar));
            }
            if ('\\' == unicodeChar || '/' == unicodeChar) {
                throw failure(String.format("%s cannot contain '%c'.", name, (char) unicodeChar));
            }
            offset += Character.charCount(unicodeChar);
        }
        return type.cast(this);
    }

    public T optionalNotEmpty() {
        if (value != null && value.length() == 0) {
            throw failure(String.format("%s cannot be an empty string. Use null instead.", name));
        }
        return type.cast(this);
    }

    protected abstract InvalidUserDataException failure(String message);
}
