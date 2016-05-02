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
import org.gradle.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

public class ActionBroadcast<T> implements Action<T> {
    private final List<Action<? super T>> actions = new ArrayList<Action<? super T>>();

    public ActionBroadcast() {}

    public ActionBroadcast(Iterable<Action<? super T>> actions) {
        CollectionUtils.addAll(this.actions, actions);
    }

    public void add(Action<? super T> action) {
        actions.add(action);
    }

    public void execute(T t) {
        for (Action<? super T> action : new ArrayList<Action<? super T>>(actions)) {
            action.execute(t);
        }
    }
}
