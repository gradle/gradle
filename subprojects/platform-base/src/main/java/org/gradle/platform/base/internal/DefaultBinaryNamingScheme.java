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

package org.gradle.platform.base.internal;

import org.gradle.api.Nullable;
import org.gradle.util.GUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DefaultBinaryNamingScheme implements BinaryNamingScheme {
    final String parentName;
    final String roleName;
    final String dimensionPrefix;
    final List<String> dimensions;

    public DefaultBinaryNamingScheme(String parentName, String roleName, List<String> dimensions) {
        this.parentName = parentName;
        this.roleName = roleName;
        this.dimensions = dimensions;
        this.dimensionPrefix = createPrefix(dimensions);
    }

    public static BinaryNamingScheme component(String componentName) {
        return new DefaultBinaryNamingScheme(componentName, "", Collections.<String>emptyList());
    }

    @Override
    public BinaryNamingScheme withVariantDimension(String dimension) {
        List<String> newDimensions = new ArrayList<String>(dimensions.size() + 1);
        newDimensions.addAll(dimensions);
        newDimensions.add(dimension);
        return new DefaultBinaryNamingScheme(parentName, roleName, newDimensions);
    }

    @Override
    public BinaryNamingScheme withRole(String role) {
        return new DefaultBinaryNamingScheme(parentName, role, dimensions);
    }

    @Override
    public BinaryNamingScheme withComponentName(String componentName) {
        return new DefaultBinaryNamingScheme(componentName, roleName, dimensions);
    }

    private String createPrefix(List<String> dimensions) {
        if (dimensions.isEmpty()) {
            return "";
        }
        return makeName(dimensions.toArray(new String[dimensions.size()]));
    }

    public String getBaseName() {
        return parentName;
    }

    public String getBinaryName() {
        return makeName(dimensionPrefix, roleName);
    }

    public String getOutputDirectoryBase() {
        StringBuilder builder = new StringBuilder(makeName(parentName, roleName));
        if (dimensionPrefix.length() > 0) {
            builder.append('/');
            builder.append(dimensionPrefix);
        }
        return builder.toString();
    }

    public String getDescription() {
        StringBuilder builder = new StringBuilder();
        builder.append(GUtil.toWords(roleName));
        builder.append(" '");
        builder.append(parentName);
        for (String dimension : dimensions) {
            builder.append(':');
            builder.append(dimension);
        }
        builder.append(':');
        appendUncapitalized(builder, roleName);
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
        return makeName(verb, dimensionPrefix, parentName, roleName, target);
    }

    public String makeName(String... words) {
        int expectedLength = 0;
        for (String word : words) {
            if (word != null) {
                expectedLength += word.length();
            }
        }
        StringBuilder builder = new StringBuilder(expectedLength);
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
