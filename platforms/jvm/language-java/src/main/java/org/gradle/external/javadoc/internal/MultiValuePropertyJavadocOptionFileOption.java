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

package org.gradle.external.javadoc.internal;

import org.gradle.api.NonNullApi;
import org.gradle.api.internal.lambdas.SerializableLambdas.SerializableBiFunction;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;

import java.io.IOException;
import java.util.Collection;
import java.util.function.BiFunction;

@NonNullApi
public class MultiValuePropertyJavadocOptionFileOption extends AbstractJavadocOptionFileOption<Provider<? extends Collection<?>>> {

    /**
     * Has to be a serializable function because it is stored in the configuration cache.
     */
    private final SerializableBiFunction<String, Object, AbstractJavadocOptionFileOption<?>> valueWriter;

    public MultiValuePropertyJavadocOptionFileOption(String option, Provider<? extends Collection<?>> value, BiFunction<String, Object, AbstractJavadocOptionFileOption<?>> valueWriter) {
        super(option, value);
        this.valueWriter = valueWriter::apply;
    }

    @Override
    public JavadocOptionFileOptionInternal<Provider<? extends Collection<?>>> duplicate() {
        return new MultiValuePropertyJavadocOptionFileOption(option, value, valueWriter);
    }

    @Override
    public void write(JavadocOptionFileWriterContext writerContext) throws IOException {
        if (value.isPresent()) {
            valueWriter.apply(option, Cast.uncheckedCast(value.get())).write(writerContext);
        }
    }
}
