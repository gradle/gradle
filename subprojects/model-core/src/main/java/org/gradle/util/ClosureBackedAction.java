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

package org.gradle.util;

import com.google.common.base.Objects;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.InvalidActionClosureException;

/**
 * NOTE: You should use {@link ConfigureUtil} instead of this class when adding a closure backed method to the DSL, whether statically or dynamically added. {@link ConfigureUtil} is much more efficient and takes care of applying consistent DSL behaviour when closures are nested.
 */
public class ClosureBackedAction<T> implements Action<T> {

    private final Closure closure;
    private final int resolveStrategy;
    private final boolean configurableAware;

    public static <T> ClosureBackedAction<T> of(Closure<?> closure) {
        return new ClosureBackedAction<T>(closure);
    }

    public ClosureBackedAction(Closure closure) {
        this(closure, Closure.DELEGATE_FIRST, true);
    }

    public ClosureBackedAction(Closure closure, int resolveStrategy) {
        this(closure, resolveStrategy, false);
    }

    public ClosureBackedAction(Closure closure, int resolveStrategy, boolean configurableAware) {
        this.closure = closure;
        this.resolveStrategy = resolveStrategy;
        this.configurableAware = configurableAware;
    }

    public static <T> void execute(T delegate, Closure<?> closure) {
        new ClosureBackedAction<T>(closure).execute(delegate);
    }

    @Override
    public void execute(T delegate) {
        if (closure == null) {
            return;
        }

        try {
            if (configurableAware && delegate instanceof Configurable) {
                ((Configurable) delegate).configure(closure);
            } else {
                Closure copy = (Closure) closure.clone();
                copy.setResolveStrategy(resolveStrategy);
                copy.setDelegate(delegate);
                if (copy.getMaximumNumberOfParameters() == 0) {
                    copy.call();
                } else {
                    copy.call(delegate);
                }
            }
        } catch (groovy.lang.MissingMethodException e) {
            if (Objects.equal(e.getType(), closure.getClass()) && Objects.equal(e.getMethod(), "doCall")) {
                throw new InvalidActionClosureException(closure, delegate);
            }
            throw e;
        }
    }

    public Closure getClosure() {
        return closure;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ClosureBackedAction that = (ClosureBackedAction) o;
        return configurableAware == that.configurableAware
            && resolveStrategy == that.resolveStrategy
            && closure.equals(that.closure);
    }

    @Override
    public int hashCode() {
        int result = closure.hashCode();
        result = 31 * result + (configurableAware ? 1 : 0);
        result = 31 * result + resolveStrategy;
        return result;
    }
}
