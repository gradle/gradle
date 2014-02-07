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

package org.gradle.language.base.internal;

import org.gradle.api.Nullable;
import org.gradle.util.GUtil;

import java.util.List;

public class DefaultBinaryNamingScheme implements BinaryNamingScheme {
    final String parentName;
    final String typeString;
    final String dimensionPrefix;
    final List<String> dimensions;

    public DefaultBinaryNamingScheme(String parentName, String typeString, List<String> dimensions) {
        this.parentName = parentName;
        this.typeString = typeString;
        this.dimensions = dimensions;
        this.dimensionPrefix = createPrefix(dimensions);
    }

    private String createPrefix(List<String> dimensions) {
        if (dimensions.isEmpty()) {
            return "";
        }
        return makeName(dimensions.toArray(new String[dimensions.size()]));
    }

    public String getLifecycleTaskName() {
        return getTaskName(null, null);
    }

    public String getOutputDirectoryBase() {
        StringBuilder builder = new StringBuilder(makeName(parentName, typeString));
        if (dimensionPrefix.length() > 0) {
            builder.append('/');
            builder.append(dimensionPrefix);
        }
        return builder.toString();
    }

    public String getDescription() {
        StringBuilder builder = new StringBuilder();
        builder.append(GUtil.toWords(typeString));
        builder.append(" '");
        builder.append(parentName);
        for (String dimension : dimensions) {
            builder.append(':');
            builder.append(dimension);
        }
        builder.append(':');
        appendUncapitalized(builder, typeString);
        builder.append("'");
        return builder.toString();
    }

    public List<String> getVariantDimensions() {
        return dimensions;
    }

    public String getTaskName(@Nullable String verb) {
        return getTaskName(verb, null);
    }

    public String getTaskName(@Nullable String verb, @Nullable String target) {
        return makeName(verb, dimensionPrefix, parentName, typeString, target);
    }

    public String makeName(String... words) {
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word == null || word.length() == 0) {
                continue;
            }
            if (builder.length() == 0) {
                appendUncapitalized(builder, word);
            } else {
                appendCapitalized(builder, word);
            }
        }
        return builder.toString();
    }

    private void appendCapitalized(StringBuilder builder, String word) {
        if (word.length() == 0) {
            return;
        }
        builder.append(Character.toTitleCase(word.charAt(0))).append(word.substring(1));
    }

    private void appendUncapitalized(StringBuilder builder, String word) {
        if (word.length() == 0) {
            return;
        }
        builder.append(Character.toLowerCase(word.charAt(0))).append(word.substring(1));
    }
}
