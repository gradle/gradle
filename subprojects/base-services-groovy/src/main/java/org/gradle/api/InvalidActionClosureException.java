/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api;

import groovy.lang.Closure;
import org.gradle.util.CollectionUtils;

import java.util.List;

/**
 * Thrown when a {@link Closure} is given as an {@link Action} implementation, but has the wrong signature.
 */
public class InvalidActionClosureException extends GradleException {

    private final Closure<?> closure;
    private final Object argument;

    public InvalidActionClosureException(Closure<?> closure, Object argument) {
        super(toMessage(closure, argument));
        this.closure = closure;
        this.argument = argument;
    }

    private static String toMessage(Closure<?> closure, Object argument) {
        List<Object> classNames = CollectionUtils.collect(closure.getParameterTypes(), new Transformer<Object, Class>() {
            @Override
            public Object transform(Class clazz) {
                return clazz.getName();
            }
        });
        return String.format(
                "The closure '%s' is not valid as an action for argument '%s'. It should accept no parameters, or one compatible with type '%s'. It accepts (%s).",
                closure, argument, argument.getClass().getName(), CollectionUtils.join(", ", classNames)
        );
    }

    /**
     * The closure being used as an action.
     *
     * @return The closure being used as an action.
     */
    public Closure<?> getClosure() {
        return closure;
    }

    /**
     * The argument the action was executed with.
     *
     * @return The argument the action was executed with.
     */
    public Object getArgument() {
        return argument;
    }
}
