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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DefaultBinaryNamingScheme implements BinaryNamingScheme {
    private final String baseName;
    private final String dimensionPrefix;
    private final List<String> dimensions;
    private String collapsedName;

    public DefaultBinaryNamingScheme(String baseName) {
        this(baseName, baseName, new ArrayList<String>());
    }

    public DefaultBinaryNamingScheme(String baseName, List<String> dimensions) {
        this(baseName, baseName, dimensions);
    }

    private DefaultBinaryNamingScheme(String baseName, String collapsedName, List<String> dimensions) {
        this.baseName = baseName;
        this.collapsedName = collapsedName;
        this.dimensions = dimensions;
        this.dimensionPrefix = createPrefix(dimensions);
    }

    private String createPrefix(List<String> dimensions) {
        if (dimensions.isEmpty()) {
            return "";
        }
        return makeName(dimensions.toArray(new String[dimensions.size()]));
    }

    protected void collapseMain() {
        collapsedName = baseName.equals("main") ? "" : baseName;
    }

    public String getLifecycleTaskName() {
        return getTaskName(null, null);
    }

    public String getOutputDirectoryBase() {
        StringBuilder builder = new StringBuilder(baseName);
        for (String dimension : dimensions) {
            builder.append('/');
            builder.append(StringUtils.uncapitalize(dimension));
        }
        return builder.toString();
    }

    public String getTaskName(@Nullable String verb) {
        return getTaskName(verb, null);
    }

    public String getTaskName(@Nullable String verb, @Nullable String target) {
        if (verb == null && target == null) {
            return makeName(dimensionPrefix, baseName);
        }
        if (verb == null) {
            return makeName(dimensionPrefix, collapsedName, target);
        }
        if (target == null) {
            return makeName(verb, dimensionPrefix, baseName);
        }
        return makeName(verb, dimensionPrefix, collapsedName, target);
    }

    public String makeName(String... words) {
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.length() == 0) {
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
