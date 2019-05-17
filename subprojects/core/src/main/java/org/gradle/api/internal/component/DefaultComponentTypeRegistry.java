/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.component;

import com.google.common.collect.Maps;
import org.gradle.api.component.Artifact;
import org.gradle.api.component.Component;

import java.util.Map;

public class DefaultComponentTypeRegistry implements ComponentTypeRegistry {
    private final Map<Class<? extends Component>, ComponentTypeRegistration> componentRegistrations = Maps.newHashMap();

    @Override
    public ComponentTypeRegistration maybeRegisterComponentType(Class<? extends Component> componentType) {
        ComponentTypeRegistration registration = componentRegistrations.get(componentType);
        if (registration == null) {
            registration = new DefaultComponentTypeRegistration(componentType);
            componentRegistrations.put(componentType, registration);
        }
        return registration;
    }

    @Override
    public ComponentTypeRegistration getComponentRegistration(Class<? extends Component> componentType) {
        ComponentTypeRegistration registration = componentRegistrations.get(componentType);
        if (registration == null) {
            throw new IllegalArgumentException(String.format("Not a registered component type: %s.", componentType.getName()));
        }
        return registration;
    }

    private static class DefaultComponentTypeRegistration implements ComponentTypeRegistration {
        private final Class<? extends Component> componentType;
        private final Map<Class<? extends Artifact>, ArtifactType> typeRegistrations = Maps.newHashMap();

        private DefaultComponentTypeRegistration(Class<? extends Component> componentType) {
            this.componentType = componentType;
        }

        @Override
        public ArtifactType getArtifactType(Class<? extends Artifact> artifact) {
            ArtifactType type = typeRegistrations.get(artifact);
            if (type == null) {
                throw new IllegalArgumentException(String.format("Artifact type %s is not registered for component type %s.", artifact.getName(), componentType.getName()));
            }
            return type;
        }

        @Override
        public ComponentTypeRegistration registerArtifactType(Class<? extends Artifact> artifact, ArtifactType artifactType) {
            if (typeRegistrations.containsKey(artifact)) {
                throw new IllegalStateException(String.format("Artifact type %s is already registered for component type %s.", artifact.getName(), componentType.getName()));
            }
            typeRegistrations.put(artifact, artifactType);
            return this;
        }
    }
}
