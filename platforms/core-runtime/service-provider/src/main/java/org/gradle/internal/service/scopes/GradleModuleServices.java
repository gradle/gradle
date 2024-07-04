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
import org.gradle.internal.service.ServiceRegistrationProvider;

/**
 * Can be implemented by Gradle modules to provide services in various scopes.
 *
 * <p>Implementations are discovered using the JAR service locator mechanism (see {@link org.gradle.internal.service.ServiceLocator}).
 */
@ServiceScope(Scope.Global.class)
public interface GradleModuleServices extends ServiceRegistrationProvider {

    /**
     * @see Scope.Global
     */
    void registerGlobalServices(ServiceRegistration registration);

    /**
     * @see Scope.UserHome
     */
    void registerGradleUserHomeServices(ServiceRegistration registration);

    /**
     * @see Scope.CrossBuildSession
     */
    void registerCrossBuildSessionServices(ServiceRegistration registration);

    /**
     * @see Scope.BuildSession
     */
    void registerBuildSessionServices(ServiceRegistration registration);

    /**
     * @see Scope.BuildTree
     */
    void registerBuildTreeServices(ServiceRegistration registration);

    /**
     * @see Scope.Build
     */
    void registerBuildServices(ServiceRegistration registration);

    /**
     * Called once per build invocation on a build, to register any {@link org.gradle.api.initialization.Settings} scoped services. These services are closed at the end of the build invocation.
     *
     * <p>Global, user home, build session, build tree and build scoped services are visible to the settings scope services, but not vice versa.</p>
     */
    void registerSettingsServices(ServiceRegistration registration);

    /**
     * @see Scope.Project
     */
    void registerProjectServices(ServiceRegistration registration);

}
