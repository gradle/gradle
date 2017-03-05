/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.collections;

import org.gradle.api.Action;

public interface CollectionEventRegister<T> {
    Action<T> getAddAction();

    Action<T> getRemoveAction();

    Action<? super T> registerAddAction(Action<? super T> addAction);

    Action<? super T> registerRemoveAction(Action<? super T> removeAction);

    <S extends T> CollectionEventRegister<S> filtered(CollectionFilter<S> filter);
}
