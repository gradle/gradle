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

import org.gradle.internal.Factories;
import org.gradle.internal.Factory;

public class InstanceModelAdapter implements ModelAdapter {

    private final ModelView<?> view;

    public static <T> ModelAdapter of(ModelType<T> type, T instance) {
        return new InstanceModelAdapter(new InstanceView<T>(type, Factories.constant(instance)));
    }

    public static <T> ModelAdapter of(ModelType<T> type, Factory<? extends T> factory) {
        return new InstanceModelAdapter(new InstanceView<T>(type, factory));
    }

    private InstanceModelAdapter(ModelView<?> view) {
        this.view = view;
    }

    public <T> ModelView<? extends T> asWritable(ModelType<T> type) {
        return type(type);
    }

    public <T> ModelView<? extends T> asReadOnly(ModelType<T> type) {
        return type(type);
    }

    private <T> ModelView<? extends T> type(ModelType<T> targetType) {
        if (targetType.isAssignableFrom(view.getType())) {
            @SuppressWarnings("unchecked") ModelView<? extends T> cast = (ModelView<? extends T>) view;
            return cast;
        } else {
            return null;
        }
    }

    private static class InstanceView<T> implements ModelView<T> {

        private final ModelType<T> type;
        private final Factory<? extends T> factory;
        private T instance;

        private InstanceView(ModelType<T> type, Factory<? extends T> factory) {
            this.type = type;
            this.factory = factory;
        }

        public ModelType<T> getType() {
            return type;
        }

        public T getInstance() {
            if (instance == null) {
                instance = factory.create();
            }
            return instance;
        }

        public void close() {

        }
    }
}
