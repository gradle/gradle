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

import org.gradle.api.NonNullApi;
import org.gradle.internal.InternalTransformer;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

import static org.gradle.util.internal.ArrayUtils.contains;
import static org.gradle.util.internal.CollectionUtils.join;

/**
 * Checks that services are being declared in the correct scope.
 * <p>
 * Only services that are annotated with {@link ServiceScope} are validated.
 */
@NonNullApi
class ServiceScopeValidator implements AnnotatedServiceLifecycleHandler {

    private static final List<Class<? extends Annotation>> SCOPE_ANNOTATIONS = Collections.<Class<? extends Annotation>>singletonList(ServiceScope.class);

    private final Class<? extends Scope> scope;

    public ServiceScopeValidator(Class<? extends Scope> scope) {
        this.scope = scope;
    }

    @Override
    public List<Class<? extends Annotation>> getAnnotations() {
        return SCOPE_ANNOTATIONS;
    }

    @Override
    public void whenRegistered(Class<? extends Annotation> annotation, Registration registration) {
        validateScope(registration.getDeclaredType());
    }

    private void validateScope(Class<?> serviceType) {
        Class<? extends Scope>[] serviceScopes = scopeOf(serviceType);

        if (serviceScopes == null) {
            return;
        }

        if (serviceScopes.length == 0) {
            throw new IllegalArgumentException(String.format("Service '%s' is declared with empty scope list", serviceType.getName()));
        }

        if (!contains(serviceScopes, scope)) {
            throw new IllegalArgumentException(invalidScopeMessage(serviceType, serviceScopes));
        }
    }

    private String invalidScopeMessage(Class<?> serviceType, Class<? extends Scope>[] actualScopes) {
        return String.format(
            "The service '%s' declares %s but is registered in the '%s' scope. " +
                "Either update the '@ServiceScope()' annotation on '%s' to include the '%s' scope " +
                "or move the service registration to one of the declared scopes.",
            serviceType.getName(),
            displayScopes(actualScopes),
            scope.getSimpleName(),
            serviceType.getSimpleName(),
            scope.getSimpleName()
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

    @Nullable
    private static Class<? extends Scope>[] scopeOf(Class<?> serviceType) {
        ServiceScope scopeAnnotation = serviceType.getAnnotation(ServiceScope.class);
        return scopeAnnotation != null ? scopeAnnotation.value() : null;
    }
}
