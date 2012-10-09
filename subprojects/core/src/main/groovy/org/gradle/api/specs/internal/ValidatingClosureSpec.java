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

package org.gradle.api.specs.internal;

import groovy.lang.Closure;
import org.gradle.api.GradleException;
import org.gradle.api.specs.Spec;

public class ValidatingClosureSpec<T> implements Spec<T> {

    private final Closure closure;
    private final String description;

    public ValidatingClosureSpec(Closure closure, String description) {
        this.closure = closure;
        this.description = description;
    }

    public boolean isSatisfiedBy(T element) {
        Object result = closure.call(element);
        if (result instanceof Boolean) {
            return (Boolean) result;
        }
        throw new GradleException("Closure: " + description + " must return boolean but it but it returned: " + result);
    }
}
