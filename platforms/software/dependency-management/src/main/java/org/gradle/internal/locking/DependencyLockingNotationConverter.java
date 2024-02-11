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

package org.gradle.internal.locking;

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;

class DependencyLockingNotationConverter {

    ModuleComponentIdentifier convertFromLockNotation(String notation) {
        String[] parts = notation.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("The module notation does not respect the lock file format of 'group:name:version' - received '" + notation + "'");
        }
        return DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId(parts[0], parts[1]), parts[2]);
    }

    String convertToLockNotation(ModuleComponentIdentifier id) {
        return id.getGroup() + ":" + id.getModule() + ":" + id.getVersion();
    }
}
