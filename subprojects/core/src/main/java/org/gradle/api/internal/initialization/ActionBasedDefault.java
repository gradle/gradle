/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.initialization;

import org.gradle.api.Action;
import org.gradle.plugin.software.internal.ModelDefault;
import org.gradle.plugin.software.internal.ModelDefault.Visitor;

public class ActionBasedDefault<T> implements ModelDefault<Visitor<Action<? super T>>> {
    private final Action<? super T> action;

    public ActionBasedDefault(Action<? super T> action) {
        this.action = action;
    }

    @Override
    public void visit(Visitor<Action<? super T>> visitor) {
        visitor.apply(action);
    }
}
