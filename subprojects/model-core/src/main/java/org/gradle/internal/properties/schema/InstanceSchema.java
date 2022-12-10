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

package org.gradle.internal.properties.schema;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;

import java.util.Comparator;
import java.util.stream.Stream;

public interface InstanceSchema {
    Stream<NestedPropertySchema> nestedProperties();

    abstract class Builder<S extends InstanceSchema> {
        private final ImmutableList.Builder<NestedPropertySchema> nestedPropertySchemas = ImmutableList.builder();

        public void add(NestedPropertySchema property) {
            nestedPropertySchemas.add(property);
        }

        public S build() {
            return build(toSortedList(nestedPropertySchemas));
        }

        protected abstract S build(ImmutableList<NestedPropertySchema> nestedPropertySchemas);

        protected static <P extends PropertySchema> ImmutableList<P> toSortedList(ImmutableCollection.Builder<P> builder) {
            // A sorted list is better here because it does not use the comparator for equals()
            return ImmutableList.sortedCopyOf(Comparator.comparing(PropertySchema::getQualifiedName), builder.build());
        }
    }
}
