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

package org.gradle.internal.model;

import com.google.common.collect.ImmutableMap;
import org.gradle.internal.schema.InstanceSchema;

import java.lang.annotation.Annotation;
import java.util.function.Function;
import java.util.stream.Stream;

public abstract class AbstractInstanceModelBuilder<V> {
    private final ImmutableMap<Class<? extends Annotation>, PropertyModelBuilder<?, V>> propertyModelBuilders;

    @SuppressWarnings("varargs")
    @SafeVarargs
    protected AbstractInstanceModelBuilder(PropertyModelBuilder<?, V>... propertyModelBuilders) {
        this.propertyModelBuilders = Stream.of(propertyModelBuilders)
            .collect(ImmutableMap.toImmutableMap(
                PropertyModelBuilder::getHandledPropertyType,
                Function.identity()
            ));
    }

    protected void handleProperties(InstanceSchema schema, V visitor) {
    }
}
