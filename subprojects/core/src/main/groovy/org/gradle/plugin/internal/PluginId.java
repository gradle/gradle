/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugin.internal;

import com.google.common.base.CharMatcher;
import org.gradle.api.Nullable;

import static com.google.common.base.CharMatcher.anyOf;
import static com.google.common.base.CharMatcher.inRange;

public class PluginId {

    public static final String ID_SEPARATOR_ON_START_OR_END = "cannot begin or end with '" + PluginId.SEPARATOR + "'";
    public static final String DOUBLE_SEPARATOR = "cannot contain '" + PluginId.SEPARATOR + PluginId.SEPARATOR + "'";

    public static final String PLUGIN_ID_VALID_CHARS_DESCRIPTION = "ASCII alphanumeric characters, '.', '_' and '-'";
    public static final CharMatcher INVALID_PLUGIN_ID_CHAR_MATCHER = inRange('a', 'z')
            .or(inRange('A', 'Z'))
            .or(inRange('0', '9'))
            .or(anyOf(".-_"))
            .negate();
    public static final String SEPARATOR = ".";

    private final String value;

    // Only use when id is guaranteed to be valid, prefer of()
    public PluginId(String value) {
        this.value = value;
    }

    public static PluginId of(String value) throws InvalidPluginIdException {
        validate(value);
        return new PluginId(value);
    }

    public static void validate(String value) throws InvalidPluginIdException {
        if (value.startsWith(SEPARATOR) || value.endsWith(SEPARATOR)) {
            throw new InvalidPluginIdException(value, ID_SEPARATOR_ON_START_OR_END);
        } else if (value.contains(PluginId.SEPARATOR + PluginId.SEPARATOR)) {
            throw new InvalidPluginIdException(value, DOUBLE_SEPARATOR);
        } else {
            int invalidCharIndex = PluginId.INVALID_PLUGIN_ID_CHAR_MATCHER.indexIn(value);
            if (invalidCharIndex >= 0) {
                char invalidChar = value.charAt(invalidCharIndex);
                throw new InvalidPluginIdException(value, invalidPluginIdCharMessage(invalidChar));
            }
        }
    }

    public static String invalidPluginIdCharMessage(char invalidChar) {
        return "Plugin id contains invalid char '" + invalidChar + "' (only " + PluginId.PLUGIN_ID_VALID_CHARS_DESCRIPTION + " characters are valid)";
    }

    public boolean isQualified() {
        return value.contains(PluginId.SEPARATOR);
    }

    public PluginId maybeQualify(String qualification) {
        return isQualified() ? this : new PluginId(qualification + PluginId.SEPARATOR + value);
    }

    @Nullable
    public String getNamespace() {
        return isQualified() ? value.substring(0, value.lastIndexOf(SEPARATOR)) : null;
    }

    public boolean inNamespace(String namespace) {
        return isQualified() && getNamespace().equals(namespace);
    }

    public String getName() {
        return isQualified() ? value.substring(value.lastIndexOf(PluginId.SEPARATOR) + 1) : value;
    }

    public PluginId getUnqualified() {
        return isQualified() ? new PluginId(getName()) : this;
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PluginId pluginId = (PluginId) o;

        return value.equals(pluginId.value);

    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
