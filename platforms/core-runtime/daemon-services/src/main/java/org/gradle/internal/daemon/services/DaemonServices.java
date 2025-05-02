/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.daemon.services;

import org.gradle.TaskExecutionRequest;
import org.gradle.api.internal.initialization.loadercache.ModelClassLoaderFactory;
import org.gradle.api.internal.tasks.userinput.DefaultUserInputReader;
import org.gradle.api.internal.tasks.userinput.UserInputReader;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classpath.CachedClasspathTransformer;
import org.gradle.internal.daemon.serialization.DaemonSidePayloadClassLoaderFactory;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;
import org.gradle.tooling.internal.provider.serialization.ClassLoaderCache;
import org.gradle.tooling.internal.provider.serialization.DefaultPayloadClassLoaderRegistry;
import org.gradle.tooling.internal.provider.serialization.PayloadClassLoaderFactory;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.gradle.tooling.internal.provider.serialization.WellKnownClassLoaderRegistry;

public class DaemonServices extends AbstractGradleModuleServices {
    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.add(UserInputReader.class, DefaultUserInputReader.class);
        registration.add(ClassLoaderCache.class, ClassLoaderCache.class);
    }

    @Override
    public void registerGradleUserHomeServices(ServiceRegistration registration) {
        registration.addProvider(new DaemonGradleUserHomeServices());
    }

    private static class DaemonGradleUserHomeServices implements ServiceRegistrationProvider {
        @Provides
        PayloadClassLoaderFactory createClassLoaderFactory(CachedClasspathTransformer cachedClasspathTransformer) {

            ClassLoader parent = this.getClass().getClassLoader();
            FilteringClassLoader.Spec filterSpec = new FilteringClassLoader.Spec();
            filterSpec.allowPackage("org.gradle.tooling.internal.protocol");
            filterSpec.allowClass(TaskExecutionRequest.class);
            FilteringClassLoader modelClassLoader = new FilteringClassLoader(parent, filterSpec);

            return new DaemonSidePayloadClassLoaderFactory(
                new ModelClassLoaderFactory(modelClassLoader),
                cachedClasspathTransformer
            );
        }

        @Provides
        PayloadSerializer createPayloadSerializer(ClassLoaderCache classLoaderCache, PayloadClassLoaderFactory classLoaderFactory) {
            return new PayloadSerializer(
                new WellKnownClassLoaderRegistry(
                    new DefaultPayloadClassLoaderRegistry(
                        classLoaderCache,
                        classLoaderFactory))
            );
        }
    }
}
