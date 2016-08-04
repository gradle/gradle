/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.projectmodule;

import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.internal.component.model.ComponentArtifactMetadata;

public class CacheLockReleasingProjectArtifactBuilder implements ProjectArtifactBuilder {
    private final ProjectArtifactBuilder delegate;
    private final CacheLockingManager cacheLockingManager;

    public CacheLockReleasingProjectArtifactBuilder(ProjectArtifactBuilder delegate, CacheLockingManager cacheLockingManager) {
        this.delegate = delegate;
        this.cacheLockingManager = cacheLockingManager;
    }

    @Override
    public void build(final ComponentArtifactMetadata artifact) {
        cacheLockingManager.longRunningOperation("Build " + artifact.getId(), new Runnable() {
            @Override
            public void run() {
                delegate.build(artifact);
            }
        });
    }

    @Override
    public void willBuild(ComponentArtifactMetadata artifact) {
        delegate.willBuild(artifact);
    }
}
