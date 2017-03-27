/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.listener;

import org.gradle.api.Action;
import org.gradle.internal.ImmutableActionSet;

/**
 * A mutable composite {@link Action}.
 *
 * This is an internal api as `org.gradle.listener` is considered an internal package.
 * For public use, implement a custom composite action. This class will be removed with
 * one of the next Gradle releases.
 *
 * @deprecated Use {@link org.gradle.internal.ImmutableActionSet} or {@link org.gradle.internal.MutableActionSet}
 */
@Deprecated
public class ActionBroadcast<T> implements Action<T> {
    private ImmutableActionSet<T> actions = ImmutableActionSet.empty();

    public void add(Action<? super T> action) {
        this.actions = actions.add(action);
    }

    public void execute(T t) {
        actions.execute(t);
    }
}
