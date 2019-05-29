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

package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.specs.Spec;

public class ModuleVersionSelectorStrictSpec implements Spec<ModuleVersionIdentifier> {

    private final ModuleVersionSelector selector;

    public ModuleVersionSelectorStrictSpec(ModuleVersionSelector selector) {
        assert selector != null;
        this.selector = selector;
    }

    @Override
    public boolean isSatisfiedBy(ModuleVersionIdentifier candidate) {
        return candidate.getName().equals(selector.getName())
                && candidate.getGroup().equals(selector.getGroup())
                && candidate.getVersion().equals(selector.getVersion());
    }
}
