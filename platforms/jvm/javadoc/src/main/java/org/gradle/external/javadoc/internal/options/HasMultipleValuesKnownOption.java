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

import org.gradle.api.internal.lambdas.SerializableLambdas.SerializableBiFunction;
import org.gradle.api.provider.HasMultipleValues;
import org.gradle.api.provider.Provider;
import org.gradle.external.javadoc.CoreJavadocOptions;
import org.gradle.external.javadoc.internal.AbstractJavadocOptionFileOption;
import org.gradle.external.javadoc.internal.JavadocOptionFile;

import java.util.Collection;
import java.util.function.Function;

public class HasMultipleValuesKnownOption<T extends CoreJavadocOptions> implements KnownOption<T> {

    private final String option;
    private final Function<T, HasMultipleValues<?>> propertyGetter;
    private final SerializableBiFunction<String, Object, AbstractJavadocOptionFileOption<?>> valueWriter;

    public HasMultipleValuesKnownOption(String option, Function<T, HasMultipleValues<?>> propertyGetter, SerializableBiFunction<String, Object, AbstractJavadocOptionFileOption<?>> valueWriter) {
        this.option = option;
        this.propertyGetter = propertyGetter;
        this.valueWriter = valueWriter;
    }

    @Override
    public String getOption() {
        return option;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addToOptionFile(T options, JavadocOptionFile optionFile) {
        optionFile.addMultiValuePropertyOption(option, (Provider<? extends Collection<?>>) propertyGetter.apply(options), valueWriter);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void copyValueFromOptionFile(T options, JavadocOptionFile optionFile) {
        HasMultipleValues<?> property = propertyGetter.apply(options);
        Object value = optionFile.getOption(option).getValue();
        if (value instanceof Provider) {
            property.set((Provider) value);
        } else if (value == null || value instanceof Iterable) {
            property.set((Iterable) value);
        } else {
            throw new IllegalArgumentException("Cannot set the value of Javadoc option '" + option + "' using an instance of type " + value.getClass());
        }
    }
}
