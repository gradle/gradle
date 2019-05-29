/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.provider;

import org.gradle.api.Task;
import org.gradle.api.provider.Property;

public interface PropertyInternal<T> extends ProviderInternal<T> {
    /**
     * Sets the property's value from some arbitrary object. Used from the Groovy DSL.
     */
    void setFromAnyValue(Object object);

    /**
     * Associates this property with the task that produces its value.
     */
    void attachProducer(Task producer);

    /**
     * Same semantics as {@link Property#finalizeValue()}. Finalizes the value of this property eagerly.
     */
    void finalizeValue();

    /**
     * Same semantics as {@link Property#finalizeValue()}, but finalizes the value of this property lazily and ignores changes to this property instead of failing. Generates a deprecation warning on changes.
     */
    void finalizeValueOnReadAndWarnAboutChanges();
}
