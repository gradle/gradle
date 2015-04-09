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

import java.util.Set;

public class DefaultComponentRequestMetaData implements ComponentRequestMetaData {
    private final boolean changing;
    private final Set<IvyArtifactName> artifacts;

    public DefaultComponentRequestMetaData(DependencyMetaData dependencyMetaData) {
        this.changing = dependencyMetaData.isChanging();
        this.artifacts = Sets.newHashSet(dependencyMetaData.getArtifacts());
    }

    public DefaultComponentRequestMetaData(boolean changing, Set<IvyArtifactName> artifacts) {
        this.changing = changing;
        this.artifacts = Sets.newHashSet(artifacts);
    }

    @Override
    public ComponentRequestMetaData withChanging() {
        return new DefaultComponentRequestMetaData(true, artifacts);
    }

    @Override
    public Set<IvyArtifactName> getArtifacts() {
        return artifacts;
    }

    @Override
    public boolean isChanging() {
        return changing;
    }
}
