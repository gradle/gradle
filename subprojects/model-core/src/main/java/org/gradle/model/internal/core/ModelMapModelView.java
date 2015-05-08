/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.model.internal.core;

import net.jcip.annotations.NotThreadSafe;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.type.ModelType;

@NotThreadSafe
public class ModelMapModelView<T extends ModelMap<?>> implements ModelView<T> {
    private final ModelType<T> type;
    private final T instance;
    private final ModelPath path;
    private final DefaultModelViewState state;

    public ModelMapModelView(ModelPath path, ModelType<T> type, T instance, DefaultModelViewState state) {
        this.path = path;
        this.type = type;
        this.state = state;
        this.instance = instance;
    }

    @Override
    public ModelPath getPath() {
        return path;
    }

    public ModelType<T> getType() {
        return type;
    }

    public T getInstance() {
        return instance;
    }

    public void close() {
        state.close();
    }
}
