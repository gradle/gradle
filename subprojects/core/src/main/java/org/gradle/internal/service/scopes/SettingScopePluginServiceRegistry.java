/*
 * Copyright 2017 the original author or authors.
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
 * Can be used by a plugin to register services available to the {@link org.gradle.api.initialization.Settings} object.
 */
public interface SettingScopePluginServiceRegistry {

    /**
     * Called once per build, to register any {@link org.gradle.api.initialization.Settings} scoped services. These services are closed at the end of the build.
     *
     * <p>Global, shared, build session and build scoped services are visible to the settings scope services, but not vice versa.</p>
     */
    void registerSettingsServices(ServiceRegistration registration);
}
