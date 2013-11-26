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

import org.gradle.internal.service.ServiceRegistration;

/**
 * Can be implemented by plugins to provide services in various scopes.
 *
 * <p>Implementations are discovered using the JAR service locator mechanism (see {@link org.gradle.internal.service.ServiceLocator}).
 */
public interface PluginServiceRegistry {
    /**
     * Called once per process, to register any globally scoped services. These services are reused across builds in the same process.
     */
    void registerGlobalServices(ServiceRegistration registration);

    /**
     * Called once per build, to registry any build scoped services. These services are discarded at the end of the current build.
     * All global scoped services are visible to the build scope services, but not vice versa.
     */
    void registerBuildServices(ServiceRegistration registration);

    /**
     * Called once per project per build, to registry any project scoped services. These services are discarded at the end of the current build.
     * All global and build scoped services are visible to the project scope services, but not vice versa.
     */
    void registerProjectServices(ServiceRegistration registration);
}
