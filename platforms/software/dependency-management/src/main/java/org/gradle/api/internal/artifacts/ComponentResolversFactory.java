/*
 * Copyright 2007-2009 the original author or authors.
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

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;

import java.util.List;

/**
 * Factory for {@link ComponentResolvers} instances scoped to a given resolve context and a set of repositories.
 */
public interface ComponentResolversFactory {

    /**
     * Create a set of resolvers that resolves components for the provided {@link ResolveContext}
     * using the provided repositories.
     */
    ComponentResolvers create(
        ResolveContext resolveContext,
        List<? extends ResolutionAwareRepository> repositories,
        AttributesSchemaInternal consumerSchema
    );

}
