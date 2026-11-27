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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.reflect.TypeToken;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.tasks.properties.DefaultServiceReferenceSpec;
import org.gradle.api.internal.tasks.properties.ServiceReferenceSpec;
import org.gradle.api.problems.internal.ProblemInternal;
import org.gradle.api.problems.internal.ProblemsInternal;
import org.gradle.api.services.ServiceReference;
import org.gradle.api.tasks.TaskLifecycleListener;
import org.gradle.cache.internal.CrossBuildInMemoryCache;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.execution.model.annotations.ServiceReferencePropertyAnnotationHandler;
import org.gradle.internal.instantiation.InstanceFactory;
import org.gradle.internal.instantiation.InstantiationScheme;
import org.gradle.internal.properties.annotations.PropertyMetadata;
import org.gradle.internal.properties.annotations.TypeMetadata;
import org.gradle.internal.properties.annotations.TypeMetadataStore;
import org.gradle.internal.reflect.DefaultTypeValidationContext;
import org.gradle.internal.reflect.validation.TypeValidationProblemRenderer;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates {@link TaskLifecycleListener} types and keeps what is known about them.
 *
 * <p>Everything checked here depends only on the listener type, so it is checked once per type
 * and reported when the listener is registered, not when the task executes.</p>
 */
@ServiceScope(Scope.Global.class)
public class TaskLifecycleListenerClassInfoStore {

    private final CrossBuildInMemoryCache<Class<?>, TaskLifecycleListenerClassInfo> classInfos;
    private final TypeMetadataStore typeMetadataStore;
    private final InstantiationScheme instantiationScheme;

    public TaskLifecycleListenerClassInfoStore(CrossBuildInMemoryCacheFactory cacheFactory, TaskLifecycleListenerScheme listenerScheme) {
        this.classInfos = cacheFactory.newClassCache();
        this.typeMetadataStore = listenerScheme.getMetadataStore();
        this.instantiationScheme = listenerScheme.getInstantiationScheme();
    }

    /**
     * Returns the info of the given listener type.
     *
     * @throws org.gradle.api.GradleException when the type is not a valid listener.
     */
    public TaskLifecycleListenerClassInfo getClassInfo(Class<? extends TaskLifecycleListener<?>> listenerType, ProblemsInternal problems) {
        return classInfos.get(listenerType, type -> createClassInfo(type, problems));
    }

    private TaskLifecycleListenerClassInfo createClassInfo(Class<?> listenerType, ProblemsInternal problems) {
        ImmutableList<Class<?>> acceptedTaskTypes = acceptedTaskTypesOf(listenerType);
        TypeMetadata typeMetadata = typeMetadataStore.getTypeMetadata(listenerType);
        checkAnnotations(listenerType, typeMetadata, problems);
        ImmutableSortedSet.Builder<ServiceReferenceSpec> serviceReferences = ImmutableSortedSet.naturalOrder();
        for (PropertyMetadata property : typeMetadata.getPropertiesMetadata()) {
            if (property.getPropertyType() == ServiceReference.class) {
                serviceReferences.add(serviceReferenceOf(property));
            }
        }
        checkInjectedServices(listenerType);
        return new TaskLifecycleListenerClassInfo(acceptedTaskTypes, serviceReferences.build());
    }

    /**
     * Rejects the annotations that listeners do not support, such as {@code @Nested}:
     * the services referenced by nested beans could only be found from a listener instance.
     */
    private static void checkAnnotations(Class<?> listenerType, TypeMetadata typeMetadata, ProblemsInternal problems) {
        DefaultTypeValidationContext validationContext = DefaultTypeValidationContext.withRootType(listenerType, false, problems);
        typeMetadata.visitValidationFailures(null, validationContext);
        ImmutableList<ProblemInternal> errors = validationContext.getErrors();
        if (!errors.isEmpty()) {
            throw new DefaultMultiCauseException(
                String.format(errors.size() == 1
                    ? "A problem was found with lifecycle listener %s."
                    : "Some problems were found with lifecycle listener %s.",
                    listenerType.getName()),
                errors.stream()
                    .map(TypeValidationProblemRenderer::renderMinimalInformationAbout)
                    .sorted()
                    .map(InvalidUserDataException::new)
                    .collect(ImmutableList.toImmutableList())
            );
        }
    }

    private static ImmutableList<Class<?>> acceptedTaskTypesOf(Class<?> listenerType) {
        Type acceptedType = TypeToken.of(listenerType).resolveType(TaskLifecycleListener.class.getTypeParameters()[0]).getType();
        if (acceptedType instanceof TypeVariable) {
            // A generic listener, or one declared without type arguments, accepts the tasks that match the bounds of its type variable
            ImmutableList.Builder<Class<?>> bounds = ImmutableList.builder();
            for (Type bound : ((TypeVariable<?>) acceptedType).getBounds()) {
                bounds.add(TypeToken.of(bound).getRawType());
            }
            return bounds.build();
        }
        return ImmutableList.of(TypeToken.of(acceptedType).getRawType());
    }

    /**
     * {@link #checkAnnotations} has already checked that the property holds a build service.
     */
    private static ServiceReferenceSpec serviceReferenceOf(PropertyMetadata property) {
        String serviceName = property.getAnnotation(ServiceReference.class).map(ServiceReference::value).orElse("");
        return new DefaultServiceReferenceSpec(property.getPropertyName(), ServiceReferencePropertyAnnotationHandler.serviceTypeOf(property), serviceName.trim());
    }

    /**
     * Injected services are looked up when the listener first uses them, which can be after the task has executed, or never.
     * So the declared services are checked here instead. This also makes sure that the listener class can be generated.
     */
    private void checkInjectedServices(Class<?> listenerType) {
        InstanceFactory<?> instanceFactory = instantiationScheme.forType(listenerType);
        // Nested beans are rejected, so only the listener type itself declares services
        for (Set<Class<?>> injectedServices : instanceFactory.injectedServicesByDeclaringType().values()) {
            for (Class<?> serviceType : injectedServices) {
                if (!TaskLifecycleListenerScheme.INJECTABLE_SERVICES.contains(serviceType)) {
                    throw invalid(listenerType, String.format(
                        "it injects a service of type %s, which is not available to lifecycle listeners. The services that can be injected are: %s.",
                        serviceType.getName(), TaskLifecycleListenerScheme.INJECTABLE_SERVICES.stream().map(Class::getSimpleName).collect(Collectors.joining(", "))
                    ));
                }
            }
        }
    }

    private static InvalidUserDataException invalid(Class<?> listenerType, String reason) {
        return new InvalidUserDataException(String.format("Cannot use lifecycle listener %s: %s", listenerType.getName(), reason));
    }
}
