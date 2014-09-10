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

package org.gradle.internal.component.local.model;

import org.gradle.api.Nullable;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.internal.component.model.ComponentArtifactIdentifier;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.Map;

public class DefaultLocalArtifactIdentifier implements ComponentArtifactIdentifier {
    private final ComponentIdentifier componentIdentifier;
    private final String componentDisplayName;
    private final IvyArtifactName name;

    // The componentDisplayName parameter is temporary
    public DefaultLocalArtifactIdentifier(ComponentIdentifier componentIdentifier, String componentDisplayName, String name, String type, @Nullable String extension, Map<String, String> attributes) {
        this.componentIdentifier = componentIdentifier;
        this.componentDisplayName = componentDisplayName;
        this.name = new DefaultIvyArtifactName(name, type, extension, attributes);
    }

    public String getDisplayName() {
        return String.format("%s (%s)", name, componentDisplayName);
    }

    public IvyArtifactName getName() {
        return name;
    }

    public ComponentIdentifier getComponentIdentifier() {
        return componentIdentifier;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public int hashCode() {
        return componentIdentifier.hashCode() ^ name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        DefaultLocalArtifactIdentifier other = (DefaultLocalArtifactIdentifier) obj;
        return other.componentIdentifier.equals(componentIdentifier) && other.name.equals(name);
    }
}
