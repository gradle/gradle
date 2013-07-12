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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result;

import org.gradle.api.Action;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.result.ResolutionResult;

import java.util.Collection;

/**
 * By Szczepan Faber on 7/12/13
 */
public class DefaultResolutionResultBuilder implements ResolvedConfigurationListener {

    private final ResolvedConfigurationListener delegate;

    public DefaultResolutionResultBuilder(Collection<Action<? super ResolutionResult>> resolutionResultActions) {
        if (resolutionResultActions.isEmpty()) {
            delegate = new NoOpResolvedConfigurationListener();
        } else {
            delegate = new ResolutionResultBuilder(resolutionResultActions);
        }
    }

    public ResolvedConfigurationListener start(ModuleVersionIdentifier root) {
        return delegate.start(root);
    }

    public void resolvedModuleVersion(ModuleVersionSelection moduleVersion) {
        delegate.resolvedModuleVersion(moduleVersion);
    }

    public void resolvedConfiguration(ModuleVersionIdentifier id, Collection<? extends InternalDependencyResult> dependencies) {
        delegate.resolvedConfiguration(id, dependencies);
    }

    public void resolutionCompleted() {
        delegate.resolutionCompleted();
    }
}
