/*
 * Copyright 2016 the original author or authors.
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
 * Can be implemented by a plugin to provide shared service scoped to the Gradle user home directory.
 *
 * <p>Implementations must also implement {@link PluginServiceRegistry}.
 */
public interface GradleUserHomeScopePluginServices {
    /**
     * Called to register any services scoped to the Gradle user home directory. These services are reused across builds in the same process while the Gradle user home directory remains unchanged. The services are closed when the Gradle user home directory changes.
     *
     * <p>These services are "mostly global" as there is usually only a single Gradle user home directory used for a given process. Some processes, such as test processes, may run builds with different user home directories.</p>
     *
     * <p>Global services are visible to these shared services, but not vice versa.</p>
     */
    void registerGradleUserHomeServices(ServiceRegistration registration);
}
