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

package org.gradle.api.internal.resolve;

import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.type.ModelTypes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("deprecation")
public class DefaultLocalLibraryResolver implements LocalLibraryResolver {
    private static final ModelType<org.gradle.model.ModelMap<org.gradle.platform.base.ComponentSpec>> COMPONENT_MAP_TYPE = ModelTypes.modelMap(org.gradle.platform.base.ComponentSpec.class);

    @Override
    @SuppressWarnings("MixedMutabilityReturnType")
    public Collection<org.gradle.platform.base.VariantComponent> resolveCandidates(ModelRegistry projectModel, String libraryName) {
        List<org.gradle.platform.base.VariantComponent> librarySpecs = new ArrayList<>();
        collectLocalComponents(projectModel, "components", librarySpecs);
        collectLocalComponents(projectModel, "testSuites", librarySpecs);
        if (librarySpecs.isEmpty()) {
            return Collections.emptyList();
        }
        return librarySpecs;
    }

    private void collectLocalComponents(ModelRegistry projectModel, String container, List<org.gradle.platform.base.VariantComponent> librarySpecs) {
        org.gradle.model.ModelMap<org.gradle.platform.base.ComponentSpec> components = projectModel.find(container, COMPONENT_MAP_TYPE);
        if (components != null) {
            org.gradle.model.ModelMap<? extends org.gradle.platform.base.VariantComponentSpec> libraries = components.withType(org.gradle.platform.base.VariantComponentSpec.class);
            librarySpecs.addAll(libraries.values());
        }
    }

}
