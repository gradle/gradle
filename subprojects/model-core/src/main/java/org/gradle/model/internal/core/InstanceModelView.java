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

import net.jcip.annotations.ThreadSafe;
import org.gradle.api.Action;
import org.gradle.internal.Actions;
import org.gradle.model.internal.type.ModelType;

@ThreadSafe
public class InstanceModelView<T> implements ModelView<T> {

    private final ModelPath path;
    private final ModelType<T> type;
    private final T instance;
    private final Action<? super T> onClose;

    public InstanceModelView(ModelPath path, ModelType<T> type, T instance, Action<? super T> onClose) {
        this.path = path;
        this.type = type;
        this.instance = instance;
        this.onClose = onClose;
    }

    public static <T> ModelView<T> of(ModelPath path, ModelType<T> type, T instance) {
        return of(path, type, instance, Actions.doNothing());
    }

    public static <T> ModelView<T> of(ModelPath path, ModelType<T> type, T instance, Action<? super T> onClose) {
        return new InstanceModelView<T>(path, type, instance, onClose);
    }

    @Override
    public ModelPath getPath() {
        return path;
    }

    @Override
    public ModelType<T> getType() {
        return type;
    }

    @Override
    public T getInstance() {
        return instance;
    }

    @Override
    public void close() {
        onClose.execute(instance);
    }
}
