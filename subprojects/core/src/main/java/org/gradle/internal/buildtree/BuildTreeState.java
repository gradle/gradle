/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.buildtree;

import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.scopeids.id.BuildInvocationScopeId;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.Closeable;

/**
 * Encapsulates the state for a particular build tree.
 */
@ServiceScope(Scope.BuildTree.class)
public class BuildTreeState implements Closeable {
    private final ServiceRegistry services;

    public BuildTreeState(
        ServiceRegistry buildSessionServices,
        BuildActionModelRequirements buildActionRequirements,
        BuildModelParameters buildModelParameters,
        BuildInvocationScopeId buildInvocationScopeId
    ) {
        services = ServiceRegistryBuilder.builder()
            .scopeStrictly(Scope.BuildTree.class)
            .displayName("build tree services")
            .parent(buildSessionServices)
            .provider(new BuildTreeScopeServices(buildActionRequirements, buildModelParameters, buildInvocationScopeId, this))
            .build();
    }

    public ServiceRegistry getServices() {
        return services;
    }

    @Override
    public void close() {
        CompositeStoppable.stoppable(services).stop();
    }
}
