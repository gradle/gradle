/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model;

import org.gradle.api.Incubating;
import org.gradle.model.collection.CollectionBuilder;

/**
 * Model backed map like structure allowing adding of items where instantiation is managed.
 *
 * @param <T> the contract type for all items
 */
@Incubating
public interface ModelMap<T> extends CollectionBuilder<T> {

    @Override
    <S> ModelMap<S> withType(Class<S> type);
}
