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

import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetadata;
import org.gradle.api.internal.artifacts.transform.ArtifactTransformListener;
import org.gradle.api.internal.artifacts.transform.CachingTransformerExecutor;
import org.gradle.api.internal.artifacts.transform.DefaultCachingTransformerExecutor;
import org.gradle.api.internal.artifacts.transform.DefaultTransformationNodeFactory;
import org.gradle.api.internal.artifacts.transform.TransformationNodeDependencyResolver;
import org.gradle.api.internal.artifacts.transform.TransformationNodeExecutor;
import org.gradle.api.internal.artifacts.transform.TransformationNodeFactory;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.execution.WorkExecutor;
import org.gradle.internal.execution.impl.steps.UpToDateResult;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.resource.local.FileAccessTimeJournal;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.internal.snapshot.FileSystemSnapshotter;

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

        TransformationNodeFactory createTransformationNodeFactory() {
            return new DefaultTransformationNodeFactory();
        }

        TransformationNodeDependencyResolver createTransformationNodeDependencyResolver(TransformationNodeFactory transformationNodeFactory) {
            return new TransformationNodeDependencyResolver(transformationNodeFactory);
        }

        TransformationNodeExecutor createTransformationNodeExecutor(BuildOperationExecutor buildOperationExecutor, ArtifactTransformListener transformListener) {
            return new TransformationNodeExecutor(buildOperationExecutor, transformListener);
        }

        CachingTransformerExecutor createCachingTransformerExecuter(WorkExecutor<UpToDateResult> workExecutor, ArtifactCacheMetadata artifactCacheMetadata, CacheRepository cacheRepository, InMemoryCacheDecoratorFactory cacheDecoratorFactory,
                                                                    FileSystemSnapshotter fileSystemSnapshotter, ListenerManager listenerManager, FileAccessTimeJournal fileAccessTimeJournal, ArtifactTransformListener artifactTransformListener) {
            DefaultCachingTransformerExecutor transformedFileCache = new DefaultCachingTransformerExecutor(workExecutor, artifactCacheMetadata, cacheRepository, cacheDecoratorFactory, fileSystemSnapshotter, fileAccessTimeJournal, artifactTransformListener);
            listenerManager.addListener(transformedFileCache);
            return transformedFileCache;
        }
    }
}
