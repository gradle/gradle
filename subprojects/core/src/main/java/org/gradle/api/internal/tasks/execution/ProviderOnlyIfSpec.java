/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.tasks.execution;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.provider.MissingValueException;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.OnlyIfDecision;

/**
 * An onlyIf predicate backed by a {@link Provider} of a {@link Boolean} or an {@link OnlyIfDecision}.
 */
public class ProviderOnlyIfSpec implements Spec<Object> {
    private final Provider<?> provider;

    public ProviderOnlyIfSpec(Provider<?> provider) {
        this.provider = provider;
    }

    /**
     * Queries the provider, failing if it has no value or a value of an unsupported type.
     */
    public OnlyIfDecision decide() {
        Object value;
        try {
            value = provider.get();
        } catch (MissingValueException e) {
            // MissingValueException is @Contextual, so as a cause it would hide the onlyIf context from the failure report
            InvalidUserDataException failure = new InvalidUserDataException("The onlyIf provider has no value. " + e.getMessage());
            failure.addSuppressed(e);
            throw failure;
        }
        if (value instanceof OnlyIfDecision) {
            return (OnlyIfDecision) value;
        }
        if (value instanceof Boolean) {
            return (Boolean) value ? OnlyIfDecision.proceed() : OnlyIfDecision.skip();
        }
        throw new InvalidUserDataException(String.format(
            "The onlyIf provider must have a value of type %s or %s, but its value '%s' is of type %s.",
            Boolean.class.getName(), OnlyIfDecision.class.getName(), value, value.getClass().getName()
        ));
    }

    @Override
    public boolean isSatisfiedBy(Object element) {
        return decide().shouldRun();
    }

    @Override
    public String toString() {
        return "ProviderOnlyIfSpec{provider=" + provider + '}';
    }
}
