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

package org.gradle.model.internal.registry;

import net.jcip.annotations.ThreadSafe;
import org.gradle.model.internal.core.Inputs;
import org.gradle.model.internal.core.ModelBinding;
import org.gradle.model.internal.core.ModelView;
import org.gradle.model.internal.type.ModelType;

import java.util.List;

@ThreadSafe
public class DefaultInputs implements Inputs {

    private final List<ModelBinding<?>> bindings;
    private final List<ModelView<?>> views;

    public DefaultInputs(List<ModelBinding<?>> bindings, List<ModelView<?>> views) {
        if (bindings.size() != views.size()) {
            throw new IllegalArgumentException("lists must be of same size");
        }

        this.bindings = bindings;
        this.views = views;
    }

    public <T> ModelView<? extends T> get(int i, ModelType<T> type) {
        ModelView<?> untypedView = views.get(i);
        if (type.isAssignableFrom(untypedView.getType())) {
            @SuppressWarnings("unchecked") ModelView<? extends T> view = (ModelView<? extends T>) untypedView;
            return view;
        } else {
            // TODO better exception type
            throw new IllegalArgumentException("Can't view input '" + i + "' (" + untypedView.getType() + ") as type '" + type + "'");
        }
    }

    @Override
    public List<ModelView<?>> getViews() {
        return views;
    }

    public int size() {
        return bindings.size();
    }

    public List<ModelBinding<?>> getBindings() {
        return bindings;
    }
}
