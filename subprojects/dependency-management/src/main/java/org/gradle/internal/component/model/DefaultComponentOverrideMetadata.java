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

package org.gradle.internal.component.model;

import com.google.common.collect.Sets;
import org.gradle.api.artifacts.ClientModule;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.internal.component.local.model.DslOriginDependencyMetadata;

import java.util.Collections;
import java.util.Set;

public class DefaultComponentOverrideMetadata implements ComponentOverrideMetadata {
    private final boolean changing;
    private final Set<IvyArtifactName> artifacts;
    private final ClientModule clientModule;

    public static ComponentOverrideMetadata forDependency(DependencyMetadata dependencyMetadata) {
        return new DefaultComponentOverrideMetadata(dependencyMetadata.isChanging(), dependencyMetadata.getArtifacts(), extractClientModule(dependencyMetadata));
    }

    public DefaultComponentOverrideMetadata() {
        this(false, Collections.<IvyArtifactName>emptySet(), null);
    }

    private DefaultComponentOverrideMetadata(boolean changing, Set<IvyArtifactName> artifacts, ClientModule clientModule) {
        this.changing = changing;
        this.artifacts = Sets.newHashSet(artifacts);
        this.clientModule = clientModule;
    }

    private static ClientModule extractClientModule(DependencyMetadata dependencyMetadata) {
        if (dependencyMetadata instanceof DslOriginDependencyMetadata) {
            ModuleDependency source = ((DslOriginDependencyMetadata) dependencyMetadata).getSource();
            if (source instanceof ClientModule) {
                return (ClientModule) source;
            }
        }
        return null;
    }

    @Override
    public ComponentOverrideMetadata withChanging() {
        return new DefaultComponentOverrideMetadata(true, artifacts, clientModule);
    }

    @Override
    public Set<IvyArtifactName> getArtifacts() {
        return artifacts;
    }

    @Override
    public boolean isChanging() {
        return changing;
    }

    @Override
    public ClientModule getClientModule() {
        return clientModule;
    }
}
