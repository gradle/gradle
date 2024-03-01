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
import org.codehaus.groovy.runtime.InvokerHelper;
import org.gradle.api.specs.Spec;

public class ClosureSpec<T> implements Spec<T> {

    private final Closure<?> closure;

    public ClosureSpec(Closure<?> closure) {
        this.closure = closure;
    }

    @Override
    public boolean isSatisfiedBy(T element) {
        Object value = closure.call(element);
        return (Boolean)InvokerHelper.invokeMethod(value, "asBoolean", null);
    }
}
