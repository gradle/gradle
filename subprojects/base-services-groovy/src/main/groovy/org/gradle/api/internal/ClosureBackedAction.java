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

package org.gradle.api.internal;

import com.google.common.base.Objects;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.InvalidActionClosureException;
import org.gradle.util.Configurable;

public class ClosureBackedAction<T> implements Action<T> {
    private final Closure closure;
    private final boolean configureableAware;
    private final int resolveStrategy;

    public ClosureBackedAction(Closure closure) {
        this(closure, Closure.DELEGATE_FIRST, true);
    }

    public ClosureBackedAction(Closure closure, int resolveStrategy) {
        this(closure, resolveStrategy, false);
    }

    public ClosureBackedAction(Closure closure, int resolveStrategy, boolean configureableAware) {
        this.closure = closure;
        this.configureableAware = configureableAware;
        this.resolveStrategy = resolveStrategy;
    }

    public static <T> void execute(T delegate, Closure<?> closure) {
        new ClosureBackedAction<T>(closure).execute(delegate);
    }

    public void execute(T delegate) {
        if (closure == null) {
            return;
        }

        try {
            if (configureableAware && delegate instanceof Configurable) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ClosureBackedAction that = (ClosureBackedAction) o;

        if (configureableAware != that.configureableAware) {
            return false;
        }
        if (resolveStrategy != that.resolveStrategy) {
            return false;
        }
        if (!closure.equals(that.closure)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = closure.hashCode();
        result = 31 * result + (configureableAware ? 1 : 0);
        result = 31 * result + resolveStrategy;
        return result;
    }
}
