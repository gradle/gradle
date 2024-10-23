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

import org.gradle.internal.service.ServiceRegistry;

import javax.annotation.concurrent.ThreadSafe;
import java.io.File;
import java.util.Optional;

/**
 * Manages the shared services that are scoped to a particular Gradle user home dir and which can be shared by multiple build invocations. These shared services also include the global services.
 *
 * <p>A plugin can contribute shared services to this scope by providing an implementation of {@link GradleModuleServices}.
 */
@ThreadSafe
@ServiceScope(Scope.Global.class)
public interface GradleUserHomeScopeServiceRegistry {
    /**
     * Locates the shared services to use for the given Gradle user home dir. The returned registry also includes global services.
     *
     * <p>The caller is responsible for releasing the registry when it is finished using it by calling {@link #release(ServiceRegistry)}.</p>
     */
    ServiceRegistry getServicesFor(File gradleUserHomeDir);

    /**
     * Locates the shared services from the last used user home dir - if any.
     *
     * The method will never create services, so the caller should not release the registry.
     */
    Optional<ServiceRegistry> getCurrentServices();

    /**
     * Releases a service registry created by {@link #getServicesFor(File)}.
     */
    void release(ServiceRegistry services);
}
