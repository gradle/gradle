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

import org.gradle.internal.InternalTransformer;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;

import static org.gradle.util.internal.ArrayUtils.contains;
import static org.gradle.util.internal.CollectionUtils.join;

/**
 * A registry validating that all registered services are annotated with a corresponding {@link Scope}.
 */
class ScopedServiceRegistry extends DefaultServiceRegistry {

    private final Class<? extends Scope> scope;

    public ScopedServiceRegistry(
        Class<? extends Scope> scope,
        boolean strict,
        String displayName,
        ServiceRegistry... parents
    ) {
        super(displayName, parents);
        this.scope = scope;
        addServiceValidator(scope, strict);
    }

    /**
     * Validator implements a special type of service ({@link AnnotatedServiceLifecycleHandler})
     * that gets notified about all existing and further service registrations.
     */
    private void addServiceValidator(Class<? extends Scope> scope, boolean strict) {
        add(new ServiceScopeValidator(scope, strict));
    }

    @Override
    public DefaultServiceRegistry addProvider(ServiceRegistrationProvider provider) {
        validateProviderScope(provider.getClass());
        return super.addProvider(provider);
    }

    private void validateProviderScope(Class<?> providerType) {
        if (providerType.isAnonymousClass()) {
            return;
        }

        Class<? extends Scope>[] serviceScopes = scopeOf(providerType);

        if (serviceScopes == null) {
            throw new IllegalArgumentException(missingScopeMessage(providerType));
        }

        if (serviceScopes.length == 0) {
            throw new IllegalArgumentException(String.format("Service '%s' is declared with empty scope list", providerType.getName()));
        }

        if (!contains(serviceScopes, scope)) {
            throw new IllegalArgumentException(invalidScopeMessage(providerType, serviceScopes));
        }
    }

    @Nullable
    private static Class<? extends Scope>[] scopeOf(Class<?> type) {
        ServiceScope scopeAnnotation = type.getAnnotation(ServiceScope.class);
        return scopeAnnotation != null ? scopeAnnotation.value() : null;
    }

    private String invalidScopeMessage(Class<?> serviceType, Class<? extends Scope>[] actualScopes) {
        return String.format(
            "The service provider '%s' declares %s but is registered in the '%s' scope. " +
                "Either update the '@ServiceScope()' annotation on '%s' to include the '%s' scope " +
                "or move the service provider to one of the declared scopes.",
            serviceType.getName(),
            displayScopes(actualScopes),
            scope.getSimpleName(),
            serviceType.getSimpleName(),
            scope.getSimpleName()
        );
    }

    private String missingScopeMessage(Class<?> providerType) {
        return String.format(
            "The service provider '%s' is registered in the '%s' scope but does not declare it. " +
                "Add the '@ServiceScope()' annotation on '%s' with the '%s' scope.",
            providerType.getName(), scope.getSimpleName(), providerType.getSimpleName(), scope.getSimpleName()
        );
    }

    private static String displayScopes(Class<? extends Scope>[] scopes) {
        if (scopes.length == 1) {
            return "service scope '" + scopes[0].getSimpleName() + "'";
        }

        return "service scopes " + join(", ", scopes, new InternalTransformer<String, Class<? extends Scope>>() {
            @Override
            public String transform(Class<? extends Scope> aClass) {
                return "'" + aClass.getSimpleName() + "'";
            }
        });
    }
}
