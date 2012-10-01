/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolutionResultBuilder;
import org.gradle.api.internal.artifacts.result.DefaultResolutionResult;

/**
 * by Szczepan Faber, created at: 9/10/12
 */
public class EmptyResolutionResultConfigurer implements ArtifactDependencyResolver {
    private final ArtifactDependencyResolver delegate;

    public EmptyResolutionResultConfigurer(ArtifactDependencyResolver delegate) {
        this.delegate = delegate;
    }

    public ResolvedConfiguration resolve(ConfigurationInternal configuration) throws ResolveException {
        ModuleVersionIdentifier id = DefaultModuleVersionIdentifier.newId(configuration.getModule());
        DefaultResolutionResult emptyResult = new ResolutionResultBuilder().start(id).getResult();
        configuration.setResolutionResult(emptyResult);
        return delegate.resolve(configuration);
    }
}
