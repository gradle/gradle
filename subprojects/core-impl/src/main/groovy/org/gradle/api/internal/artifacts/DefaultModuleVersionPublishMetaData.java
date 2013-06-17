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

package org.gradle.api.internal.artifacts;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.id.ModuleRevisionId;

import java.io.File;
import java.util.Map;

public class DefaultModuleVersionPublishMetaData implements ModuleVersionPublishMetaData {
    private final ModuleRevisionId moduleRevisionId;
    private final Map<Artifact, File> artifacts;

    public DefaultModuleVersionPublishMetaData(ModuleRevisionId moduleRevisionId, Map<Artifact, File> artifacts) {
        this.moduleRevisionId = moduleRevisionId;
        this.artifacts = artifacts;
    }

    public ModuleRevisionId getId() {
        return moduleRevisionId;
    }

    public Map<Artifact, File> getArtifacts() {
        return artifacts;
    }
}
