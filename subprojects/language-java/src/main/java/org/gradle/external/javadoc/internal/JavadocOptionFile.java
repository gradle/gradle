/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.external.javadoc.internal;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.external.javadoc.JavadocOptionFileOption;
import org.gradle.external.javadoc.OptionLessJavadocOptionFileOption;
import org.gradle.internal.Cast;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JavadocOptionFile {
    private final Map<String, JavadocOptionFileOptionInternal<?>> options;

    private final OptionLessJavadocOptionFileOptionInternal<List<String>> sourceNames;

    public JavadocOptionFile() {
        this(new LinkedHashMap<String, JavadocOptionFileOptionInternal<?>>(), new OptionLessStringsJavadocOptionFileOption(Lists.<String>newArrayList()));
    }

    private JavadocOptionFile(Map<String, JavadocOptionFileOptionInternal<?>> options, OptionLessJavadocOptionFileOptionInternal<List<String>> sourceNames) {
        this.options = options;
        this.sourceNames = sourceNames;
    }

    public JavadocOptionFile(JavadocOptionFile original) {
        this(duplicateOptions(original.options), original.sourceNames.duplicate());
    }

    private static Map<String, JavadocOptionFileOptionInternal<?>> duplicateOptions(Map<String, JavadocOptionFileOptionInternal<?>> original) {
        Map<String, JavadocOptionFileOptionInternal<?>> duplicateOptions = Maps.newLinkedHashMap();
        for (Map.Entry<String, JavadocOptionFileOptionInternal<?>> entry : original.entrySet()) {
            duplicateOptions.put(entry.getKey(), entry.getValue().duplicate());
        }
        return duplicateOptions;
    }

    public OptionLessJavadocOptionFileOption<List<String>> getSourceNames() {
        return sourceNames;
    }

    Map<String, JavadocOptionFileOptionInternal<?>> getOptions() {
        return Collections.unmodifiableMap(options);
    }

    public <T> JavadocOptionFileOption<T> addOption(JavadocOptionFileOptionInternal<T> option) {
        if (option == null) {
            throw new IllegalArgumentException("option == null!");
        }

        options.put(option.getOption(), option);

        return option;
    }

    public JavadocOptionFileOption<String> addStringOption(String option) {
        return addStringOption(option, null);
    }

    public JavadocOptionFileOption<String> addStringOption(String option, String value) {
        return addOption(new StringJavadocOptionFileOption(option, value));
    }

    public <T> JavadocOptionFileOption<T> addEnumOption(String option) {
        return addEnumOption(option, null);
    }

    public <T> JavadocOptionFileOption<T> addEnumOption(String option, T value) {
        return addOption(new EnumJavadocOptionFileOption<T>(option, value));
    }

    public JavadocOptionFileOption<List<File>> addPathOption(String option) {
        return addPathOption(option, System.getProperty("path.separator"));
    }

    public JavadocOptionFileOption<List<File>> addPathOption(String option, String joinBy) {
        return addOption(new PathJavadocOptionFileOption(option, Lists.<File>newArrayList(), joinBy));
    }

    public JavadocOptionFileOption<List<String>> addStringsOption(String option) {
        return addStringsOption(option, System.getProperty("path.separator"));
    }

    public JavadocOptionFileOption<List<String>> addStringsOption(String option, String joinBy) {
        return addOption(new StringsJavadocOptionFileOption(option, Lists.<String>newArrayList(), joinBy));
    }

    public JavadocOptionFileOption<List<String>> addMultilineStringsOption(String option) {
        return addOption(new MultilineStringsJavadocOptionFileOption(option, Lists.<String>newArrayList()));
    }

    public JavadocOptionFileOption<Boolean> addBooleanOption(String option) {
        return addBooleanOption(option, false);
    }

    public JavadocOptionFileOption<Boolean> addBooleanOption(String option, boolean value) {
        return addOption(new BooleanJavadocOptionFileOption(option, value));
    }

    public JavadocOptionFileOption<File> addFileOption(String option) {
        return addFileOption(option, null);
    }

    public JavadocOptionFileOption<File> addFileOption(String option, File value) {
        return addOption(new FileJavadocOptionFileOption(option, value));
    }

    public void write(File optionFile) throws IOException {
        if (optionFile == null) {
            throw new IllegalArgumentException("optionFile == null!");
        }

        final JavadocOptionFileWriter optionFileWriter = new JavadocOptionFileWriter(this);

        optionFileWriter.write(optionFile);
    }

    public <T> JavadocOptionFileOption<T> getOption(String option) {
        JavadocOptionFileOption<?> foundOption = options.get(option);
        if (foundOption == null) {
            throw new IllegalArgumentException("Cannot find option " + option);
        }
        return Cast.uncheckedCast(foundOption);
    }

    public JavadocOptionFileOption<List<List<String>>> addMultilineMultiValueOption(String option) {
        return addOption(new MultilineMultiValueJavadocOptionFileOption(option, Lists.<List<String>>newArrayList(), " "));
    }
}
