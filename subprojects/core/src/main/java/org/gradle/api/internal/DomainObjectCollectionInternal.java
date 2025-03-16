/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.api.internal;

import org.gradle.api.Action;
import org.gradle.api.Describable;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.internal.collections.ElementSource;

/**
 * Internal counterpart to {@link DomainObjectCollection}.
 */
public interface DomainObjectCollectionInternal<T> extends DomainObjectCollection<T>, Describable {

    /**
     * @see ElementSource#estimatedSize()
     */
    int estimatedSize();

    /**
     * Provide an action to be executed before any changes are made to the collection.
     */
    void beforeCollectionChanges(Action<String> action);
}
