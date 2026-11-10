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
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.tasks.properties.DefaultServiceReferenceSpec;
import org.gradle.api.internal.tasks.properties.ServiceReferenceSpec;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.problems.Problems;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.ServiceReference;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskLifecycleListener;
import org.gradle.cache.internal.CrossBuildInMemoryCache;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.internal.Cast;
import org.gradle.internal.instantiation.InstanceFactory;
import org.gradle.internal.instantiation.InstantiationScheme;
import org.gradle.internal.properties.annotations.PropertyMetadata;
import org.gradle.internal.properties.annotations.TypeMetadata;
import org.gradle.internal.properties.annotations.TypeMetadataStore;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.process.ExecOperations;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
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

    /**
     * The services that listeners can inject.
     */
    public static final ImmutableList<Class<?>> INJECTABLE_SERVICES = ImmutableList.of(
        ObjectFactory.class,
        ProviderFactory.class,
        FileSystemOperations.class,
        ArchiveOperations.class,
        ExecOperations.class,
        ProjectLayout.class,
        Problems.class
    );

    private final CrossBuildInMemoryCache<Class<?>, TaskLifecycleListenerClassInfo> classInfos;
    private final TypeMetadataStore typeMetadataStore;
    private final InstantiationScheme instantiationScheme;

    public TaskLifecycleListenerClassInfoStore(CrossBuildInMemoryCacheFactory cacheFactory, TypeMetadataStore typeMetadataStore, InstantiationScheme instantiationScheme) {
        this.classInfos = cacheFactory.newClassCache();
        this.typeMetadataStore = typeMetadataStore;
        this.instantiationScheme = instantiationScheme;
    }

    /**
     * Returns the info of the given listener type.
     *
     * @throws InvalidUserDataException when the type is not a valid listener.
     */
    public TaskLifecycleListenerClassInfo getClassInfo(Class<? extends TaskLifecycleListener<?>> listenerType) {
        return classInfos.get(listenerType, this::createClassInfo);
    }

    private TaskLifecycleListenerClassInfo createClassInfo(Class<?> listenerType) {
        Class<?> acceptedTaskType = acceptedTaskTypeOf(listenerType);
        TypeMetadata typeMetadata = typeMetadataStore.getTypeMetadata(listenerType);
        ImmutableSortedSet.Builder<ServiceReferenceSpec> serviceReferences = ImmutableSortedSet.naturalOrder();
        for (PropertyMetadata property : typeMetadata.getPropertiesMetadata()) {
            if (property.getPropertyType() == Nested.class) {
                // The services referenced by nested beans could only be found from a listener instance
                throw invalid(listenerType, String.format(
                    "property '%s' is annotated with @Nested, which is not supported for lifecycle listeners. Use @ServiceReference properties or instance fields instead.",
                    property.getPropertyName()
                ));
            }
            if (property.getPropertyType() == ServiceReference.class) {
                serviceReferences.add(serviceReferenceOf(listenerType, property));
            }
        }
        checkInjectedServices(listenerType);
        return new TaskLifecycleListenerClassInfo(acceptedTaskType, serviceReferences.build());
    }

    private static Class<?> acceptedTaskTypeOf(Class<?> listenerType) {
        Type acceptedType = TypeToken.of(listenerType).resolveType(TaskLifecycleListener.class.getTypeParameters()[0]).getType();
        if (!(acceptedType instanceof Class) && !(acceptedType instanceof ParameterizedType)) {
            throw invalid(listenerType, "the task type the listener accepts cannot be determined. Declare the listener as implementing TaskLifecycleListener with a concrete task type.");
        }
        return TypeToken.of(acceptedType).getRawType();
    }

    private static ServiceReferenceSpec serviceReferenceOf(Class<?> listenerType, PropertyMetadata property) {
        Type declaredType = property.getDeclaredType().getType();
        if (declaredType instanceof ParameterizedType && Property.class.isAssignableFrom(property.getDeclaredType().getRawType())) {
            Class<?> serviceType = TypeToken.of(((ParameterizedType) declaredType).getActualTypeArguments()[0]).getRawType();
            if (BuildService.class.isAssignableFrom(serviceType)) {
                String serviceName = property.getAnnotation(ServiceReference.class).map(ServiceReference::value).orElse("");
                return new DefaultServiceReferenceSpec(property.getPropertyName(), Cast.uncheckedNonnullCast(serviceType), serviceName.trim());
            }
        }
        throw invalid(listenerType, String.format(
            "property '%s' is annotated with @ServiceReference, but its type is not a Property of a build service.",
            property.getPropertyName()
        ));
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
                if (!INJECTABLE_SERVICES.contains(serviceType)) {
                    throw invalid(listenerType, String.format(
                        "it injects a service of type %s, which is not available to lifecycle listeners. The services that can be injected are: %s.",
                        serviceType.getName(), INJECTABLE_SERVICES.stream().map(Class::getSimpleName).collect(Collectors.joining(", "))
                    ));
                }
            }
        }
    }

    private static InvalidUserDataException invalid(Class<?> listenerType, String reason) {
        return new InvalidUserDataException(String.format("Cannot use lifecycle listener %s: %s", listenerType.getName(), reason));
    }
}
