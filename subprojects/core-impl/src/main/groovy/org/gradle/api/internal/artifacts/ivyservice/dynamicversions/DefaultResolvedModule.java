/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.dynamicversions;

import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ResolvedModule;
import org.gradle.api.internal.artifacts.DefaultResolvedModuleId;

public class DefaultResolvedModule implements ResolvedModule {
    private final ModuleRevisionId moduleRevisionId;

    public DefaultResolvedModule(ModuleRevisionId moduleRevisionId) {
        this.moduleRevisionId = moduleRevisionId;
    }

    public ModuleIdentifier getId() {
        return new DefaultResolvedModuleId(moduleRevisionId.getOrganisation(), moduleRevisionId.getName(), moduleRevisionId.getRevision());
    }
}
