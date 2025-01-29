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
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Property;

import java.io.IOException;

@NonNullApi
public class PropertyJavadocOptionFileOption extends AbstractJavadocOptionFileOption<Property<?>> {
    public PropertyJavadocOptionFileOption(String option, Property<?> value) {
        super(option, value);
    }

    @Override
    public void write(JavadocOptionFileWriterContext writerContext) throws IOException {
        if (value.isPresent()) {
            Object rawValue = value.get();
            if (rawValue instanceof Enum) {
                new EnumJavadocOptionFileOption<>(option, rawValue).write(writerContext);
            } else if (rawValue instanceof String) {
                new StringJavadocOptionFileOption(option, (String) rawValue).write(writerContext);
            } else if (rawValue instanceof Boolean) {
                new BooleanJavadocOptionFileOption(option, (Boolean) rawValue).write(writerContext);
            } else if (rawValue instanceof FileSystemLocation) {
                new FileJavadocOptionFileOption(option, ((FileSystemLocation) rawValue).getAsFile()).write(writerContext);
            } else {
                throw new UnsupportedOperationException("Unsupported property type: " + rawValue.getClass());
            }
        }
    }

    @Override
    public PropertyJavadocOptionFileOption duplicate() {
        return new PropertyJavadocOptionFileOption(option, value);
    }
}
