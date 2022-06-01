/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.tasks.options;

import org.gradle.api.Task;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Built-in options are additional task options available
 * to all tasks.
 *
 * A built-in option is always a flag/modifier,
 * and, as such, does not take an argument.
 */
public class BuiltInOptionElement extends AbstractOptionElement {
    /**
     * The action to be performed in case the option is specified.
     */
    private final Consumer<Task> optionAction;

    public BuiltInOptionElement(String description, String optionName, Consumer<Task> optionAction) {
        super(description, optionName, Void.TYPE);
        this.optionAction = optionAction;
    }

    @Override
    public Set<String> getAvailableValues() {
        return Collections.emptySet();
    }

    @Override
    public void apply(Object object, List<String> parameterValues) {
        optionAction.accept((Task) object);
    }
}
