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

import com.google.common.collect.Lists;
import groovy.lang.Closure;

import java.util.Arrays;
import java.util.List;

public class ClosureBackedRuleAction<T> implements RuleAction<T> {
    private final Closure<?> closure;
    private final Class<? super T> subjectType;
    private final List<Class<?>> inputTypes;

    public ClosureBackedRuleAction(Class<T> subjectType, Closure<?> closure) {
        this.subjectType = subjectType;
        this.closure = closure;
        this.inputTypes = parseInputTypes(closure);
    }

    @Override
    public List<Class<?>> getInputTypes() {
        return inputTypes;
    }

    @Override
    public void execute(T subject, List<?> inputs) {
        Closure<?> copy = (Closure<?>) closure.clone();
        copy.setResolveStrategy(Closure.DELEGATE_FIRST);
        copy.setDelegate(subject);

        if (closure.getMaximumNumberOfParameters() == 0) {
            copy.call();
        } else {
            Object[] argList = new Object[inputs.size() + 1];
            argList[0] = subject;
            int i = 1;
            for (Object arg : inputs) {
                argList[i++] = arg;
            }
            copy.call(argList);
        }
    }

    private List<Class<?>> parseInputTypes(Closure<?> closure) {
        Class<?>[] parameterTypes = closure.getParameterTypes();
        List<Class<?>> inputTypes = Lists.newArrayList();

        if (parameterTypes.length != 0) {
            if (parameterTypes[0].isAssignableFrom(subjectType)) {
                inputTypes.addAll(Arrays.asList(parameterTypes).subList(1, parameterTypes.length));
            } else {
                throw new RuleActionValidationException(String.format("First parameter of rule action closure must be of type '%s'.", subjectType.getSimpleName()));
            }
        }

        return inputTypes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ClosureBackedRuleAction<?> that = (ClosureBackedRuleAction<?>) o;
        return closure.equals(that.closure)
                && subjectType.equals(that.subjectType);
    }

    @Override
    public int hashCode() {
        int result = closure.hashCode();
        result = 31 * result + subjectType.hashCode();
        return result;
    }
}
