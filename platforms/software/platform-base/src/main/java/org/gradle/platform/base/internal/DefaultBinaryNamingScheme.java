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

import com.google.common.collect.Lists;
import org.gradle.api.Named;
import org.gradle.util.internal.GUtil;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DefaultBinaryNamingScheme implements BinaryNamingScheme {
    @Nullable
    final String parentName;
    @Nullable
    private final String binaryName;
    @Nullable
    final String binaryType;
    final String dimensionPrefix;
    @Nullable
    private final String role;
    private final boolean main;
    final List<String> dimensions;

    DefaultBinaryNamingScheme(@Nullable String parentName, @Nullable String binaryName, @Nullable String binaryType, @Nullable String role, boolean main, List<String> dimensions) {
        this.parentName = parentName;
        this.binaryName = binaryName;
        this.binaryType = binaryType;
        this.role = role;
        this.main = main;
        this.dimensions = dimensions;
        this.dimensionPrefix = createPrefix(dimensions);
    }

    public static BinaryNamingScheme component(@Nullable String componentName) {
        return new DefaultBinaryNamingScheme(componentName, null, null, null, false, Collections.<String>emptyList());
    }

    @Override
    public BinaryNamingScheme withVariantDimension(String dimension) {
        List<String> newDimensions = new ArrayList<String>(dimensions.size() + 1);
        newDimensions.addAll(dimensions);
        newDimensions.add(dimension);
        return new DefaultBinaryNamingScheme(parentName, binaryName, binaryType, role, main, newDimensions);
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
        return new DefaultBinaryNamingScheme(parentName, binaryName, binaryType, role, isMain, dimensions);
    }

    @Override
    public BinaryNamingScheme withBinaryType(@Nullable String type) {
        return new DefaultBinaryNamingScheme(parentName, binaryName, type, role, main, dimensions);
    }

    @Override
    public BinaryNamingScheme withComponentName(@Nullable String componentName) {
        return new DefaultBinaryNamingScheme(componentName, binaryName, binaryType, role, main, dimensions);
    }

    @Override
    public BinaryNamingScheme withBinaryName(@Nullable String name) {
        return new DefaultBinaryNamingScheme(parentName, name, binaryType, role, main, dimensions);
    }

    private String createPrefix(List<String> dimensions) {
        if (dimensions.isEmpty()) {
            return "";
        }
        return makeName(dimensions.toArray(new String[0]));
    }

    public String getBaseName() {
        return parentName;
    }

    @Override
    public String getBinaryName() {
        return binaryName != null ? binaryName : makeName(dimensionPrefix, binaryType);
    }

    private String getOutputDirectoryBase(@Nullable String outputType) {
        List<String> elements = Lists.newArrayList();
        elements.add(outputType);
        elements.add(parentName);
        if (binaryName != null) {
            elements.add(binaryName);
        } else {
            if (!main) {
                if (role != null) {
                    elements.add(role);
                } else {
                    elements.add(binaryType);
                }
            }
            elements.addAll(dimensions);
        }
        return makePath(elements);
    }

    @Override
    public File getOutputDirectory(File baseDir) {
        return getOutputDirectory(baseDir, null);
    }

    @Override
    public File getOutputDirectory(File baseDir, @Nullable String outputType) {
        return new File(baseDir, getOutputDirectoryBase(outputType));
    }

    @Override
    public String getDescription() {
        StringBuilder builder = new StringBuilder();
        builder.append(GUtil.toWords(binaryType));
        builder.append(" '");
        List<String> elements = Lists.newArrayList();
        elements.add(parentName);
        if (binaryName != null) {
            elements.add(binaryName);
        } else {
            elements.addAll(dimensions);
            elements.add(binaryType);
        }
        builder.append(makeSeparated(elements));
        builder.append("'");
        return builder.toString();
    }

    @Override
    public List<String> getVariantDimensions() {
        return dimensions;
    }

    @Override
    public String getTaskName(@Nullable String verb) {
        return getTaskName(verb, null);
    }

    @Override
    public String getTaskName(@Nullable String verb, @Nullable String target) {
        if (binaryName != null) {
            return makeName(verb, parentName, binaryName, target);
        }
        return makeName(verb, parentName, dimensionPrefix, binaryType, target);
    }

    private String makeName(String... words) {
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

    private String makePath(Iterable<String> words) {
        int expectedLength = 0;
        for (String word : words) {
            if (word != null) {
                expectedLength += word.length() + 1;
            }
        }
        StringBuilder builder = new StringBuilder(expectedLength);
        for (String word : words) {
            if (word == null || word.length() == 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('/');
            }
            appendUncapitalized(builder, word);
        }
        return builder.toString();
    }

    private String makeSeparated(Iterable<String> words) {
        int expectedLength = 0;
        for (String word : words) {
            if (word != null) {
                expectedLength += word.length() + 1;
            }
        }
        StringBuilder builder = new StringBuilder(expectedLength);
        for (String word : words) {
            if (word == null || word.length() == 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(':');
            }
            appendUncapitalized(builder, word);
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
