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

import java.util.ArrayList;
import java.util.List;

public class DefaultBinaryNamingScheme implements BinaryNamingScheme {
    private final String baseName;
    private final String typeString;
    private final String dimensionPrefix;
    private final List<String> dimensions;

    public DefaultBinaryNamingScheme(String baseName) {
        this(baseName, "", new ArrayList<String>());
    }

    public DefaultBinaryNamingScheme withTypeString(String newTypeString) {
        return new DefaultBinaryNamingScheme(baseName, newTypeString, dimensions);
    }

    public DefaultBinaryNamingScheme withVariantDimension(String dimension) {
        List<String> newDimensions = new ArrayList<String>(dimensions);
        newDimensions.add(dimension);
        return new DefaultBinaryNamingScheme(baseName, typeString, newDimensions);
    }

    private DefaultBinaryNamingScheme(String baseName, String typeString, List<String> dimensions) {
        this.baseName = baseName;
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
        StringBuilder builder = new StringBuilder(makeName(baseName, typeString));
        if (dimensionPrefix.length() > 0) {
            builder.append('/');
            builder.append(dimensionPrefix);
        }
        return builder.toString();
    }

    public String getDescription() {
        return String.format("%s '%s'", GUtil.toWords(typeString), getLifecycleTaskName());
    }

    public String getTaskName(@Nullable String verb) {
        return getTaskName(verb, null);
    }

    public String getTaskName(@Nullable String verb, @Nullable String target) {
        return makeName(verb, dimensionPrefix, baseName, typeString, target);
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
        builder.append(Character.toTitleCase(word.charAt(0))).append(word.substring(1));
    }

    private void appendUncapitalized(StringBuilder builder, String word) {
        builder.append(Character.toLowerCase(word.charAt(0))).append(word.substring(1));
    }
}
