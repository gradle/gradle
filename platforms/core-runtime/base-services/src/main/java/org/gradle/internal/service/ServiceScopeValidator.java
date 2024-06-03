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
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

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
    private final boolean strict;

    public ServiceScopeValidator(Class<? extends Scope> scope, boolean strict) {
        this.scope = scope;
        this.strict = strict;
    }

    @Override
    public List<Class<? extends Annotation>> getAnnotations() {
        return SCOPE_ANNOTATIONS;
    }

    @Override
    public Class<? extends Annotation> getImplicitAnnotation() {
        return ServiceScope.class;
    }

    @Override
    public void whenRegistered(Class<? extends Annotation> annotation, Registration registration) {
        validateScope(registration.getDeclaredType());
    }

    private void validateScope(Class<?> serviceType) {
        if (ServiceScopeValidator.class.isAssignableFrom(serviceType)) {
            return;
        }

        if (ServiceScopeValidatorWorkarounds.shouldSuppressValidation(serviceType)) {
            return;
        }

        Class<? extends Scope>[] serviceScopes = scopeOf(serviceType);

        if (serviceScopes == null) {
            if (strict) {
                failWithMissingScope(serviceType);
            }
            return;
        }

        if (serviceScopes.length == 0) {
            throw new IllegalArgumentException(String.format("Service '%s' is declared with empty scope list", serviceType.getName()));
        }

        if (!contains(serviceScopes, scope)) {
            throw new IllegalArgumentException(invalidScopeMessage(serviceType, serviceScopes));
        }
    }

    private void failWithMissingScope(Class<?> serviceType) {
        Set<Class<?>> annotatedSupertypes = findAnnotatedSupertypes(serviceType);
        if (annotatedSupertypes.size() != 1) {
            throw new IllegalArgumentException(missingScopeMessage(serviceType));
        }

        Class<?> inferredServiceType = annotatedSupertypes.iterator().next();
        throw new IllegalArgumentException(implementationWithMissingScopeMessage(inferredServiceType, serviceType));
    }

    // TODO: use the implementation from `org.gradle.internal.reflect.Types`, when its available for `:base-services`
    private static Set<Class<?>> findAnnotatedSupertypes(Class<?> serviceType) {
        Set<Class<?>> annotatedSuperTypes = new LinkedHashSet<Class<?>>();

        Set<Class<?>> seen = new HashSet<Class<?>>();
        seen.add(Object.class);

        Queue<Class<?>> queue = new ArrayDeque<Class<?>>();
        queue.add(serviceType);

        while (!queue.isEmpty()) {
            Class<?> type = queue.remove();
            if (scopeOf(type) != null) {
                annotatedSuperTypes.add(type);
                continue;
            }

            if (type.getSuperclass() != null) {
                queue.add(type.getSuperclass());
            }
            for (Class<?> superInterface : type.getInterfaces()) {
                if (seen.add(superInterface)) {
                    queue.add(superInterface);
                }
            }
        }

        return annotatedSuperTypes;
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

    private String missingScopeMessage(Class<?> serviceType) {
        return String.format(
            "The service '%s' is registered in the '%s' scope but does not declare it. " +
                "Add the '@ServiceScope()' annotation on '%s' with the '%s' scope.",
            serviceType.getName(), scope.getSimpleName(), serviceType.getSimpleName(), scope.getSimpleName()
        );
    }

    private String implementationWithMissingScopeMessage(Class<?> serviceType, Class<?> implementationType) {
        return String.format(
            "The service implementation '%s' is registered in the '%s' scope but does not declare it explicitly.\n" +
                "The implementation appears to serve %s.\n" +
                "Try the following:\n" +
                "- If registered via an instance or implementation type then use an overload providing an explicit service type, e.g. 'ServiceRegistration.add(serviceType, implementationType)'\n" +
                "- If registered via a creator-method in a service provider class then change the return type of the method to the service type\n" +
                "- Alternatively, add the '@ServiceScope()' to the implementation type",
            implementationType.getName(), scope.getSimpleName(), displayServiceTypes(serviceType)
        );
    }

    private static String displayServiceTypes(Class<?> serviceType) {
        return String.format("'%s' service type", serviceType.getSimpleName());
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
