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

import java.io.IOException;
import java.util.List;

/**
 * A base class for {@link org.gradle.external.javadoc.JavadocOptionFileOption} implementations whose value is a {@code List}.
 *
 * @param <T> The type which this option represents.
 */
public abstract class AbstractListJavadocOptionFileOption<T extends List<?>> extends AbstractJavadocOptionFileOption<T> {
    protected String joinBy;

    protected AbstractListJavadocOptionFileOption(String option, String joinBy) {
        super(option);
        this.joinBy = joinBy;
    }

    protected AbstractListJavadocOptionFileOption(String option, T value, String joinBy) {
        super(option, value);
        this.joinBy = joinBy;
    }

    @Override
    public T getValue() {
        return value;
    }

    @Override
    public void setValue(T value) {
        if (value == null) {
            this.value.clear();
        } else {
            this.value = value;
        }
    }

    @Override
    public void write(JavadocOptionFileWriterContext writerContext) throws IOException {
        if (value != null && !value.isEmpty()) {
            writeCollectionValue(writerContext);
        }
    }

    protected abstract void writeCollectionValue(JavadocOptionFileWriterContext writerContext) throws IOException;
}
