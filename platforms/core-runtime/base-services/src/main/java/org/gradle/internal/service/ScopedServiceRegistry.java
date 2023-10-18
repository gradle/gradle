/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.service;

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;

public class ScopedServiceRegistry extends DefaultServiceRegistry {
    private final Class<? extends Scopes> scope;

    public ScopedServiceRegistry(Class<? extends Scopes> scope) {
        this.scope = scope;
    }

    @Override
    public <T> DefaultServiceRegistry add(Class<? extends T> serviceType, T serviceInstance) {
        assertCorrectScope(serviceType);
        return super.add(serviceType, serviceInstance);
    }

    private void assertCorrectScope(Class<?> serviceType) {
        Class<? extends Scope> serviceScope = scopeOf(serviceType);
        if (scope != serviceScope) {
            throw new IllegalArgumentException(invalidScopeMessage(serviceType, serviceScope));
        }
    }

    private String invalidScopeMessage(
        Class<?> serviceType,
        Class<? extends Scope> actualScope
    ) {
        return String.format("Service '%s' was declared in scope '%s' but registered in scope '%s'",
            serviceType.getSimpleName(),
            actualScope.getSimpleName(),
            scope.getSimpleName()
        );
    }

    @Nullable
    private Class<? extends Scope> scopeOf(Class<?> serviceType) {
        ServiceScope scopeAnnotation = serviceType.getAnnotation(ServiceScope.class);
        return scopeAnnotation != null ? scopeAnnotation.value() : null;
    }
}
