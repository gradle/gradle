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
package org.gradle.ide.visualstudio.internal;

import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.ide.visualstudio.VisualStudioProject;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.plugins.ide.internal.IdeArtifactRegistry;

public class DefaultVisualStudioExtension implements VisualStudioExtensionInternal {
    private final VisualStudioProjectRegistry projectRegistry;

    public DefaultVisualStudioExtension(Instantiator instantiator, FileResolver fileResolver, IdeArtifactRegistry ideArtifactRegistry, CollectionCallbackActionDecorator collectionCallbackActionDecorator, ObjectFactory objectFactory, ProviderFactory providerFactory) {
        this.projectRegistry = new VisualStudioProjectRegistry(fileResolver, instantiator, ideArtifactRegistry, collectionCallbackActionDecorator, objectFactory, providerFactory);
    }

    @Override
    public NamedDomainObjectSet<? extends VisualStudioProject> getProjects() {
        return projectRegistry;
    }

    @Override
    public VisualStudioProjectRegistry getProjectRegistry() {
        return projectRegistry;
    }
}
