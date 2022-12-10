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

import com.google.common.collect.ImmutableList;
import org.gradle.internal.execution.schema.AbstractWorkInstanceSchema;
import org.gradle.internal.execution.schema.FileInputPropertySchema;
import org.gradle.internal.execution.schema.ScalarInputPropertySchema;
import org.gradle.internal.properties.schema.NestedPropertySchema;

import javax.annotation.Nullable;
import java.util.stream.Stream;

public class DefaultTaskInstanceSchema extends AbstractWorkInstanceSchema implements TaskInstanceSchema {
    private final ImmutableList<FileOutputPropertySchema> outputs;
    private final ImmutableList<LocalStatePropertySchema> localStates;
    private final ImmutableList<DestroysPropertySchema> destroys;
    private final ImmutableList<ServiceReferencePropertySchema> serviceReferences;

    public DefaultTaskInstanceSchema(
        ImmutableList<NestedPropertySchema> nestedProperties,
        ImmutableList<ScalarInputPropertySchema> inputs,
        ImmutableList<FileInputPropertySchema> fileInputs,
        ImmutableList<FileOutputPropertySchema> outputs,
        ImmutableList<LocalStatePropertySchema> localStates,
        ImmutableList<DestroysPropertySchema> destroys,
        ImmutableList<ServiceReferencePropertySchema> serviceReferences
    ) {
        super(nestedProperties, inputs, fileInputs);
        this.outputs = outputs;
        this.localStates = localStates;
        this.destroys = destroys;
        this.serviceReferences = serviceReferences;
    }

    @Override
    public Stream<FileOutputPropertySchema> getOutputs() {
        return outputs.stream();
    }

    @Override
    public Stream<LocalStatePropertySchema> getLocalStates() {
        return localStates.stream();
    }

    @Override
    public Stream<DestroysPropertySchema> getDestroys() {
        return destroys.stream();
    }

    @Override
    public Stream<ServiceReferencePropertySchema> getServiceReferences() {
        return serviceReferences.stream();
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        DefaultTaskInstanceSchema that = (DefaultTaskInstanceSchema) o;

        if (!outputs.equals(that.outputs)) {
            return false;
        }
        if (!localStates.equals(that.localStates)) {
            return false;
        }
        if (!destroys.equals(that.destroys)) {
            return false;
        }
        return serviceReferences.equals(that.serviceReferences);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + outputs.hashCode();
        result = 31 * result + localStates.hashCode();
        result = 31 * result + destroys.hashCode();
        result = 31 * result + serviceReferences.hashCode();
        return result;
    }
}
