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
 * <p>
 * Implementations are discovered using the JAR service locator mechanism (see {@link org.gradle.internal.service.ServiceLocator}).
 *
 * @see Scope
 */
@ServiceScope(Scope.Global.class)
public interface GradleModuleServices extends ServiceRegistrationProvider {

    /**
     * Called to register services in the {@link Scope.Global Global} scope.
     *
     * @see Scope
     * @see Scope.Global
     */
    void registerGlobalServices(ServiceRegistration registration);

    /**
     * Called to register services in the {@link Scope.UserHome UserHome} scope.
     *
     * @see Scope
     * @see Scope.UserHome
     */
    void registerGradleUserHomeServices(ServiceRegistration registration);

    /**
     * Called to register services in the {@link Scope.CrossBuildSession CrossBuildSession} scope.
     *
     * @see Scope
     * @see Scope.CrossBuildSession
     */
    void registerCrossBuildSessionServices(ServiceRegistration registration);

    /**
     * Called to register services in the {@link Scope.BuildSession BuildSession} scope.
     *
     * @see Scope
     * @see Scope.BuildSession
     */
    void registerBuildSessionServices(ServiceRegistration registration);

    /**
     * Called to register services in the {@link Scope.BuildTree BuildTree} scope.
     *
     * @see Scope
     * @see Scope.BuildTree
     */
    void registerBuildTreeServices(ServiceRegistration registration);

    /**
     * Called to register services in the {@link Scope.Build Build} scope.
     *
     * @see Scope
     * @see Scope.Build
     */
    void registerBuildServices(ServiceRegistration registration);

    /**
     * Called to register services in the {@link Scope.Settings Settings} scope.
     *
     * @see Scope
     * @see Scope.Settings
     */
    void registerSettingsServices(ServiceRegistration registration);

    /**
     * Called to register services in the {@link Scope.Project Project} scope.
     *
     * @see Scope
     * @see Scope.Project
     */
    void registerProjectServices(ServiceRegistration registration);

}
