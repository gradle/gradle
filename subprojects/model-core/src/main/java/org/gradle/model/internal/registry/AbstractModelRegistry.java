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

package org.gradle.model.internal.registry;

import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.type.ModelType;

/**
 * Implements non essential methods of {@link ModelRegistry} on top of the essential methods provided by subclasses.
 */
public abstract class AbstractModelRegistry implements ModelRegistry {

    @Override
    public <T> T find(String path, Class<T> type) {
        return find(path, ModelType.of(type));
    }

    @Override
    public <T> T find(String path, ModelType<T> type) {
        return find(ModelPath.path(path), type);
    }

    @Override
    public <T> T realize(String path, Class<T> type) {
        return realize(path, ModelType.of(type));
    }

    @Override
    public <T> T realize(String path, ModelType<T> type) {
        return realize(ModelPath.path(path), type);
    }
}
