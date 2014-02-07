/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.service.ServiceRegistry;

class BuildScopeServiceRegistryFactory implements ServiceRegistryFactory {
    private final ServiceRegistry services;
    private final CompositeStoppable registries = new CompositeStoppable();

    public BuildScopeServiceRegistryFactory(ServiceRegistry services) {
        this.services = services;
    }

    public ServiceRegistry createFor(Object domainObject) {
        if (domainObject instanceof GradleInternal) {
            GradleScopeServices gradleServices = new GradleScopeServices(services, (GradleInternal) domainObject);
            registries.add(gradleServices);
            return gradleServices;
        }
        if (domainObject instanceof SettingsInternal) {
            SettingsScopeServices settingsServices = new SettingsScopeServices(services, (SettingsInternal) domainObject);
            registries.add(settingsServices);
            return settingsServices;
        }
        throw new IllegalArgumentException(String.format("Cannot create services for unknown domain object of type %s.",
                domainObject.getClass().getSimpleName()));
    }

    public void close() {
        registries.stop();
    }
}
