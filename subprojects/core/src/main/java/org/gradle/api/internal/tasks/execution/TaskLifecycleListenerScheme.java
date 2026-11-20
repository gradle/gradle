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
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.tasks.properties.AbstractTypeScheme;
import org.gradle.api.internal.tasks.properties.InspectionScheme;
import org.gradle.api.problems.Problems;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.api.tasks.TaskLifecycleListener;
import org.gradle.internal.instantiation.InstantiationScheme;
import org.gradle.internal.instantiation.managed.ManagedObjectRegistry;
import org.gradle.internal.properties.annotations.MissingPropertyAnnotationHandler;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.process.ExecOperations;

import java.lang.reflect.Modifier;

/**
 * Scheme for instantiating and inspecting {@link TaskLifecycleListener} classes.
 *
 * <p>Listeners support two property annotations: {@code @ServiceReference}, and {@code @Inject} for
 * {@link #INJECTABLE_SERVICES a curated set of services}. Only abstract getters need an annotation,
 * see {@link #MISSING_ANNOTATION_HANDLER}.</p>
 */
@ServiceScope(Scope.Global.class)
public class TaskLifecycleListenerScheme extends AbstractTypeScheme {

    /**
     * The services that listeners can inject.
     */
    public static final ImmutableList<Class<?>> INJECTABLE_SERVICES = ImmutableList.of(
        ProviderFactory.class,
        FileSystemOperations.class,
        ArchiveOperations.class,
        ExecOperations.class,
        ProjectLayout.class,
        Problems.class
    );

    /**
     * Reports abstract getters without {@code @ServiceReference} or {@code @Inject}: nothing can configure a listener,
     * so Gradle could only implement them with an empty value. A concrete getter is just a method of the listener.
     *
     * <p>Abstract base listeners are reported too, so a base listener leaves a value to its subclasses
     * with an abstract method that is not a getter.</p>
     */
    public static final MissingPropertyAnnotationHandler MISSING_ANNOTATION_HANDLER = (context, property, displayName) -> {
        if (!Modifier.isAbstract(property.getMethod().getModifiers())) {
            return;
        }
        context.visitPropertyError(problem -> problem
            .forProperty(property.getPropertyName())
            .id(problem.getGradleGroup().getPluginValidation().problemId("Missing annotation"))
            .contextualLabel("is abstract but is not annotated with @ServiceReference or @Inject")
            .details("Nothing can set the value of a lifecycle listener property, so Gradle only implements abstract getters that reference a build service or inject a service")
            .solution("Annotate it with @ServiceReference or @Inject")
            .solution("Implement the getter, or keep the value in a field")
            .solution(String.format("In a base listener, use an abstract method that is not a getter, such as %s()", property.getPropertyName()))
        );
    };

    public TaskLifecycleListenerScheme(InstantiationScheme instantiationScheme, InspectionScheme inspectionScheme) {
        super(instantiationScheme, inspectionScheme);
    }

    @Override
    public boolean appliesTo(Class<?> type) {
        return TaskLifecycleListener.class.isAssignableFrom(type);
    }

    /**
     * Returns the instantiation scheme for the listeners of a project, which only exposes the {@link #INJECTABLE_SERVICES} of that project.
     */
    public InstantiationScheme withProjectServices(ServiceRegistry projectServices) {
        ServiceRegistry services = ServiceRegistryBuilder.builder()
            .displayName("task lifecycle listener services")
            .provider(registration -> {
                for (Class<?> serviceType : INJECTABLE_SERVICES) {
                    addProjectService(registration, projectServices, serviceType);
                }
                // Used by the generated listener classes, for @ServiceReference and managed properties
                addProjectService(registration, projectServices, BuildServiceRegistry.class);
                addProjectService(registration, projectServices, ManagedObjectRegistry.class);
            })
            .build();
        return getInstantiationScheme().withServices(services);
    }

    private static <T> void addProjectService(ServiceRegistration registration, ServiceRegistry projectServices, Class<T> serviceType) {
        registration.add(serviceType, projectServices.get(serviceType));
    }
}
