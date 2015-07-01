/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import com.google.common.collect.Lists;
import org.gradle.api.Nullable;
import org.gradle.api.internal.artifacts.ResolveContext;
import org.gradle.internal.reflect.Instantiator;

public class RequestScopeResolverProviderFactory {
    private final Instantiator instantiator;

    public RequestScopeResolverProviderFactory(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    @Nullable
    public ResolverProvider tryCreate(ResolveContext resolveContext, Query query) {
        if (query.canCreateFrom(resolveContext)) {
            Object[] parameters = query.constructorArgs == null ? new Object[]{resolveContext} : Lists.asList(resolveContext, query.constructorArgs).toArray();
            return instantiator.newInstance(query.providerClass, parameters);
        }
        return null;
    }

    /**
     * We use an indirection level here because providers are going to be generated from different modules, hence the constructor arguments are injected through the Gradle DI system.
     */
    public static class Query {
        private final Class<? extends ResolverProvider> providerClass;
        private final Object[] constructorArgs;

        public Query(Class<? extends ResolverProvider> providerClass, Object... constructorArgs) {
            this.providerClass = providerClass;
            this.constructorArgs = constructorArgs;
        }

        public boolean canCreateFrom(ResolveContext context) {
            return true;
        }
    }
}
