/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.external.javadoc.JavadocOptionFileOption;

import java.io.File;
import java.util.function.Function;

public enum KnownJavadocOptions implements KnownJavadocOptionNames {
    DESTINATION_DIRECTORY(OPTION_D, File.class, optionFile -> optionFile.addFileOption(OPTION_D)),
    USE(OPTION_USE, Boolean.class, optionFile -> optionFile.addBooleanOption(OPTION_USE)),
    VERSION(OPTION_VERSION, Boolean.class, optionFile -> optionFile.addBooleanOption(OPTION_VERSION)),
    AUTHOR(OPTION_AUTHOR, Boolean.class, optionFile -> optionFile.addBooleanOption(OPTION_AUTHOR));

    private final String name;
    private final Class<?> type;
    private final Function<JavadocOptionFile, JavadocOptionFileOption<?>> creator;

    KnownJavadocOptions(String name, Class<?> type, Function<JavadocOptionFile, JavadocOptionFileOption<?>> creator) {
        this.name = name;
        this.type = type;
        this.creator = creator;
    }

    public String getName() {
        return name;
    }

    public Class<?> getType() {
        return type;
    }

    public JavadocOptionFileOption<?> create(JavadocOptionFile optionFile) {
        return creator.apply(optionFile);
    }
}
