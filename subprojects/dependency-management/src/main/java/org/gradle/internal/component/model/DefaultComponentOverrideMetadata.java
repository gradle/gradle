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

import org.gradle.api.artifacts.ClientModule;
import org.gradle.api.artifacts.Dependency;
import org.gradle.internal.component.local.model.DslOriginDependencyMetadata;

import javax.annotation.Nullable;

public class DefaultComponentOverrideMetadata implements ComponentOverrideMetadata {
    public static final ComponentOverrideMetadata EMPTY = new DefaultComponentOverrideMetadata(false, null, null);

    private final boolean changing;
    private final IvyArtifactName artifact;
    private final ClientModule clientModule;

    public static ComponentOverrideMetadata forDependency(boolean changing, @Nullable IvyArtifactName mainArtifact, @Nullable ClientModule clientModule) {
        if (!changing && mainArtifact == null && clientModule == null) {
            return EMPTY;
        }
        return new DefaultComponentOverrideMetadata(changing, mainArtifact, clientModule);
    }

    private DefaultComponentOverrideMetadata(boolean changing, @Nullable IvyArtifactName artifact, @Nullable ClientModule clientModule) {
        this.changing = changing;
        this.artifact = artifact;
        this.clientModule = clientModule;
    }

    @Nullable
    public static ClientModule extractClientModule(DependencyMetadata dependencyMetadata) {
        if (dependencyMetadata instanceof DslOriginDependencyMetadata) {
            Dependency source = ((DslOriginDependencyMetadata) dependencyMetadata).getSource();
            if (source instanceof ClientModule) {
                return (ClientModule) source;
            }
        }
        return null;
    }

    @Override
    public ComponentOverrideMetadata withChanging() {
        return new DefaultComponentOverrideMetadata(true, artifact, clientModule);
    }

    @Nullable
    @Override
    public IvyArtifactName getArtifact() {
        return artifact;
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
