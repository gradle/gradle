/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.nativeplatform.internal.services;

import org.gradle.api.reporting.components.internal.AbstractBinaryRenderer;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;
import org.gradle.nativeplatform.internal.NativeBinaryRenderer;
import org.gradle.nativeplatform.internal.NativeExecutableBinaryRenderer;
import org.gradle.nativeplatform.internal.SharedLibraryBinaryRenderer;
import org.gradle.nativeplatform.internal.StaticLibraryBinaryRenderer;
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolverServices;

public class PluginsModelNativeServices extends AbstractGradleModuleServices {
    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.add(AbstractBinaryRenderer.class, NativeBinaryRenderer.class);
        registration.add(AbstractBinaryRenderer.class, SharedLibraryBinaryRenderer.class);
        registration.add(AbstractBinaryRenderer.class, StaticLibraryBinaryRenderer.class);
        registration.add(AbstractBinaryRenderer.class, NativeExecutableBinaryRenderer.class);
    }

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new NativeDependencyResolverServices());
    }
}
