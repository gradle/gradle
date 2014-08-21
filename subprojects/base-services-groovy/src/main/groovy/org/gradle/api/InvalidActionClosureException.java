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
import org.gradle.internal.exceptions.Contextual;

/**
 * Thrown when a {@link Closure} is given as an {@link Action} or {@link RuleAction} implementation, but has the wrong signature.
 */
@Contextual
public class InvalidActionClosureException extends GradleException {

    private final Closure<?> closure;

    public InvalidActionClosureException(String message, Closure<?> closure) {
        super(message);
        this.closure = closure;
    }

    public InvalidActionClosureException(String message, Closure<?> closure, Throwable cause) {
        this(message, closure);
        initCause(cause);
    }

    /**
     * The closure being used as an action.
     *
     * @return The closure being used as an action.
     */
    public Closure<?> getClosure() {
        return closure;
    }
}
