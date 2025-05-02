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
package org.gradle.api.internal.artifacts.repositories.resolver;

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.external.model.DefaultVirtualModuleComponentIdentifier;
import org.gradle.internal.component.external.model.VirtualComponentIdentifier;

public class VirtualComponentHelper {
    /**
     * Decorates a component identifier, marking it as virtual.
     * @param id the original component identifier
     * @return a virtual component identifier
     */
    public static VirtualComponentIdentifier makeVirtual(final ComponentIdentifier id) {
        ModuleComponentIdentifier mid = assertModuleComponentIdentifier(id);
        return new DefaultVirtualModuleComponentIdentifier(mid.getModuleIdentifier(), mid.getVersion());
    }

    private static ModuleComponentIdentifier assertModuleComponentIdentifier(ComponentIdentifier id) {
        if (id instanceof ModuleComponentIdentifier) {
            return (ModuleComponentIdentifier) id;
        }
        throw new UnsupportedOperationException("Cannot create a virtual component from a " + id.getClass());
    }
}
