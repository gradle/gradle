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

package org.gradle.api.tasks.diagnostics.internal.dsl;

import groovy.lang.Closure;
import org.gradle.api.GradleException;
import org.gradle.api.specs.Spec;

/**
* by Szczepan Faber, created at: 10/9/12
*/
class ClosureBasedSpec<T> implements Spec<T> {

    //TODO SF move somewhere else
    private final Closure closure;
    private final String description;

    public ClosureBasedSpec(Closure closure, String description) {
        this.closure = closure;
        this.description = description;
    }

    public boolean isSatisfiedBy(T element) {
        Object result = closure.call(element);
        if (result instanceof Boolean) {
            return (Boolean) result;
        }
        throw new GradleException("Incorrect closure specified for " + description + ". The closure must return boolean but it returned: " + result);
    }
}
