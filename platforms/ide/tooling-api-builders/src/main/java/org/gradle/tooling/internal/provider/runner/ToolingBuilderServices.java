/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.internal.build.event.BuildEventListenerFactory;
import org.gradle.internal.buildtree.BuildActionRunner;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ToolingBuilderServices extends AbstractGradleModuleServices {
    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.add(BuildEventListenerFactory.class, ToolingApiBuildEventListenerFactory.class);
//        registration.add(IsolatableSerializerRegistry.class);
        registration.addProvider(new MyServiceRegistrationProvider());
    }


    public static class MyServiceRegistrationProvider implements ServiceRegistrationProvider {
        @Provides
        ClassLoaderHierarchyHasher createClassLoaderHierarchyHasher() {
            // Return a dummy implementation of this as creating a real hasher drags ~20 more services
            // along with it, and a hasher isn't actually needed on the worker process side at the moment.
            return new ClassLoaderHierarchyHasher() {
                @Nullable
                @Override
                public HashCode getClassLoaderHash(@Nonnull ClassLoader classLoader) {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }

    @Override
    public void registerBuildTreeServices(ServiceRegistration registration) {
        registration.add(BuildControllerFactory.class);
        registration.add(BuildActionRunner.class, BuildModelActionRunner.class);
        registration.add(BuildActionRunner.class, TestExecutionRequestActionRunner.class);
        registration.add(BuildActionRunner.class, ClientProvidedBuildActionRunner.class);
        registration.add(BuildActionRunner.class, ClientProvidedPhasedActionRunner.class);
    }
}
