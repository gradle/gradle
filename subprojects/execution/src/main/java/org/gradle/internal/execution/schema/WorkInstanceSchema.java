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
import org.gradle.internal.schema.InstanceSchema;
import org.gradle.internal.schema.NestedPropertySchema;

import java.util.stream.Stream;

public interface WorkInstanceSchema extends InstanceSchema {

    Stream<ScalarInputPropertySchema> getInputs();

    Stream<FileInputPropertySchema> getFileInputs();

    abstract class Builder<S extends WorkInstanceSchema> extends InstanceSchema.Builder<S> {
        private final ImmutableSortedSet.Builder<ScalarInputPropertySchema> scalarInputs = ImmutableSortedSet.naturalOrder();
        private final ImmutableSortedSet.Builder<FileInputPropertySchema> fileInputs = ImmutableSortedSet.naturalOrder();

        public void add(ScalarInputPropertySchema property) {
            scalarInputs.add(property);
        }

        public void add(FileInputPropertySchema property) {
            fileInputs.add(property);
        }

        @Override
        protected S build(ImmutableSortedSet<NestedPropertySchema> nestedPropertySchemas) {
            return build(
                nestedPropertySchemas,
                scalarInputs.build(),
                fileInputs.build()
            );
        }

        protected abstract S build(
            ImmutableSortedSet<NestedPropertySchema> nestedPropertySchemas,
            ImmutableSortedSet<ScalarInputPropertySchema> scalarInputs,
            ImmutableSortedSet<FileInputPropertySchema> fileInputs
        );
    }
}
