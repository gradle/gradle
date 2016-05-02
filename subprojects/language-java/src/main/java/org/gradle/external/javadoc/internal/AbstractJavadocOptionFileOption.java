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

import org.gradle.external.javadoc.JavadocOptionFileOption;

/**
 * A base class for {@link org.gradle.external.javadoc.JavadocOptionFileOption} implementations.
 *
 * @param <T> The type which this option represents.
 */
public abstract class AbstractJavadocOptionFileOption<T> implements JavadocOptionFileOption<T> {
    protected final String option;
    protected T value;

    protected AbstractJavadocOptionFileOption(String option) {
        this(option, null);
    }

    protected AbstractJavadocOptionFileOption(String option, T value) {
        if (option == null) {
            throw new IllegalArgumentException("option == null!");
        }

        this.option = option;
        this.value = value;
    }

    @Override
    public final String getOption() {
        return option;
    }

    @Override
    public T getValue() {
        return value;
    }

    @Override
    public void setValue(T value) {
        this.value = value;
    }
}
