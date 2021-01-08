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
package org.gradle.internal;

import org.gradle.api.Action;

/**
 * A mutable composite {@link Action}. Actions are executed in the order added, stopping on the first failure.
 *
 * This type is not thread-safe.
 *
 * Consider using {@link org.gradle.internal.ImmutableActionSet} instead of this.
 *
 * Implements {@link InternalListener} as components themselves should be decorated if appropriate.
 */
public class MutableActionSet<T> implements Action<T>, InternalListener {
    private ImmutableActionSet<T> actions = ImmutableActionSet.empty();

    public void add(Action<? super T> action) {
        this.actions = actions.add(action);
    }

    @Override
    public void execute(T t) {
        actions.execute(t);
    }

    public boolean isEmpty() {
        return actions.isEmpty();
    }
}
