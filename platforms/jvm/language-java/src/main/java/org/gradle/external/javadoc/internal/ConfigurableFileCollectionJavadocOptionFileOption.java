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

import org.gradle.api.file.ConfigurableFileCollection;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.util.ArrayList;

@NullMarked
public class ConfigurableFileCollectionJavadocOptionFileOption extends AbstractJavadocOptionFileOption<ConfigurableFileCollection> {
    public ConfigurableFileCollectionJavadocOptionFileOption(String option, ConfigurableFileCollection value) {
        super(option, value);
    }

    @Override
    public void write(JavadocOptionFileWriterContext writerContext) throws IOException {
        new PathJavadocOptionFileOption(option, new ArrayList<>(value.getFiles()), System.getProperty("path.separator")).write(writerContext);
    }

    @Override
    public ConfigurableFileCollectionJavadocOptionFileOption duplicate() {
        return new ConfigurableFileCollectionJavadocOptionFileOption(option, value);
    }
}
