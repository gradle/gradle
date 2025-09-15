/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.plugins.DefaultPluginManager;
import org.gradle.api.internal.plugins.ImperativeOnlyPluginTarget;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.api.internal.plugins.PluginTarget;
import org.gradle.api.internal.plugins.PluginTargetType;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.ServiceRegistry;

/**
 * Contains the services for a given {@link GradleInternal} instance.
 */
public class GradleScopeServices implements ServiceRegistrationProvider {

    @Provides
    PluginRegistry createPluginRegistry(PluginRegistry parentRegistry, GradleInternal gradleInternal) {
        return parentRegistry.createChild(gradleInternal.getClassLoaderScope());
    }

    @Provides
    PluginManagerInternal createPluginManager(
        ServiceRegistry gradleScopeServiceRegistry,
        Instantiator instantiator,
        GradleInternal gradleInternal,
        PluginRegistry pluginRegistry,
        InstantiatorFactory instantiatorFactory,
        BuildOperationRunner buildOperationRunner,
        UserCodeApplicationContext userCodeApplicationContext,
        CollectionCallbackActionDecorator decorator,
        DomainObjectCollectionFactory domainObjectCollectionFactory,
        InternalProblems problems
    ) {
        PluginTarget target = new ImperativeOnlyPluginTarget<>(PluginTargetType.GRADLE, gradleInternal, problems);
        return instantiator.newInstance(DefaultPluginManager.class, pluginRegistry, instantiatorFactory.inject(gradleScopeServiceRegistry), target, buildOperationRunner, userCodeApplicationContext, decorator, domainObjectCollectionFactory);
    }
}
