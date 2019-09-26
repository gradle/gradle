/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.workers.internal;

import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.process.ExecOperations;

public class WorkerPublicServicesBuilder {
    private final ServiceRegistry internalServices;
    private boolean internalServicesRequired;

    WorkerPublicServicesBuilder(ServiceRegistry internalServices) {
        this.internalServices = internalServices;
    }

    WorkerPublicServicesBuilder withInternalServicesVisible(boolean canUseInternalServices) {
        this.internalServicesRequired = canUseInternalServices;
        return this;
    }

    DefaultServiceRegistry build() {
        if (internalServicesRequired) {
            return new DefaultServiceRegistry("unit of work services (internal)", internalServices);
        } else {
            DefaultServiceRegistry services = new DefaultServiceRegistry("unit of work services");
            services.add(ObjectFactory.class, internalServices.get(ObjectFactory.class));
            services.add(FileSystemOperations.class, internalServices.get(FileSystemOperations.class));
            services.add(ExecOperations.class, internalServices.get(ExecOperations.class));
            services.add(ProjectLayout.class, internalServices.get(ProjectLayout.class));
            services.add(ProviderFactory.class, internalServices.get(ProviderFactory.class));
            return services;
        }
    }
}
