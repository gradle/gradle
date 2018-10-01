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

package org.gradle.api.internal.artifacts;

import org.gradle.api.internal.artifacts.transform.ArtifactTransformListener;
import org.gradle.api.internal.artifacts.transform.DefaultTransformNodeFactory;
import org.gradle.api.internal.artifacts.transform.TransformNodeDependencyResolver;
import org.gradle.api.internal.artifacts.transform.TransformNodeExecutor;
import org.gradle.api.internal.artifacts.transform.TransformNodeFactory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;

public class DependencyServices extends AbstractPluginServiceRegistry {
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.addProvider(new DependencyManagementGlobalScopeServices());
    }

    @Override
    public void registerGradleUserHomeServices(ServiceRegistration registration) {
        registration.addProvider(new DependencyManagementGradleUserHomeScopeServices());
    }

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new DependencyManagementBuildScopeServices());
    }

    @Override
    public void registerBuildTreeServices(ServiceRegistration registration) {
        registration.addProvider(new DependencyManagementBuildTreeScopeServices());
    }

    @Override
    public void registerGradleServices(ServiceRegistration registration) {
        registration.addProvider(new DependencyManagementGradleServices());
    }

    private static class DependencyManagementGradleServices {
        ArtifactTransformListener createArtifactTransformListener(ListenerManager listenerManager) {
            return listenerManager.getBroadcaster(ArtifactTransformListener.class);
        }

        TransformNodeFactory createTransformNodeFactory() {
            return new DefaultTransformNodeFactory();
        }

        TransformNodeDependencyResolver createTransformNodeDepndencyResolver(TransformNodeFactory transformNodeFactory) {
            return new TransformNodeDependencyResolver(transformNodeFactory);
        }

        TransformNodeExecutor createTransformNodeExecutor(BuildOperationExecutor buildOperationExecutor, ArtifactTransformListener transformListener) {
            return new TransformNodeExecutor(buildOperationExecutor, transformListener);
        }
    }
}
