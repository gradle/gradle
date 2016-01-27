/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.manage.schema.extract;

import org.gradle.api.Action;
import org.gradle.model.internal.inspect.ValidationProblemCollector;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.type.ModelType;

import java.lang.reflect.Method;

public interface ModelSchemaExtractionContext<T> extends ValidationProblemCollector {
    /**
     * Returns the type currently being inspected.
     */
    ModelType<T> getType();

    /**
     * Registers a type that should be inspected.
     */
    <C> ModelSchemaExtractionContext<C> child(ModelType<C> type, String description);

    /**
     * Registers a type that should be inspected. The given action is invoked after the type has been inspected.
     */
    <C> ModelSchemaExtractionContext<C> child(ModelType<C> type, String description, Action<? super ModelSchema<C>> validator);

    /**
     * Marks the type as recognized.
     */
    void found(ModelSchema<T> result);

    /**
     * Adds a problem with a method.
     */
    void add(Method method, String problem);
}
