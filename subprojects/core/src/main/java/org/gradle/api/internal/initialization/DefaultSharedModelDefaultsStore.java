/*
 * Copyright 2025 the original author or authors.
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
import org.gradle.api.initialization.SharedModelDefaults;
import org.gradle.api.initialization.SharedModelDefaultsStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DefaultSharedModelDefaultsStore implements SharedModelDefaultsStore {

    private final List<Action<? super SharedModelDefaults>> actions = new ArrayList<>();

    @Override
    public void add(Action<? super SharedModelDefaults> action) {
        actions.add(action);
    }

    @Override
    public List<Action<? super SharedModelDefaults>> getActions() {
        return Collections.unmodifiableList(actions);
    }
}
