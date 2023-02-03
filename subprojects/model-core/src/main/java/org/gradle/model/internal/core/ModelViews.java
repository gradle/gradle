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

package org.gradle.model.internal.core;

import org.gradle.model.internal.type.ModelType;

import java.util.List;

public abstract class ModelViews {

    public static <T> ModelView<T> assertType(ModelView<?> untypedView, ModelType<T> type) {
        if (type.isAssignableFrom(untypedView.getType())) {
            @SuppressWarnings("unchecked") ModelView<T> view = (ModelView<T>) untypedView;
            return view;
        } else {
            // TODO better exception type
            throw new IllegalArgumentException("Model view of type " + untypedView.getType() + " requested as " + type);
        }
    }

    public static <T> ModelView<T> assertType(ModelView<?> untypedView, Class<T> type) {
        return assertType(untypedView, ModelType.of(type));
    }

    public static <T> ModelView<T> assertType(ModelView<?> untypedView, ModelReference<T> reference) {
        return assertType(untypedView, reference.getType());
    }

    public static <T> T getInstance(ModelView<?> untypedView, ModelReference<T> reference) {
        return assertType(untypedView, reference.getType()).getInstance();
    }

    public static <T> T getInstance(ModelView<?> untypedView, ModelType<T> type) {
        return assertType(untypedView, type).getInstance();
    }

    public static <T> T getInstance(ModelView<?> untypedView, Class<T> type) {
        return assertType(untypedView, ModelType.of(type)).getInstance();
    }

    public static <T> T getInstance(List<? extends ModelView<?>> views, int index, ModelType<T> type) {
        return getInstance(views.get(index), type);
    }

    public static <T> T getInstance(List<? extends ModelView<?>> views, int index, Class<T> type) {
        return getInstance(views.get(index), type);
    }
}
