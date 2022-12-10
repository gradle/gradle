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

import com.google.common.collect.ImmutableSortedSet;
import org.gradle.internal.execution.schema.FileInputPropertySchema;
import org.gradle.internal.execution.schema.ScalarInputPropertySchema;
import org.gradle.internal.execution.schema.WorkInstanceSchema;
import org.gradle.internal.schema.NestedPropertySchema;

import java.util.stream.Stream;

public interface TaskInstanceSchema extends WorkInstanceSchema {
    Stream<OutputPropertySchema> getOutputs();
    Stream<LocalStatePropertySchema> getLocalState();
    Stream<DestroysPropertySchema> getDestroys();

    class Builder extends WorkInstanceSchema.Builder<TaskInstanceSchema> {
        private final ImmutableSortedSet.Builder<OutputPropertySchema> outputs = ImmutableSortedSet.naturalOrder();
        private final ImmutableSortedSet.Builder<LocalStatePropertySchema> localState = ImmutableSortedSet.naturalOrder();
        private final ImmutableSortedSet.Builder<DestroysPropertySchema> destroys = ImmutableSortedSet.naturalOrder();

        @Override
        protected TaskInstanceSchema build(
            ImmutableSortedSet<NestedPropertySchema> nestedProperties,
            ImmutableSortedSet<ScalarInputPropertySchema> scalarInputs,
            ImmutableSortedSet<FileInputPropertySchema> fileInputs
        ) {
            return new DefaultTaskInstanceSchema(
                nestedProperties,
                scalarInputs,
                fileInputs,
                outputs.build(),
                localState.build(),
                destroys.build()
            );
        }
    }
}
