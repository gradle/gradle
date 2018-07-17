/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.service.scopes;

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.service.ServiceRegistry;

public class GradleScopeServiceRegistryFactory implements ServiceRegistryFactory, Stoppable {
    private final ServiceRegistry services;
    private final Factory<LoggingManagerInternal> loggingManagerInternalFactory;
    private final CompositeStoppable registries = new CompositeStoppable();

    public GradleScopeServiceRegistryFactory(ServiceRegistry services, Factory<LoggingManagerInternal> loggingManagerInternalFactory) {
        this.services = services;
        this.loggingManagerInternalFactory = loggingManagerInternalFactory;
    }

    @Override
    public ServiceRegistry createFor(Object domainObject) {
        if (domainObject instanceof ProjectInternal) {
            ProjectScopeServices projectScopeServices = new ProjectScopeServices(services, (ProjectInternal) domainObject, loggingManagerInternalFactory);
            registries.add(projectScopeServices);
            return projectScopeServices;
        }
        throw new IllegalArgumentException(String.format("Cannot create services for unknown domain object of type %s.",
            domainObject.getClass().getSimpleName()));
    }

    @Override
    public void stop() {
        registries.stop();
    }
}
