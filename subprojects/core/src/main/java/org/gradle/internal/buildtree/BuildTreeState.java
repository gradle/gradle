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
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.Closeable;
import java.util.function.Function;

/**
 * Encapsulates the state for a particular build tree.
 */
@ServiceScope(Scopes.BuildTree.class)
public class BuildTreeState implements Closeable {
    private final ServiceRegistry services;
    private final DefaultBuildTreeContext context;

    public BuildTreeState(ServiceRegistry parent, BuildTreeModelControllerServices.Supplier modelServices) {
        services = ServiceRegistryBuilder.builder()
            .displayName("build tree services")
            .parent(parent)
            .provider(new BuildTreeScopeServices(this, modelServices))
            .build();
        context = new DefaultBuildTreeContext(services);
    }

    public ServiceRegistry getServices() {
        return services;
    }

    /**
     * Runs the given action against the state of this build tree.
     */
    public <T> T run(Function<? super BuildTreeContext, T> action) {
        return action.apply(context);
    }

    @Override
    public void close() {
        CompositeStoppable.stoppable(services).stop();
    }
}
