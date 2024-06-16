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
import org.gradle.plugin.software.internal.Convention;

public class ActionConvention<T> implements Convention<ActionConventionReceiver<T>> {
    private final Action<T> action;

    public ActionConvention(Action<T> action) {
        this.action = action;
    }

    @Override
    public void apply(ActionConventionReceiver<T> receiver) {
        receiver.receive(action);
    }
}
