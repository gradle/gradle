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

package org.gradle.api.internal.services;

import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.services.BuiltInServices;
import org.gradle.api.services.InvalidServiceLookupException;
import org.gradle.api.services.UnknownServiceException;
import org.gradle.internal.service.ServiceRegistry;
import org.jspecify.annotations.Nullable;

/**
 * Default implementation of {@link BuiltInServices}: the generic {@code get}/{@code find} escape hatch
 * over a scope's {@link ServiceRegistry}, gated to public Gradle services only.
 */
public class DefaultBuiltInServices implements BuiltInServices {

    private final ServiceRegistry services;

    public DefaultBuiltInServices(ServiceRegistry services) {
        this.services = services;
    }

    @Override
    public <T> T get(Class<T> serviceType) {
        assertPublicService(serviceType);
        try {
            return services.get(serviceType);
        } catch (org.gradle.internal.service.UnknownServiceException e) {
            throw notAvailableInScope(serviceType, e);
        }
    }

    @Override
    public <T> @Nullable T find(Class<T> serviceType) {
        assertPublicService(serviceType);
        // ServiceRegistry has no typed find(Class), only find(Type) -> Object, so a typed cast is required here.
        Object service = services.find(serviceType);
        return service == null ? null : serviceType.cast(service);
    }

    // SPIKE: provisional gate. The production rule is PublicApi package membership
    // (e.g. via GradleApiSpecProvider or the shipped gradle-api-declaration.properties).
    private void assertPublicService(Class<?> serviceType) {
        String typeName = serviceType.getName();
        if (!typeName.startsWith("org.gradle.internal.") && !typeName.contains(".internal.")) {
            return;
        }
        throw report(
            new InvalidServiceLookupException("Cannot access '" + typeName + "': only public Gradle services are available."),
            "non-public-service", "Accessing a non-public Gradle service",
            "'" + typeName + "' is not a public Gradle service",
            "Only public Gradle services can be accessed through the built-in services.",
            "Request a public Gradle service (a type in the org.gradle.* public API, not org.gradle.internal.*).");
    }

    private RuntimeException notAvailableInScope(Class<?> serviceType, Throwable cause) {
        return report(
            new UnknownServiceException("Service '" + serviceType.getName() + "' is not available in this scope.", cause),
            "service-not-available", "Service not available in this scope",
            "'" + serviceType.getName() + "' is not available in this scope",
            "The requested public service is not registered in the current scope.",
            "Request the service from a scope where it is available, or inject it into a task.");
    }

    private RuntimeException report(
        InvalidServiceLookupException failure, String id, String displayName, String label, String details, String solution
    ) {
        ProblemId problemId = ProblemId.create(id, displayName, GradleCoreProblemGroup.services());
        return services.get(Problems.class).getReporter().throwing(failure, problemId, spec -> spec
            .contextualLabel(label)
            .details(details)
            .solution(solution));
    }
}
