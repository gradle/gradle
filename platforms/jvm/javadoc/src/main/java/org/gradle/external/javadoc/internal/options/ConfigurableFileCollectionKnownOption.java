/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.external.javadoc.internal.options;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.external.javadoc.CoreJavadocOptions;
import org.gradle.external.javadoc.internal.JavadocOptionFile;

import java.util.function.Function;

public class ConfigurableFileCollectionKnownOption<T extends CoreJavadocOptions> implements KnownOption<T> {

    private final String option;
    private final Function<T, ConfigurableFileCollection> propertyGetter;

    public ConfigurableFileCollectionKnownOption(String option, Function<T, ConfigurableFileCollection> propertyGetter) {
        this.option = option;
        this.propertyGetter = propertyGetter;
    }

    @Override
    public String getOption() {
        return option;
    }

    @Override
    public void addToOptionFile(T options, JavadocOptionFile optionFile) {
        optionFile.addConfigurableFileCollectionOption(option, propertyGetter.apply(options));
    }

    @Override
    public void copyValueFromOptionFile(T options, JavadocOptionFile optionFile) {
        ConfigurableFileCollection configurableFileCollection = propertyGetter.apply(options);
        Object value = optionFile.getOption(option).getValue();
        configurableFileCollection.setFrom(value);
    }
}
