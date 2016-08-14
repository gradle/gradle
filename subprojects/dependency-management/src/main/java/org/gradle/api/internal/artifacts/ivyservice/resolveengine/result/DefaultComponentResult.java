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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ComponentResult;

public class DefaultComponentResult implements ComponentResult {
    private final Long resultId;
    private final ModuleVersionIdentifier id;
    private final ComponentSelectionReason reason;
    private final ComponentIdentifier componentIdentifier;

    public DefaultComponentResult(Long resultId, ModuleVersionIdentifier id, ComponentSelectionReason reason, ComponentIdentifier componentIdentifier) {
        this.resultId = resultId;
        this.id = id;
        this.reason = reason;
        this.componentIdentifier = componentIdentifier;
    }

    @Override
    public Long getResultId() {
        return resultId;
    }

    public ModuleVersionIdentifier getModuleVersion() {
        return id;
    }

    public ComponentSelectionReason getSelectionReason() {
        return reason;
    }

    public ComponentIdentifier getComponentId() {
        return componentIdentifier;
    }
}
