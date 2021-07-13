/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Resolver;
import org.gradle.api.resolvers.ResolverSpec;

import java.io.File;
import java.util.Set;

public class DefaultResolver implements Resolver {

    private final Configuration configuration;
    private final ResolverSpec resolverSpec;

    public DefaultResolver(Configuration configuration, ResolverSpec resolverSpec) {
        this.configuration = configuration;
        this.resolverSpec = resolverSpec;

        this.configuration.setVisible(false);
        this.configuration.setCanBeConsumed(false);
        this.configuration.setCanBeResolved(true);
        this.configuration.extendsFrom(resolverSpec.getFrom());
        this.configuration.attributes(resolverSpec.getAttributes());
    }

    @Override
    public Set<File> resolve() {
        if (resolverSpec.isIgnoreMissing()) {
            return configuration.getIncoming().artifactView(a -> a.lenient(true)).getFiles().getFiles();
        }
        return configuration.resolve();
    }
}
