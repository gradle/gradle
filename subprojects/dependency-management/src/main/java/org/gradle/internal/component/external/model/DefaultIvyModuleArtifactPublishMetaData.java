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

package org.gradle.internal.component.external.model;

import com.google.common.collect.Lists;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.model.IvyArtifactName;

import java.io.File;
import java.util.List;

public class DefaultIvyModuleArtifactPublishMetaData implements IvyModuleArtifactPublishMetaData {
    private final DefaultModuleComponentArtifactIdentifier id;
    private final List<String> configurations = Lists.newArrayList();
    private final File file;

    public DefaultIvyModuleArtifactPublishMetaData(ModuleComponentIdentifier componentId, IvyArtifactName artifact, List<String> configurations) {
        this.id = new DefaultModuleComponentArtifactIdentifier(componentId, artifact);
        this.file = null;
        this.configurations.addAll(configurations);
    }

    public DefaultIvyModuleArtifactPublishMetaData(ModuleVersionIdentifier moduleVersionIdentifier, IvyArtifactName artifact, File file) {
        this.id = new DefaultModuleComponentArtifactIdentifier(DefaultModuleComponentIdentifier.newId(moduleVersionIdentifier), artifact);
        this.file = file;
    }

    public void addConfiguration(String configuration) {
        configurations.add(configuration);
    }

    public IvyArtifactName getArtifactName() {
        return id.getName();
    }

    @Override
    public List<String> getConfigurations() {
        return configurations;
    }

    public ModuleComponentArtifactIdentifier getId() {
        return id;
    }

    public File getFile() {
        return file;
    }
}
