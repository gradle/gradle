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

package org.gradle.internal.execution.schema;

import com.google.common.collect.ImmutableSortedSet;
import org.gradle.internal.schema.AbstractInstanceSchema;
import org.gradle.internal.schema.NestedPropertySchema;

import java.util.stream.Stream;

public class AbstractWorkInstanceSchema extends AbstractInstanceSchema implements WorkInstanceSchema {
    private final ImmutableSortedSet<ScalarInputPropertySchema> inputs;
    private final ImmutableSortedSet<FileInputPropertySchema> fileInputs;

    public AbstractWorkInstanceSchema(
        ImmutableSortedSet<NestedPropertySchema> nestedProperties,
        ImmutableSortedSet<ScalarInputPropertySchema> inputs,
        ImmutableSortedSet<FileInputPropertySchema> fileInputs
    ) {
        super(nestedProperties);
        this.inputs = inputs;
        this.fileInputs = fileInputs;
    }

    @Override
    public Stream<ScalarInputPropertySchema> getScalarInputs() {
        return inputs.stream();
    }

    @Override
    public Stream<FileInputPropertySchema> getFileInputs() {
        return fileInputs.stream();
    }
}
