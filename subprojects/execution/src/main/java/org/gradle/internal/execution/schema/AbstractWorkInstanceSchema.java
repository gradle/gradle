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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import org.gradle.internal.properties.schema.AbstractInstanceSchema;
import org.gradle.internal.properties.schema.NestedPropertySchema;

public class AbstractWorkInstanceSchema extends AbstractInstanceSchema implements WorkInstanceSchema {
    private final ImmutableList<ScalarInputPropertySchema> inputs;
    private final ImmutableList<FileInputPropertySchema> fileInputs;

    public AbstractWorkInstanceSchema(
        ImmutableList<NestedPropertySchema> nestedProperties,
        ImmutableList<ScalarInputPropertySchema> inputs,
        ImmutableList<FileInputPropertySchema> fileInputs
    ) {
        super(nestedProperties);
        this.inputs = inputs;
        this.fileInputs = fileInputs;
    }

    @Override
    public ImmutableCollection<ScalarInputPropertySchema> getScalarInputs() {
        return inputs;
    }

    @Override
    public ImmutableCollection<FileInputPropertySchema> getFileInputs() {
        return fileInputs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        AbstractWorkInstanceSchema that = (AbstractWorkInstanceSchema) o;

        if (!inputs.equals(that.inputs)) {
            return false;
        }
        return fileInputs.equals(that.fileInputs);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + inputs.hashCode();
        result = 31 * result + fileInputs.hashCode();
        return result;
    }
}
