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
 *
 */
public interface PluginServiceRegistry {
    /**
     * Called once per process, to register any globally scoped services. These services are reused across builds in the same process.
     * The services are closed when the process finishes.
     *
     * <p>Global services are visible to all other services.</p>
     */
    void registerGlobalServices(ServiceRegistration registration);

    /**
     * Called to register any services scoped to the Gradle user home directory. These services are reused across builds in the same process while the Gradle user home directory remains unchanged. The services are closed when the Gradle user home directory changes.
     *
     * <p>These services are "mostly global" as there is usually only a single Gradle user home directory used for a given process. Some processes, such as test processes, may run builds with different user home directories.</p>
     *
     * <p>Global services are visible to these shared services, but not vice versa.</p>
     */
    void registerGradleUserHomeServices(ServiceRegistration registration);

    /**
     * Called once per build session to register any build session scoped services.  These services are reused across build invocations when in
     * continuous mode. They are closed at the end of the build session.
     *
     * <p>Global and shared services are visible to build session scope services, but not vice versa</p>
     */
    void registerBuildSessionServices(ServiceRegistration registration);

    /**
     * Called once per build invocation on a build tree to register any build tree scoped services to use during that build invocation.  These services are recreated when in continuous mode and shared across all nested builds. They are closed when the build invocation is completed.
     *
     * <p>Global, user home and build session services are visible to build tree scope services, but not vice versa.</p>
     */
    void registerBuildTreeServices(ServiceRegistration registration);

    /**
     * Called once per build invocation on a build, to register any build scoped services to use during that build invocation. These services are closed at the end of the build invocation.
     *
     * <p>Global, user home, build session and build tree services are visible to the build scope services, but not vice versa.</p>
     */
    void registerBuildServices(ServiceRegistration registration);

    /**
     * Called once per build invocation on a build, to register any {@link org.gradle.api.initialization.Settings} scoped services. These services are closed at the end of the build invocation.
     *
     * <p>Global, user home, build session, build tree and build scoped services are visible to the settings scope services, but not vice versa.</p>
     */
    void registerSettingsServices(ServiceRegistration registration);

    /**
     * Called once per build invocation on a build, to register any {@link org.gradle.api.invocation.Gradle} scoped services. These services are closed at the end of the build invocation.
     *
     * <p>Global, user home, build session, build tree and and build scoped services are visible to the gradle scope services, but not vice versa.</p>
     */
    void registerGradleServices(ServiceRegistration registration);

    /**
     * Called once per project per build invocation, to register any project scoped services. These services are closed at the end of the build invocation.
     *
     * <p>Global, user home, build session, build tree, build and gradle scoped services are visible to the project scope services, but not vice versa.</p>
     */
    void registerProjectServices(ServiceRegistration registration);

}
