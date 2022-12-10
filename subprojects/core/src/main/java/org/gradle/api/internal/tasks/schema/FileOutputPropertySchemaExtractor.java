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

package org.gradle.api.internal.tasks.schema;

import org.gradle.api.tasks.OutputDirectories;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.properties.annotations.PropertyMetadata;
import org.gradle.internal.schema.AbstractPropertySchemaExtractor;

import java.lang.annotation.Annotation;
import java.util.function.Supplier;

public class FileOutputPropertySchemaExtractor extends AbstractPropertySchemaExtractor<TaskInstanceSchema.Builder> {
    public static final FileOutputPropertySchemaExtractor OUTPUT_FILE = new FileOutputPropertySchemaExtractor(OutputFile.class, TreeType.FILE);
    public static final FileOutputPropertySchemaExtractor OUTPUT_FILES = new FileOutputPropertySchemaExtractor(OutputFiles.class, TreeType.FILE);
    public static final FileOutputPropertySchemaExtractor OUTPUT_DIRECTORY = new FileOutputPropertySchemaExtractor(OutputDirectory.class, TreeType.DIRECTORY);
    public static final FileOutputPropertySchemaExtractor OUTPUT_DIRECTORIES = new FileOutputPropertySchemaExtractor(OutputDirectories.class, TreeType.DIRECTORY);

    private final TreeType outputType;

    private FileOutputPropertySchemaExtractor(Class<? extends Annotation> annotationType, TreeType outputType) {
        super(annotationType);
        this.outputType = outputType;
    }

    @Override
    public void extractProperty(String qualifiedName, PropertyMetadata metadata, Supplier<Object> valueResolver, TaskInstanceSchema.Builder builder) {
        builder.add(new DefaultFileOutputPropertySchema(qualifiedName, metadata, outputType, valueResolver));
    }
}
