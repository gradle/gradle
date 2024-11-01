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

import javax.inject.Inject;
import java.util.List;

/**
 * Injecting parameterized types, like {@code List<ResolverProviderFactory>} is very expensive for the
 * ServiceRegistry, since it needs to look up and parse the generic signature of a method or constructor,
 * which is a string, and then resolve the types.
 * <p>
 * When injecting generic parameters into constructors or method with very long signatures, this parsing
 * and loading step can take a non-negligible amount of time. This type acts as a proxy to loading a list
 * of resolver provider factories. Injecting this class is much cheaper, as the service registry only needs
 * to parse this short constructor, and the type that this class is injected into can remain generic-free.
 */
public class ResolverProviderFactories {

    private final List<ResolverProviderFactory> factories;

    @Inject
    public ResolverProviderFactories(List<ResolverProviderFactory> factories) {
        this.factories = factories;
    }

    public List<ResolverProviderFactory> getFactories() {
        return factories;
    }

}
