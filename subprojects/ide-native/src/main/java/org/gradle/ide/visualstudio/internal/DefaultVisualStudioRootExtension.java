/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.ide.visualstudio.VisualStudioRootExtension;
import org.gradle.ide.visualstudio.VisualStudioSolution;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.plugins.ide.internal.IdeArtifactRegistry;

public class DefaultVisualStudioRootExtension extends DefaultVisualStudioExtension implements VisualStudioRootExtension, VisualStudioExtensionInternal {
    private final VisualStudioSolution solution;

    public DefaultVisualStudioRootExtension(String projectName, Instantiator instantiator, ObjectFactory objectFactory, FileResolver fileResolver, IdeArtifactRegistry ideArtifactRegistry, CollectionCallbackActionDecorator collectionCallbackActionDecorator, ProviderFactory providerFactory) {
        super(instantiator, fileResolver, ideArtifactRegistry, collectionCallbackActionDecorator, objectFactory, providerFactory);
        this.solution = objectFactory.newInstance(DefaultVisualStudioSolution.class, projectName);
    }

    @Override
    public VisualStudioSolution getSolution() {
        return solution;
    }

    @Override
    public void solution(Action<? super VisualStudioSolution> configAction) {
        configAction.execute(solution);
    }
}
