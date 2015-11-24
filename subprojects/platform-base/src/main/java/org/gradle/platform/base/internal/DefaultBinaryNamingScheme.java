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

import org.gradle.api.Named;
import org.gradle.api.Nullable;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DefaultBinaryNamingScheme implements BinaryNamingScheme {
    final String parentName;
    @Nullable
    final String binaryType;
    final String dimensionPrefix;
    @Nullable
    private final String role;
    private final boolean main;
    final List<String> dimensions;
    @Nullable
    private final String outputType;

    DefaultBinaryNamingScheme(String parentName, @Nullable String binaryType, @Nullable String role, boolean main, List<String> dimensions, @Nullable String outputType) {
        this.parentName = parentName;
        this.binaryType = binaryType;
        this.role = role;
        this.main = main;
        this.dimensions = dimensions;
        this.outputType = outputType;
        this.dimensionPrefix = createPrefix(dimensions);
    }

    public static BinaryNamingScheme component(String componentName) {
        return new DefaultBinaryNamingScheme(componentName, null, null, false, Collections.<String>emptyList(), null);
    }

    @Override
    public BinaryNamingScheme withVariantDimension(String dimension) {
        List<String> newDimensions = new ArrayList<String>(dimensions.size() + 1);
        newDimensions.addAll(dimensions);
        newDimensions.add(dimension);
        return new DefaultBinaryNamingScheme(parentName, binaryType, role, main, newDimensions, null);
    }

    @Override
    public <T extends Named> BinaryNamingScheme withVariantDimension(T value, Collection<? extends T> allValuesForAxis) {
        if (allValuesForAxis.size() == 1) {
            return this;
        }
        return withVariantDimension(value.getName());
    }

    @Override
    public BinaryNamingScheme withRole(String role, boolean isMain) {
        return new DefaultBinaryNamingScheme(parentName, binaryType, role, isMain, dimensions, outputType);
    }

    @Override
    public BinaryNamingScheme withBinaryType(String type) {
        return new DefaultBinaryNamingScheme(parentName, type, role, main, dimensions, null);
    }

    @Override
    public BinaryNamingScheme withComponentName(String componentName) {
        return new DefaultBinaryNamingScheme(componentName, binaryType, role, main, dimensions, null);
    }

    @Override
    public BinaryNamingScheme withOutputType(String type) {
        return new DefaultBinaryNamingScheme(parentName, binaryType, role, main, dimensions, type);
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
        return makeName(dimensionPrefix, binaryType);
    }

    public String getOutputDirectoryBase() {
        StringBuilder builder = new StringBuilder();
        if (outputType != null) {
            builder.append(outputType);
            builder.append('/');
        }
        builder.append(makeName(parentName));
        if (!main) {
            if (role != null) {
                builder.append('/');
                builder.append(makeName(role));
            } else if (binaryType != null) {
                builder.append('/');
                builder.append(makeName(binaryType));
            }
        }
        for (String dimension : dimensions) {
            builder.append('/');
            builder.append(makeName(dimension));
        }
        return builder.toString();
    }

    @Override
    public File getOutputDirectory(File baseDir) {
        return new File(baseDir, getOutputDirectoryBase());
    }

    public String getDescription() {
        StringBuilder builder = new StringBuilder();
        builder.append(GUtil.toWords(binaryType));
        builder.append(" '");
        builder.append(parentName);
        for (String dimension : dimensions) {
            builder.append(':');
            builder.append(dimension);
        }
        builder.append(':');
        appendUncapitalized(builder, binaryType);
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
        return makeName(verb, dimensionPrefix, parentName, binaryType, target);
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
