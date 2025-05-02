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

package org.gradle.configuration.project;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.util.internal.ConfigureUtil;

import java.util.ArrayList;
import java.util.List;

public class DefaultProjectConfigurationActionContainer implements ProjectConfigurationActionContainer {
    private final List<Action<? super ProjectInternal>> actions = new ArrayList<Action<? super ProjectInternal>>();

    @Override
    public void finished() {
        actions.clear();
    }

    @Override
    public List<Action<? super ProjectInternal>> getActions() {
        return actions;
    }

    @Override
    public void add(Action<? super ProjectInternal> action) {
        actions.add(action);
    }

    @Override
    public void add(Closure action) {
        add(ConfigureUtil.configureUsing(action));
    }
}
