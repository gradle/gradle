/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.rules;

import org.gradle.api.Action;

import java.util.Collections;
import java.util.List;

public class NoInputsRuleAction<T> implements RuleAction<T> {
    private final Action<? super T> action;

    public NoInputsRuleAction(Action<? super T> action) {
        this.action = action;
    }

    @Override
    public List<Class<?>> getInputTypes() {
        return Collections.emptyList();
    }

    @Override
    public void execute(T subject, List<?> inputs) {
        action.execute(subject);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        NoInputsRuleAction that = (NoInputsRuleAction) o;
        return action.equals(that.action);
    }

    @Override
    public int hashCode() {
        return action.hashCode();
    }
}
